package com.ottoman.empire1800

import com.ottoman.empire1800.data.projectCatalog
import com.ottoman.empire1800.data.projectDef
import com.ottoman.empire1800.engine.Calibrator
import com.ottoman.empire1800.engine.GameEngine
import com.ottoman.empire1800.models.DecisionOption
import com.ottoman.empire1800.models.Difficulty
import com.ottoman.empire1800.models.Effects
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.LineChange
import com.ottoman.empire1800.models.StatPoint
import com.ottoman.empire1800.models.SultanState
import com.ottoman.empire1800.services.ClimateRules
import com.ottoman.empire1800.services.EconomyRules
import com.ottoman.empire1800.services.ForeignRules
import com.ottoman.empire1800.services.PowerRules
import com.ottoman.empire1800.services.ProjectRules
import com.ottoman.empire1800.support.neutralPlan
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CampaignAndPaceTest {
    private val engine = GameEngine()

    @Test
    fun difficultyMultipliersMatchSpec() {
        val easy = Difficulty(0)
        val normal = Difficulty(1)
        val hard = Difficulty(2)

        assertEquals(1.06, easy.revenue, 1e-6)
        assertEquals(0.7, easy.events, 1e-6)
        assertEquals(0.8, easy.losses, 1e-6)

        assertEquals(1.0, normal.revenue, 1e-6)
        assertEquals(1.0, normal.events, 1e-6)
        assertEquals(1.0, normal.losses, 1e-6)

        assertEquals(0.94, hard.revenue, 1e-6)
        assertEquals(1.3, hard.events, 1e-6)
        assertEquals(1.25, hard.losses, 1e-6)

        // Harder losses amplify negative effects
        val sEasy = GameState(difficulty = 0).apply {
            plan = neutralPlan()
            plan!!.decisions[0].options[0] = DecisionOption(text = "ت", result = "ن", now = Effects(legitimacy = -8))
            picks = mutableListOf(0, 0, 0)
        }
        val sHard = GameState(difficulty = 2).apply {
            plan = neutralPlan()
            plan!!.decisions[0].options[0] = DecisionOption(text = "ت", result = "ن", now = Effects(legitimacy = -8))
            picks = mutableListOf(0, 0, 0)
        }
        engine.resolveRound(sEasy)
        engine.resolveRound(sHard)
        assertTrue(sEasy.legitimacy > sHard.legitimacy)
    }

    @Test
    fun quarterPacePpy4LabelsCourtActionsAndYearDuration() {
        val q = GameState()
        assertEquals(4, q.ppy)
        assertEquals(1, q.courtActions)
        assertEquals(0.5, q.f, 1e-6)
        assertEquals(2, q.k)
        assertEquals(684, GameEngine.totalRounds)
        assertEquals(684, GameEngine.totalRoundsFor(4))
        assertEquals(1800, GameEngine.yearOf(3))
        assertEquals(1801, GameEngine.yearOf(4))
        assertEquals("نيسان - حزيران 1800", GameEngine.periodLabel(1))

        // Calibrator enforces quarterly caps cleanly (cost 20%, now +-2, gain <= 3, revenue delay >= 2)
        val opt = DecisionOption(
            text = "ت",
            result = "ن",
            cost = 20_000_000.0,
            now = Effects(legitimacy = 8),
            delay = 1,
            lineChanges = listOf(LineChange("land", 1_000_000.0)),
        )
        val calQuarter = Calibrator.calibrateOption(opt, 18_000_000.0, f = 0.5)
        assertEquals(18_000_000.0 * Calibrator.MAX_COST_SHARE, calQuarter.cost, 1.0)
        assertEquals(2, calQuarter.now.legitimacy)
        assertEquals(2, calQuarter.delay)
        assertTrue(calQuarter.calibrated)

        // Two quarters produce expected quarterly budget scaling under neutral choices
        val s4 = GameState(treasury = 20_000_000.0)
        s4.plan = neutralPlan(); s4.picks = mutableListOf(0, 0, 0)
        val r4a = engine.resolveRound(s4)
        s4.plan = neutralPlan(); s4.picks = mutableListOf(0, 0, 0)
        val r4b = engine.resolveRound(s4)

        assertEquals(1, s4.courtActions)
        assertEquals(4, r4a.ppy)
        assertEquals(4, r4b.ppy)
        assertTrue(r4a.revenue > 0.0 && r4b.revenue > 0.0)
    }

    @Test
    fun nationalProjectsCatalogInstallmentsPauseAndCompletion() {
        assertEquals(31, projectCatalog.size)
        val s = GameState(treasury = 50_000_000.0)
        val printPress = projectDef("printing")!!
        val early = projectDef("ottoman_bank")!! // fromYear = 1855
        assertTrue(ProjectRules.isAvailable(s, printPress))
        assertFalse(ProjectRules.isAvailable(s, early))

        // Faster schedule costs more overall due to RUSH_PREMIUM
        val slowCost = ProjectRules.totalCost(s, printPress, printPress.maxHalves)
        val fastCost = ProjectRules.totalCost(s, printPress, printPress.minHalves)
        assertTrue(fastCost > slowCost)
        assertNotNull(ProjectRules.paybackYears(s, projectDef("irrigation")!!, 6))
        assertNull(ProjectRules.paybackYears(s, printPress, 4))

        val started = ProjectRules.start(s, "printing", printPress.minHalves)
        assertTrue(started.ok)
        assertNotNull(s.project)

        // Pausing skips installment payment and clears stalled
        ProjectRules.setPaused(s, true)
        val pausedStep = ProjectRules.fund(s)
        assertEquals(0.0, pausedStep.spent, 0.01)
        assertEquals(0, s.project!!.funded)

        // Insufficient treasury increments stalled
        ProjectRules.setPaused(s, false)
        val savedTreasury = s.treasury
        s.treasury = 1.0
        val stalledStep = ProjectRules.fund(s)
        assertEquals(0.0, stalledStep.spent, 0.01)
        assertEquals(1, s.project!!.stalled)
        assertTrue(stalledStep.notes.any { it.contains("تعثر") })

        // Restore treasury and complete remaining installments
        s.treasury = savedTreasury
        val eduBefore = s.education
        while (s.project != null) {
            val step = ProjectRules.fund(s)
            assertTrue(step.spent > 0.0)
        }
        assertTrue(ProjectRules.has(s, "printing"))
        assertTrue(s.education > eduBefore)
    }

    @Test
    fun projectSuggestionsPrioritizeCurrentCrises() {
        val s = GameState(treasury = 100_000.0, army = 20, education = 15)
        s.econ.grain = 160.0
        s.econ.corruption = 70.0
        s.econ.inflation = 8.0
        val suggestions = ProjectRules.suggestions(s, limit = 3)
        assertTrue(suggestions.isNotEmpty())
        assertTrue(suggestions.size <= 3)
        assertTrue(suggestions.all { it.reason.isNotEmpty() })
    }

    @Test
    fun foreignEconomiesGrowDeterministicallyOverTime() {
        val a = GameState(seed = 42)
        val b = GameState(seed = 42)
        val britainStart = a.countries["britain"]!!.economy
        for (i in 0 until 20) {
            a.round = i
            b.round = i
            ForeignRules.step(a)
            ForeignRules.step(b)
        }
        assertTrue(a.countries["britain"]!!.economy > britainStart)
        assertEquals(a.countries["britain"]!!.economy, b.countries["britain"]!!.economy, 1e-6)
        assertTrue(ForeignRules.ottomanEconomy(a) > 0.0)
        assertEquals(
            EconomyRules.halfYearRevenue(a.budget, a.stability, econ = a.econ),
            EconomyRules.halfYearRevenue(b.budget, b.stability, econ = b.econ),
            1e-6,
        )
    }

    @Test
    fun emergencyBorrowingAppliesIntegerPenaltiesEveryKthQuarterInPpy4() {
        val s = GameState(ppy = 4, treasury = 100_000.0)
        val plan = neutralPlan()
        plan.decisions[0].options[0] = DecisionOption(text = "كلفة", result = "ن", cost = 2_000_000.0)
        s.plan = plan
        s.picks = mutableListOf(0, 0, 0)

        val legStart = s.legitimacy
        val armyStart = s.army
        val rep1 = engine.resolveRound(s)
        assertTrue(rep1.emergencyBorrowed > 0.0)
        assertEquals(1, s.emergencyStreak)
        assertEquals(legStart, s.legitimacy)
        assertEquals(armyStart, s.army)
        assertEquals(4, rep1.ppy)

        val plan2 = neutralPlan()
        plan2.decisions[0].options[0] = DecisionOption(text = "كلفة", result = "ن", cost = 2_000_000.0)
        s.plan = plan2
        s.picks = mutableListOf(0, 0, 0)
        val rep2 = engine.resolveRound(s)
        assertTrue(rep2.emergencyBorrowed > 0.0)
        assertEquals(2, s.emergencyStreak)
        assertEquals(legStart - 1, s.legitimacy)
        assertEquals(armyStart - 2, s.army)
    }

    @Test
    fun estimateRoundTreasuryAndDynamicPeriodWordingMatchRules() {
        val s = GameState(ppy = 4, treasury = 500_000.0)
        val plan = neutralPlan()
        plan.decisions[0].options[0] = DecisionOption(text = "كلفة", result = "ن", cost = 1_500_000.0)
        s.plan = plan
        s.picks = mutableListOf(0, 0, 0)

        val proj = GameEngine.estimateRoundTreasury(s)
        assertTrue(proj.willBorrowEmergency)
        assertTrue(proj.emergencyShortfall > 0.0)
        assertTrue(proj.estimatedEmergencyLoan > proj.emergencyShortfall)

        s.courtActions = 0
        assertEquals("استنفدت إجراءات الديوان لربع السنة هذا", PowerRules.appease(s, "janissaries").message)
    }

    @Test
    fun climateHarvestLastsFullTwoQuartersWhenTriggeredOnOddRound() {
        var foundSeed = -1
        for (seed in 1800 until 2500) {
            val probe = GameState(seed = seed, ppy = 4, round = 9)
            ClimateRules.step(probe)
            if (probe.econ.provinces.values.any { it.harvest != 1.0 }) {
                foundSeed = seed
                break
            }
        }
        assertTrue(foundSeed >= 1800)

        val s = GameState(seed = foundSeed, ppy = 4, round = 9)
        ClimateRules.step(s)
        val hit = s.econ.provinces.values.first { it.harvest != 1.0 }
        assertEquals(2, hit.harvestRounds)

        s.round = 0 // grace avoids triggering another shock while counting down
        ClimateRules.step(s)
        assertTrue(hit.harvest != 1.0)
        assertEquals(1, hit.harvestRounds)

        ClimateRules.step(s)
        assertEquals(1.0, hit.harvest, 1e-6)
        assertEquals(0, hit.harvestRounds)
    }

    @Test
    fun migratesLegacyPpy2SaveToLockedQuarterlyPpy4() {
        val base = GameState(seed = 99, difficulty = 1)
        val json = base.toJson()
        json.put("ppy", 2)
        json.put("round", 41) // year 1820 in ppy=2 -> round 82 in ppy=4
        json.put("courtActions", 2)
        json.put("defaultRound", 40)
        json.put("lastAusterityRound", 35)
        json.put("emergencyStreak", 3)

        json.put(
            "pending",
            JSONArray().put(
                JSONObject().apply {
                    put("due", 43)
                    put("effects", Effects().toJson())
                    put("lines", JSONArray())
                    put("source", "مؤجل")
                },
            ),
        )
        json.put(
            "history",
            JSONArray().put(
                JSONObject().apply {
                    put("round", 40)
                    put("category", "داخلي")
                    put("title", "إصلاح")
                    put("choice", "أ")
                    put("result", "تم")
                },
            ),
        )
        json.put(
            "gazette",
            JSONArray().put(
                JSONObject().apply {
                    put("round", 40)
                    put("period", "كانون الثاني - حزيران 1820")
                    put("headlines", JSONArray(listOf("خبر أول", "خبر ثانٍ")))
                    put("policy", "free")
                },
            ),
        )
        json.put(
            "intel",
            JSONArray().put(
                JSONObject().apply {
                    put("round", 39)
                    put("target", "russia")
                    put("claim", "تقرير سري")
                    put("reliability", 80)
                    put("truth", true)
                    put("revealed", false)
                },
            ),
        )
        json.put(
            "lastFullReport",
            JSONObject().apply {
                put("round", 40)
                put("ppy", 2)
                put("period", "كانون الثاني - حزيران 1820")
                put("revenue", 8_000_000.0)
                put("spending", 7_500_000.0)
                put("interest", 200_000.0)
                put("oneTimeCosts", 0.0)
                put("loans", 0.0)
                put("emergencyBorrowed", 0.0)
                put("dueNotes", JSONArray())
                put("indicatorChanges", JSONObject().apply { put("الشرعية", 1) })
                put("treasuryAfter", 3_300_000.0)
                put("debtAfter", 6_000_000.0)
                put("repaid", 0.0)
                put("events", JSONArray())
                put("courtLog", JSONArray())
                put("projectSpend", 0.0)
                put("warSpend", 0.0)
                put("dadPaid", 0.0)
            },
        )
        json.getJSONObject("econ").apply {
            put("lastEconSystemRound", 38)
            put("deficitStreak", 2)
            put("surplusStreak", 1)
            val provs = getJSONArray("provinces")
            for (i in 0 until provs.length()) {
                val po = provs.getJSONObject(i)
                if (po.optString("key") == "rumelia") po.put("harvestRounds", 3)
            }
        }
        val powersArr = json.getJSONArray("powers")
        for (i in 0 until powersArr.length()) {
            val po = powersArr.getJSONObject(i)
            if (po.optString("key") == "ulema") po.put("containedUntil", 45)
        }
        val countriesArr = json.getJSONArray("countries")
        for (i in 0 until countriesArr.length()) {
            val co = countriesArr.getJSONObject(i)
            if (co.optString("key") == "britain") co.put("lastTreaty", 37)
        }
        json.put(
            "wars",
            JSONArray().put(
                JSONObject().apply {
                    put("enemy", "russia")
                    put("startRound", 39)
                    put("byUs", true)
                    put("score", 10.0)
                    put("halves", 2)
                },
            ),
        )
        json.put(
            "projects",
            JSONArray().put(
                JSONObject().apply {
                    put("power", "janissaries")
                    put("kind", "contain")
                    put("remaining", 5)
                },
            ),
        )
        json.put(
            "project",
            JSONObject().apply {
                put("key", "printing")
                put("halves", 6)
                put("installment", 500_000.0)
                put("startRound", 38)
                put("funded", 2)
                put("stalled", 1)
                put("paused", false)
            },
        )

        val migrated = GameState.fromJson(json)
        assertEquals(4, migrated.ppy)
        assertEquals(82, migrated.round)
        assertEquals(1820, migrated.year)
        assertEquals(1, migrated.courtActions)
        assertEquals(80, migrated.defaultRound)
        assertEquals(70, migrated.lastAusterityRound)
        assertEquals(6, migrated.emergencyStreak)
        assertEquals(86, migrated.pending.first().dueRound)
        assertEquals(80, migrated.history.first().round)
        assertEquals(80, migrated.gazette.first().round)
        assertEquals(78, migrated.intel.first().round)
        assertEquals(80, migrated.lastFullReport?.round)
        assertEquals(4, migrated.lastFullReport?.ppy)
        assertEquals(76, migrated.econ.lastEconSystemRound)
        assertEquals(4, migrated.econ.deficitStreak)
        assertEquals(2, migrated.econ.surplusStreak)
        assertEquals(6, migrated.econ.provinces["rumelia"]!!.harvestRounds)
        assertEquals(90, migrated.powers["ulema"]!!.containedUntil)
        assertEquals(74, migrated.countries["britain"]!!.lastTreaty)
        assertEquals(78, migrated.wars.first().startRound)
        assertEquals(4, migrated.wars.first().halves)
        assertEquals(10, migrated.projects.first().remaining)
        assertEquals(12, migrated.project!!.halves)
        assertEquals(250_000.0, migrated.project!!.installment, 0.01)
        assertEquals(76, migrated.project!!.startRound)
        assertEquals(4, migrated.project!!.funded)
        assertEquals(2, migrated.project!!.stalled)
    }

    @Test
    fun lateProjectsAnd1970Caps() {
        val s = GameState(treasury = 500_000_000.0)
        val lateKeys = listOf("oil_refineries", "national_airforce", "universities", "highways", "heavy_industry")
        for (k in lateKeys) {
            assertFalse(ProjectRules.isAvailable(s, projectDef(k)!!))
        }
        s.round = (1955 - 1800) * 4
        for (k in lateKeys) {
            s.courtActions = 1
            val def = projectDef(k)!!
            assertTrue(ProjectRules.isAvailable(s, def))
            assertTrue(ProjectRules.start(s, k, def.minHalves * 2).ok)
            while (s.project != null) {
                ProjectRules.fund(s)
            }
            assertTrue(ProjectRules.has(s, k))
        }

        s.countries["britain"]!!.economy = 12_000.0
        s.round = (1960 - 1800) * 4
        ForeignRules.step(s)
        assertTrue(s.countries["britain"]!!.economy > 12_000.0 && s.countries["britain"]!!.economy <= 25_000.0)

        s.sultan = SultanState("السلطان عثمان الثالث", "reformer", 1962, 6)
        s.series = (1..750).map { idx ->
            StatPoint(
                round = idx,
                treasury = 3_000_000.0,
                debt = 6_000_000.0,
                revenue = 18_000_000.0,
                legitimacy = 65,
                stability = 55,
                army = 45,
                foreign = 45,
                education = 15,
                population = 30.0,
                literacy = 10.0,
                inflation = 2.0,
            )
        }.toMutableList()
        val restored = GameState.fromJson(s.toJson())
        assertEquals(1962, restored.sultan.enthronedYear)
        assertEquals(750, restored.series.size)
        assertTrue(restored.countries["britain"]!!.economy > 12_000.0)

        s.plan = neutralPlan()
        s.picks = mutableListOf(0, 0, 0)
        engine.resolveRound(s)
        assertEquals(751, s.series.size)
    }
}
