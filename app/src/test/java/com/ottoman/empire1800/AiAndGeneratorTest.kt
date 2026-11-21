package com.ottoman.empire1800

import com.ottoman.empire1800.engine.RoundParser
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.services.AiClient
import com.ottoman.empire1800.services.AiException
import com.ottoman.empire1800.services.AiProvider
import com.ottoman.empire1800.services.AiSettings
import com.ottoman.empire1800.services.RoundGenerator
import com.ottoman.empire1800.services.aiProviders
import com.ottoman.empire1800.services.buildRoundPrompt
import com.ottoman.empire1800.services.isCompleteJsonObjectText
import com.ottoman.empire1800.services.parseChatCompletionText
import com.ottoman.empire1800.services.parseGeminiText
import com.ottoman.empire1800.services.rankDiscoveredModels
import com.ottoman.empire1800.support.FakeAi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private class StubProvider(
    override val id: String = "stub",
    override val name: String = "Stub",
    override val defaultModel: String = "m1",
    override val fallbackModels: List<String> = listOf("m2"),
    override val supportsSearch: Boolean = true,
    private val handler: (model: String, search: Boolean) -> String,
) : AiProvider {
    val calls = mutableListOf<Pair<String, Boolean>>()

    override suspend fun generate(
        prompt: String,
        apiKey: String,
        model: String,
        json: Boolean,
        maxTokens: Int,
        search: Boolean,
        thinkingLevel: String,
    ): String {
        calls.add(model to search)
        return handler(model, search)
    }
}

class AiAndGeneratorTest {

    @Test
    fun parsesGeminiAndChatCompletionResponses() {
        val geminiJson = """{"candidates":[{"content":{"parts":[{"text":"{\"ok\":true}"}]}}]}"""
        assertEquals("""{"ok":true}""", parseGeminiText(geminiJson))

        val groqJson = """{"choices":[{"message":{"content":" {\"ok\":1} "}}]}"""
        assertEquals("""{"ok":1}""", parseChatCompletionText(groqJson))

        assertTrue(aiProviders.map { it.id }.containsAll(listOf("gemini", "groq")))
    }

    @Test
    fun ranksDiscoveredModelsAndValidatesCompleteJson() {
        val gemRanked = rankDiscoveredModels(
            "gemini",
            listOf(
                "models/gemini-2.5-flash-tts",
                "models/gemini-nano-banana-2.1",
                "models/gemini-banana-exp",
                "models/gemini-gemma-test",
                "models/gemini-learnlm-2.0",
                "models/gemini-2.5-flash",
                "models/gemini-3.5-flash-lite",
                "models/gemini-3.6-flash",
                "models/gemini-4-hydrogen",
                "models/gemini-1.5-pro",
            ),
        )
        assertEquals(
            listOf("gemini-4-hydrogen", "gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-2.5-flash"),
            gemRanked,
        )

        val groqRanked = rankDiscoveredModels(
            "groq",
            listOf("whisper-large-v3", "llama-3.1-8b-instant", "openai/gpt-oss-120b"),
        )
        assertEquals(listOf("openai/gpt-oss-120b", "llama-3.1-8b-instant"), groqRanked)

        assertTrue(isCompleteJsonObjectText("""{"ok":true}"""))
        assertTrue(isCompleteJsonObjectText("```json\n{\"ok\":true}\n```\n{extra}"))
        assertFalse(isCompleteJsonObjectText("""{"ok":true"""))
    }

    @Test
    fun aiClientFallsBackToNextModelOnErrorsTruncatedJsonAndRetriesWithoutSearchOn400() = runTest {
        AiClient.clearDiscoveryCache()
        val p1 = StubProvider { _, search ->
            if (search) throw AiException("bad", status = 400)
            """{"ok":true}"""
        }
        val c1 = AiClient(p1, "k", listOf("m1", "m2"), search = true)
        val (txt1, model1) = c1.generateJsonWithModel("prompt")
        assertEquals("""{"ok":true}""", txt1)
        assertEquals("m1", model1)
        assertEquals(listOf("m1" to true, "m1" to false), p1.calls)

        val pSearch429 = StubProvider { _, search ->
            if (search) throw AiException("429", retryable = true, status = 429)
            """{"ok":"no-search"}"""
        }
        val cSearch429 = AiClient(pSearch429, "k", listOf("m1", "m2"), search = true)
        val (txtSearch429, modelSearch429) = cSearch429.generateJsonWithModel("prompt")
        assertEquals("""{"ok":"no-search"}""", txtSearch429)
        assertEquals("m1", modelSearch429)
        assertEquals(listOf("m1" to true, "m1" to false), pSearch429.calls)

        val p2 = StubProvider { model, _ ->
            if (model == "m1") throw AiException("429", retryable = true, status = 429)
            """{"ok":2}"""
        }
        val c2 = AiClient(p2, "k", listOf("m1", "m2"), search = false)
        assertEquals("""{"ok":2}""", c2.generateJson("prompt"))
        assertEquals(listOf("m1" to false, "m2" to false), p2.calls)

        // Falls back when m1 returns truncated JSON
        val pTrunc = StubProvider { model, _ ->
            if (model == "m1") """{"headline":"truncated""" else """{"ok":"recovered"}"""
        }
        val cTrunc = AiClient(
            provider = pTrunc,
            apiKey = "k-disc",
            models = listOf("m1", "m2"),
            autoDiscover = true,
            discoverFn = { listOf("m1", "m2") },
        )
        val (recText, recModel) = cTrunc.generateJsonWithModel("prompt")
        assertEquals("""{"ok":"recovered"}""", recText)
        assertEquals("m2", recModel)

        val p3 = StubProvider { _, _ ->
            throw AiException("401", retryable = false, modelProblem = false, status = 401)
        }
        val c3 = AiClient(p3, "k", listOf("m1", "m2"), search = false)
        var authFailed = false
        try {
            c3.generateJson("prompt")
        } catch (e: AiException) {
            authFailed = e.status == 401 && e.diagnostic?.contains("m1") == true
        }
        assertTrue(authFailed)
        assertEquals(listOf("m1" to false), p3.calls)
    }

