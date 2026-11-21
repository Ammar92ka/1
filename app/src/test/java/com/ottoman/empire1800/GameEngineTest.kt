package com.ottoman.empire1800

import com.ottoman.empire1800.engine.Calibrator
import com.ottoman.empire1800.engine.EndingType
import com.ottoman.empire1800.engine.GameEngine
import com.ottoman.empire1800.engine.RoundParser
import com.ottoman.empire1800.models.Decision
import com.ottoman.empire1800.models.DecisionCategory
import com.ottoman.empire1800.models.DecisionOption
import com.ottoman.empire1800.models.Effects
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.LineChange
import com.ottoman.empire1800.models.PendingEffect
import com.ottoman.empire1800.models.RoundPlan
import com.ottoman.empire1800.models.SultanState
import com.ottoman.empire1800.models.revenueLines
import com.ottoman.empire1800.models.spendingLines
import com.ottoman.empire1800.services.DeepEconomyRules
import com.ottoman.empire1800.services.EconomyRules
import com.ottoman.empire1800.services.ForeignRules
import com.ottoman.empire1800.services.GazetteRules
import com.ottoman.empire1800.services.GrandVizierAdvisor
import com.ottoman.empire1800.services.StabilityRules
import com.ottoman.empire1800.services.SultanRules
import com.ottoman.empire1800.services.WarRules
import com.ottoman.empire1800.services.buildRoundPrompt
import com.ottoman.empire1800.support.FakeAi
import org.json.JSONObject
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameEngineTest {
    private val engine = GameEngine()

    private fun plan(
        first: DecisionOption = DecisionOption(text = "أ", result = "ن"),
        second: DecisionOption = DecisionOption(text = "أ", result = "ن"),
        third: DecisionOption = DecisionOption(text = "أ", result = "ن"),
    ): RoundPlan {
        val neutral = DecisionOption(text = "محايد", result = "ن")
        fun d(c: DecisionCategory, o: DecisionOption) = Decision(
            category = c,
            title = "قرار ${c.id}",
            scene = "مشهد",
            dilemma = "معضلة",
            options = listOf(o, neutral, neutral),
        )
        return RoundPlan(
            headline = "ع",
            briefing = "ملخص",
            decisions = listOf(
                d(DecisionCategory.INTERNAL, first),
                d(DecisionCategory.ECONOMY, second),
                d(DecisionCategory.FOREIGN, third),
            ),
        )
    }

    private fun ready(plan: RoundPlan, base: GameState? = null): GameState {
        val s = base ?: GameState()
        s.plan = plan
        s.picks = mutableListOf(0, 0, 0)
        return s
    }

    @Test
    fun calendarLabelsAndTotalRounds() {
        assertEquals(684, GameEngine.totalRounds)
        assertEquals(1800, GameEngine.yearOf(0))
        assertEquals(1800, GameEngine.yearOf(3))
        assertEquals(1801, GameEngine.yearOf(4))
        assertEquals("كانون الثاني - آذار 1800", GameEngine.periodLabel(0))
        assertEquals("نيسان - حزيران 1800", GameEngine.periodLabel(1))
        assertEquals("تشرين الأول - كانون الأول 1970", GameEngine.periodLabel(683))
    }

    @Test(expected = IllegalStateException::class)
    fun throwsWhenNoPlan() {
        engine.resolveRound(GameState())
    }

    @Test(expected = IllegalStateException::class)
    fun throwsWhenMissingPicks() {
        val s = GameState().apply { this.plan = plan() }
        engine.resolveRound(s)
    }

    @Test
    fun budgetLoansCostsAndTreasuryMath() {
        val s = ready(
            plan(
                first = DecisionOption(text = "أ", result = "ن", cost = 1_000_000.0),
                second = DecisionOption(text = "أ", result = "ن", loan = 2_000_000.0),
            ),
        )
        val report = engine.resolveRound(s)
        assertEquals(4_196_250.0, report.revenue, 0.01)
        assertEquals(4_250_000.0, report.spending, 0.01)
        assertEquals(240_000.0, report.interest, 0.01)
        assertEquals(1_000_000.0, report.oneTimeCosts, 0.01)
        assertEquals(2_000_000.0, report.loans, 0.01)
        assertEquals(3_706_250.0, s.treasury, 0.01)
        assertEquals(8_000_000.0, s.debt, 0.01)
        assertEquals(1, s.round)
        assertNull(s.plan)
        assertFalse(s.hasAllPicks)
        assertEquals(3, s.history.size)
        assertEquals("كانون الثاني - آذار 1800", report.period)
        assertTrue(s.lastReport.isNotEmpty())
    }

    @Test
    fun immediateEffectsClampedToZeroToHundred() {
        val s = ready(
            plan(
                first = DecisionOption(text = "أ", result = "ن", now = Effects(legitimacy = 8, army = -8)),
            ),
        )
        engine.resolveRound(s)
        assertEquals(73, s.legitimacy)
        assertEquals(37, s.army)

        val low = GameState(army = 3).apply {
            this.plan = plan(first = DecisionOption(text = "أ", result = "ن", now = Effects(army = -8)))
            this.picks = mutableListOf(0, 0, 0)
        }
        engine.resolveRound(low)
        assertEquals(0, low.army)
    }

    @Test
    fun delayedEffectsAppearOnDueRound() {
        val s = ready(
            plan(
                first = DecisionOption(
                    text = "مدارس",
                    result = "ن",
                    later = Effects(education = 5),
                    delay = 2,
                    lineChanges = listOf(LineChange("civil", 600_000.0)),
                ),
            ),
        )
        val r1 = engine.resolveRound(s)
        assertTrue(r1.dueNotes.isEmpty())
        assertEquals(1, s.pending.size)
        assertEquals(2, s.pending.first().dueRound)
        assertEquals(15, s.education)
        val civilBefore = s.budget.lineValue("civil")

        s.plan = plan()
        s.picks = mutableListOf(0, 0, 0)
        val r2 = engine.resolveRound(s)
        assertEquals(1, r2.dueNotes.size)
        assertTrue(s.pending.isEmpty())
        assertEquals(20, s.education)
        assertTrue(s.budget.lineValue("civil") > civilBefore + 590_000.0)
    }

    @Test
    fun delayZeroAppliesImmediately() {
        val s = ready(
            plan(first = DecisionOption(text = "أ", result = "ن", later = Effects(foreign = 4), delay = 0)),
        )
        engine.resolveRound(s)
        assertTrue(s.pending.isEmpty())
        assertEquals(49, s.foreign)
    }

    @Test
    fun treasuryDepletionTriggersEmergencyLoan() {
        val s = GameState(treasury = 100_000.0).apply {
            this.plan = plan(first = DecisionOption(text = "كلفة", result = "ن", cost = 2_000_000.0))
            this.picks = mutableListOf(0, 0, 0)
        }
        val debtBefore = s.debt
        val report1 = engine.resolveRound(s)
        assertEquals(0.0, s.treasury, 0.001)
        assertTrue(report1.emergencyBorrowed > 0.0)
        assertTrue(s.debt > debtBefore)

        // With k = 2 in quarterly mode, integer army & legitimacy penalties apply on the 2nd consecutive emergency round
        s.plan = plan(first = DecisionOption(text = "كلفة", result = "ن", cost = 2_000_000.0))
        s.picks = mutableListOf(0, 0, 0)
        val report2 = engine.resolveRound(s)
        assertTrue(report2.emergencyBorrowed > 0.0)
        assertTrue(s.army < 45)
        assertTrue(s.legitimacy < 65)
    }

    @Test
    fun endingsEvaluateProperly() {
        assertNull(engine.evaluate(GameState()))
        assertEquals(EndingType.DEPOSED, engine.evaluate(GameState(legitimacy = 15))!!.type)
        assertEquals(EndingType.COLLAPSE, engine.evaluate(GameState(stability = 12))!!.type)
        assertEquals(EndingType.BANKRUPTCY, engine.evaluate(GameState(debt = 108_000_000.0))!!.type)
        assertEquals(EndingType.CONQUEST, engine.evaluate(GameState(army = 8, foreign = 12))!!.type)
        assertNull(engine.evaluate(GameState(army = 8, foreign = 30)))

        val n = GameEngine.totalRounds
        val gold = engine.evaluate(
            GameState(round = n, legitimacy = 80, stability = 75, army = 70, foreign = 70, education = 60, debt = 1_000_000.0),
        )
        assertNotNull(gold)
        assertTrue(gold!!.isVictory)
        assertEquals("إمبراطورية مزدهرة", gold.title)

        val solid = engine.evaluate(
            GameState(round = n, legitimacy = 55, stability = 55, army = 50, foreign = 50, education = 45, debt = 20_000_000.0),
        )
        assertEquals("دولة متماسكة", solid!!.title)

        val barely = engine.evaluate(
            GameState(round = n, legitimacy = 30, stability = 30, army = 30, foreign = 30, education = 30, debt = 60_000_000.0),
        )
        assertEquals("نجوت بالكاد", barely!!.title)

        assertEquals(
            EndingType.DEPOSED,
            engine.evaluate(GameState(round = GameEngine.totalRounds, legitimacy = 10))!!.type,
        )
    }

    @Test
    fun fullCampaignWithFakeAiStaysBounded() = runTest {
        suspend fun play(ai: FakeAi, seed: Int, maxRounds: Int = 300): GameState {
            val rnd = Random(seed)
            val s = GameState()
            for (i in 0 until maxRounds) {
                val raw = RoundParser.parse(ai.call(""))
                s.plan = Calibrator.calibrate(raw, s)
                s.picks = mutableListOf(rnd.nextInt(3), rnd.nextInt(3), rnd.nextInt(3))
                val report = engine.resolveRound(s)
                assertTrue(s.treasury >= 0.0)
                assertTrue(s.debt >= 0.0)
                for (v in listOf(s.legitimacy, s.stability, s.army, s.foreign, s.education)) {
                    assertTrue(v in 0..100)
                }
                for (l in revenueLines + spendingLines) {
                    assertTrue(s.budget.lineValue(l.key) >= 0.0)
                }
                if (report.ending != null) break
            }
            return s
        }
        for (seed in 1..25) {
            play(FakeAi(seed = seed), seed)
            play(FakeAi(seed = seed, wild = true), seed)
        }
        val finished = play(FakeAi(seed = 5), 5, maxRounds = 720)
        assertNotNull(engine.evaluate(finished))
    }

    @Test
    fun sultanSuccessionPassiveTraitsAndPrevStatsWorkInQuarterlyMode() {
        val s = GameState(seed = 42)
        assertEquals(4, s.ppy)
        assertEquals("السلطان سليم الثالث", s.sultan.name)
        assertEquals("reformer", s.sultan.trait)
        assertEquals(1800, s.sultan.enthronedYear)
        assertEquals(1, s.sultan.reignOrder)
        assertFalse(s.pendingSuccession)
        assertNull(s.prevStats)

        val prompt = buildRoundPrompt(s)
        assertTrue(prompt.contains("السلطان الحالي: السلطان سليم الثالث"))

        val reignYears = SultanRules.reignDurationYears(s.sultan, s.seed)
        assertTrue(reignYears in 25..30)

        val sq = GameState(seed = 42, round = (reignYears - 1) * 4)
        assertFalse(SultanRules.shouldTriggerSuccession(sq))
        sq.round = reignYears * 4
        assertTrue(SultanRules.shouldTriggerSuccession(sq))

        s.round = reignYears * 4 - 1
        s.treasury = 50_000_000.0
        s.plan = plan()
        s.picks = mutableListOf(0, 0, 0)
        val legitBefore = s.legitimacy
        val treasuryBefore = s.treasury
        val rep = engine.resolveRound(s)
        assertNull(rep.ending)
        assertTrue(s.pendingSuccession)
        assertNotNull(s.prevStats)
        assertEquals(treasuryBefore, s.prevStats!!.treasury, 0.01)
        assertEquals(legitBefore, s.prevStats!!.legitimacy)

        val legitBeforeHeir = s.legitimacy
        val polBeforeHeir = s.stab["political"]
        val chosen = SultanRules.chooseHeir(s, 0)
        assertFalse(s.pendingSuccession)
        assertEquals(chosen.name, s.sultan.name)
        assertEquals(2, s.sultan.reignOrder)
        assertEquals(s.year, s.sultan.enthronedYear)
        assertEquals((legitBeforeHeir + 4).coerceIn(0, 100), s.legitimacy)
        assertTrue(s.stab["political"] >= polBeforeHeir + 2.0 - 1e-6)
        assertTrue(s.decrees.any { it.contains(chosen.name) })

        // Passive traits: economist revenue/spending & military war balance
        val sEco = GameState(sultan = SultanState("السلطان مراد الخامس", "economist", 1850, 4))
        val sRef = GameState(sultan = SultanState("السلطان سليم الثالث", "reformer", 1800, 1))
        assertTrue(
            EconomyRules.halfYearRevenue(sEco.budget, sEco.stability, econ = sEco.econ, sultanTrait = sEco.sultan.trait) >
                EconomyRules.halfYearRevenue(sRef.budget, sRef.stability, econ = sRef.econ, sultanTrait = sRef.sultan.trait),
        )
        assertTrue(
            EconomyRules.halfYearSpending(sEco.budget, econ = sEco.econ, sultanTrait = sEco.sultan.trait) <
                EconomyRules.halfYearSpending(sRef.budget, econ = sRef.econ, sultanTrait = sRef.sultan.trait),
        )

        val sMil = GameState(sultan = SultanState("السلطان عبد العزيز", "military", 1850, 3))
        assertEquals(1.05, WarRules.ourPower(sMil) / WarRules.ourPower(sRef), 1e-6)

        // Ending blocks succession trigger
        val sEnd = GameState(seed = 42, round = reignYears * 4 - 1, legitimacy = 5).apply {
            this.plan = plan()
            this.picks = mutableListOf(0, 0, 0)
        }
        val repEnd = engine.resolveRound(sEnd)
        assertNotNull(repEnd.ending)
        assertFalse(sEnd.pendingSuccession)
    }

    @Test
    fun localGrandVizierAdvisorAndGreatPowersRatiosWork() {
        val s = GameState(seed = 11)
        val dec = Decision(
            category = DecisionCategory.INTERNAL,
            title = "إصلاح الدولة",
            scene = "مشهد",
            dilemma = "معضلة",
            options = listOf(
                DecisionOption(
                    text = "إصلاح المدارس والديوان",
                    result = "تحديث",
                    cost = 500_000.0,
                    now = Effects(education = 5, foreign = 2),
                    later = Effects(education = 4),
                    delay = 2,
                    powerMood = mapOf("ulema" to -4),
                ),
                DecisionOption(
                    text = "تعبئة عسكرية شاملة",
                    result = "جيش",
                    cost = 800_000.0,
                    now = Effects(army = 6, stability = 1),
                    later = Effects(army = 3),
                    delay = 2,
                ),
                DecisionOption(
                    text = "تقشف وحفظ العوائد والتقليد",
                    result = "توازن",
                    now = Effects(legitimacy = 4, stability = 2),
                    lineChanges = listOf(LineChange("customs", 1_200_000.0)),
                    powerMood = mapOf("ulema" to 5),
                ),
            ),
        )

        s.grandVizier = "reformer"
        assertEquals(0, GrandVizierAdvisor.advise(s, dec).recommendedIndex)

        s.grandVizier = "military"
        assertEquals(1, GrandVizierAdvisor.advise(s, dec).recommendedIndex)

        s.grandVizier = "economist"
        assertEquals(2, GrandVizierAdvisor.advise(s, dec).recommendedIndex)

        s.grandVizier = "traditional"
        val tradHint = GrandVizierAdvisor.advise(s, dec)
        assertEquals(2, tradHint.recommendedIndex)
        assertTrue(tradHint.recommendationLabel.contains("(ج)"))

        for (key in listOf("britain", "france", "russia")) {
            val c = s.countries[key]!!
            assertTrue(ForeignRules.econRatio(s, c) > 0.0)
            assertTrue(ForeignRules.milRatio(s, c) > 0.0)
            assertTrue(ForeignRules.strengthLabel(ForeignRules.econRatio(s, c)).isNotEmpty())
        }
    }

    @Test
    fun imperialGazetteAndPressPolicyWorkWithLegacyCompat() {
        val s = GameState(seed = 88)
        assertFalse(GazetteRules.isUnlocked(s))
        assertEquals("censored", s.pressPolicy)
        assertTrue(s.gazette.isEmpty())

        val sEdu = GameState(education = 25)
        assertTrue(GazetteRules.isUnlocked(sEdu))

        s.completed.add("printing")
        assertTrue(GazetteRules.isUnlocked(s))
        s.courtActions = 2
        s.powers["intelligentsia"]?.unlocked = true
        val intelBefore = s.powers["intelligentsia"]!!.satisfaction
        val ulemaBefore = s.powers["ulema"]!!.satisfaction

        assertFalse(GazetteRules.setPressPolicy(s, "censored").ok)
        assertTrue(GazetteRules.setPressPolicy(s, "free").ok)
        assertEquals("free", s.pressPolicy)
        assertEquals(1, s.courtActions)
        assertEquals(intelBefore + 4.0, s.powers["intelligentsia"]!!.satisfaction, 1e-6)
        assertEquals(ulemaBefore - 2.0, s.powers["ulema"]!!.satisfaction, 1e-6)
        assertTrue(s.decrees.any { it.contains("فرمان المطبوعات: حرية الصحافة") })

        // Free press literacy boost vs political sensitivity under quarterly (ppy=4) scaling
        val sFree = GameState(seed = 12).apply {
            completed.add("printing")
            pressPolicy = "free"
            econ.corruption = 65.0
        }
        val sCens = GameState(seed = 12).apply {
            completed.add("printing")
            pressPolicy = "censored"
            econ.corruption = 65.0
        }
        DeepEconomyRules.step(sFree, 0.0)
        DeepEconomyRules.step(sCens, 0.0)
        assertTrue(sFree.demog.literacy > sCens.demog.literacy)

        val freeBefore = sFree.stab["political"]
        val notes = StabilityRules.step(sFree, debtRatio = 0.5, emergency = true)
        assertTrue(notes.any { it.contains("الصحافة الحرة") })
        val freeDelta = sFree.stab["political"] - freeBefore

        val censBefore = sCens.stab["political"]
        StabilityRules.step(sCens, debtRatio = 0.5, emergency = true)
        val censDelta = sCens.stab["political"] - censBefore
        assertTrue(freeDelta < censDelta)

        // Recording Gazette issues caps at MAX_ISSUES = 8
        fun demoOpt(text: String) = DecisionOption(text = text, result = "تم", now = Effects())
        fun demoPlan() = RoundPlan(
            chronicle = "حكم",
            headline = "أمر",
            briefing = "ملخص",
            decisions = listOf(
                Decision(DecisionCategory.INTERNAL, "داخلي", "", "ساحة", "سؤال", listOf(demoOpt("أ"), demoOpt("ب"), demoOpt("ج"))),
                Decision(DecisionCategory.ECONOMY, "اقتصاد", "", "سوق", "سؤال", listOf(demoOpt("أ"), demoOpt("ب"), demoOpt("ج"))),
                Decision(DecisionCategory.FOREIGN, "خارجي", "", "ثغر", "سؤال", listOf(demoOpt("أ"), demoOpt("ب"), demoOpt("ج"))),
            ),
        )
        repeat(GazetteRules.MAX_ISSUES + 2) {
            s.treasury = 50_000_000.0
            s.plan = demoPlan()
            s.picks = mutableListOf(0, 0, 0)
            engine.resolveRound(s)
        }
        assertEquals(GazetteRules.MAX_ISSUES, s.gazette.size)
        assertTrue(s.gazette.first().headlines.size in 2..3)

        // JSON roundtrip & legacy fallback
        val json = s.toJson()
        val restored = GameState.fromJson(json)
        assertEquals("free", restored.pressPolicy)
        assertEquals(GazetteRules.MAX_ISSUES, restored.gazette.size)
        assertEquals(s.gazette.first().headlines, restored.gazette.first().headlines)

        val legacy = JSONObject(json.toString())
        legacy.remove("pressPolicy")
        legacy.remove("gazette")
        val restoredLegacy = GameState.fromJson(legacy)
        assertEquals("censored", restoredLegacy.pressPolicy)
        assertTrue(restoredLegacy.gazette.isEmpty())
    }

    @Test
    fun antiSnowballingDiminishingReturnsAtHighStatsAndFullNegativeLosses() {
        val s = GameState(treasury = 50_000_000.0, round = 1, legitimacy = 76, army = 90, education = 80, grandVizier = "economist")
        s.budget.spending["army"] = 6_000_000.0
        s.plan = plan(
            first = DecisionOption(text = "أ", result = "ن", now = Effects(legitimacy = 2, army = 2, education = 1)),
        )
        s.picks = mutableListOf(0, 0, 0)
        engine.resolveRound(s)
        assertEquals(77, s.legitimacy)
        assertEquals(89, s.army)
        assertEquals(80, s.education)

        val sNeg = GameState(treasury = 50_000_000.0, legitimacy = 92)
        sNeg.plan = plan(first = DecisionOption(text = "أ", result = "ن", now = Effects(legitimacy = -2)))
        sNeg.picks = mutableListOf(0, 0, 0)
        engine.resolveRound(sNeg)
        assertEquals(90, sNeg.legitimacy)
    }
}