    @Test
    fun aiClientFromSettingsBuildsOrderedUniqueModels() {
        val gCustom = AiClient.fromSettings(AiSettings(provider = "gemini", model = "custom-model", apiKey = "abc"))
        assertEquals(
            listOf("custom-model", "gemini-4-hydrogen", "gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-2.5-flash"),
            gCustom.models,
        )

        val gDefault = AiClient.fromSettings(AiSettings(provider = "gemini", model = "", apiKey = "abc"))
        assertEquals(
            listOf("gemini-4-hydrogen", "gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-2.5-flash"),
            gDefault.models,
        )

        val qDefault = AiClient.fromSettings(AiSettings(provider = "groq", model = "", apiKey = "gsk_123"))
        assertEquals(
            listOf("openai/gpt-oss-120b", "llama-3.3-70b-versatile", "openai/gpt-oss-20b", "llama-3.1-8b-instant"),
            qDefault.models,
        )
    }

    @Test
    fun roundPromptAndParserHandleTrailingBraces() {
        val s1 = GameState(round = 0)
        val s2 = GameState(round = 40, debt = 25_000_000.0, foreignDebt = 10_000_000.0)
        val p1 = buildRoundPrompt(s1)
        val p2 = buildRoundPrompt(s2)
        assertTrue(p1.length < 9000)
        assertTrue(p2.length < 11000)
        assertEquals(p1.substring(0, 200), p2.substring(0, 200))
        assertTrue(p1.contains("CRITICAL: decisions MUST contain all 3 categories (internal, economy, foreign)"))
        assertTrue(p1.contains("\"category\":\"internal\"") && p1.contains("\"category\":\"economy\"") && p1.contains("\"category\":\"foreign\""))
        assertTrue(p1.contains("\"text\":\"خيار 3\""))
        assertTrue(p2.contains("الدين الخارجي"))
        assertTrue(buildRoundPrompt(s1, problem = "نقص قرار").contains("نقص قرار"))

        val validJson = FakeAi(seed = 7).call("")
        val parsedWithExtra = RoundParser.parse("```json\n$validJson\n```\n{trailing}")
        assertEquals(3, parsedWithExtra.decisions.size)
    }

    @Test
    fun roundGeneratorRetriesFormatAndTransientAiErrors() = runTest {
        var n = 0
        val valid = FakeAi(seed = 7)
        val gen = RoundGenerator(
            call = {
                n++
                when (n) {
                    1 -> "not-json"
                    2 -> throw AiException("rate", retryable = true)
                    else -> valid.call("")
                }
            },
            wait = { _ -> },
        )
        val plan = gen.generate(GameState())
        assertEquals(3, n)
        assertEquals(3, plan.decisions.size)
        assertFalse(plan.headline.isEmpty())
    }

    @Test
    fun roundGeneratorKeepsLowThinkingAndFallsBackImmediatelyOnInvalidRoundJson() = runTest {
        val validJson = FakeAi(seed = 7).call("")
        val parsed = RoundParser.parse(validJson)
        // Produce JSON with only 1 option on decision 1
        val badObj = org.json.JSONObject(validJson)
        val decs = badObj.getJSONArray("decisions")
        val firstOpts = decs.getJSONObject(0).getJSONArray("options")
        decs.getJSONObject(0).put("options", org.json.JSONArray().put(firstOpts.getJSONObject(0)))
        val badOneOptionJson = badObj.toString()

        val detailedCalls = mutableListOf<Triple<String, Set<String>, Boolean>>()
        val gen = RoundGenerator(
            callDetailed = { prompt, thinkingLevel, exclude ->
                val model = if ("gemini-4-hydrogen" in exclude) "gemini-3.6-flash" else "gemini-4-hydrogen"
                detailedCalls.add(Triple(thinkingLevel, exclude.toSet(), prompt.contains("يحتاج ثلاثة خيارات")))
                if (model == "gemini-4-hydrogen") badOneOptionJson to model else validJson to model
            },
            wait = { _ -> },
        )
        val plan = gen.generate(GameState())
        assertEquals(parsed.decisions.size, plan.decisions.size)
        assertEquals(2, detailedCalls.size)
        assertEquals(Triple("low", emptySet<String>(), false), detailedCalls[0])
        assertEquals(Triple("low", setOf("gemini-4-hydrogen"), true), detailedCalls[1])
    }
}
