package com.ottoman.empire1800

import com.ottoman.empire1800.data.anchorsFor
import com.ottoman.empire1800.data.effectiveAbout
import com.ottoman.empire1800.data.frontierRegionDefs
import com.ottoman.empire1800.data.historyAnchorList
import com.ottoman.empire1800.data.projectDef
import com.ottoman.empire1800.data.subProvinceDefs
import com.ottoman.empire1800.data.techCatalog
import com.ottoman.empire1800.data.techDef
import com.ottoman.empire1800.engine.Calibrator
import com.ottoman.empire1800.engine.GameEngine
import com.ottoman.empire1800.engine.RoundParser
import com.ottoman.empire1800.models.ActiveResearch
import com.ottoman.empire1800.models.Decision
import com.ottoman.empire1800.models.DecisionCategory
import com.ottoman.empire1800.models.DecisionOption
import com.ottoman.empire1800.models.EconomicSystemKey
import com.ottoman.empire1800.models.FactoryKey
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.RoundPlan
import com.ottoman.empire1800.models.powerDefs
import com.ottoman.empire1800.services.CrisisRules
import com.ottoman.empire1800.services.DebtRules
import com.ottoman.empire1800.services.DeepEconomyRules
import com.ottoman.empire1800.services.DiplomacyRules
import com.ottoman.empire1800.services.EconomyRules
import com.ottoman.empire1800.services.ForeignRules
import com.ottoman.empire1800.services.FrontierRules
import com.ottoman.empire1800.services.GrandVizierRules
import com.ottoman.empire1800.services.IndustryRules
import com.ottoman.empire1800.services.MilestoneRules
import com.ottoman.empire1800.services.PowerRules
import com.ottoman.empire1800.services.ProjectRules
import com.ottoman.empire1800.services.ProvinceRules
import com.ottoman.empire1800.services.ResearchRules
import com.ottoman.empire1800.services.SpyRules
import com.ottoman.empire1800.services.StabilityRules
import com.ottoman.empire1800.services.WarRules
import com.ottoman.empire1800.services.buildRoundPrompt
import com.ottoman.empire1800.services.costShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CourtAndDiplomacyTest {

    @Test
    fun powerAppeaseAndProjectsUpdateInfluenceAndSatisfaction() {
        val s = GameState(treasury = 20_000_000.0)
        s.courtActions = 2
        val jan = s.powers["janissaries"]!!
        val satBefore = jan.satisfaction
        val res = PowerRules.appease(s, "janissaries")
        assertTrue(res.ok)
        assertTrue(jan.satisfaction > satBefore)
        assertEquals(1, s.courtActions)

        val proj = PowerRules.startProject(s, "janissaries", "reform")
        assertTrue(proj.ok)
        assertTrue(PowerRules.hasProject(s, "janissaries"))
        assertEquals(0, s.courtActions)

        // Out of court actions rejects another court action
        val extra = PowerRules.appease(s, "ulema")
        assertFalse(extra.ok)

        val infBefore = jan.influence
        for (i in 0 until 12) {
            s.round = i
            PowerRules.step(s, emergency = false)
        }
        assertTrue(jan.influence < infBefore)
        assertFalse(PowerRules.hasProject(s, "janissaries"))
    }

    @Test
    fun dissolveIsDeterministicPerSeedAndLowDangerSucceeds() {
        val s1 = GameState(seed = 123, treasury = 20_000_000.0)
        val s2 = GameState(seed = 123, treasury = 20_000_000.0)
        s1.powers["tax_farmers"]!!.satisfaction = 80.0
        s1.powers["tax_farmers"]!!.influence = 20.0
        s2.powers["tax_farmers"]!!.satisfaction = 80.0
        s2.powers["tax_farmers"]!!.influence = 20.0

        assertEquals("خطر منخفض", PowerRules.dissolveRisk(s1.powers["tax_farmers"]!!))
        val r1 = PowerRules.dissolve(s1, "tax_farmers")
        val r2 = PowerRules.dissolve(s2, "tax_farmers")
        assertTrue(r1.ok)
        assertEquals(r1.ok, r2.ok)
        assertTrue(s1.powers["tax_farmers"]!!.dissolved)
    }

    @Test
    fun spyLevelsGenerateAndRevealReportsCappedAtMaxKept() {
        val s = GameState(treasury = 20_000_000.0)
        s.courtActions = 5
        assertTrue(DiplomacyRules.setSpyLevel(s, "russia", 2).ok)
        assertTrue(DiplomacyRules.setSpyLevel(s, "britain", 2).ok)
        assertTrue(DiplomacyRules.setDomesticSpy(s, 2).ok)
        assertEquals(2, s.countries["russia"]!!.intelQuality)

        for (i in 0 until 12) {
            s.round = i
            SpyRules.step(s)
        }
        assertTrue(s.intel.isNotEmpty())
        assertTrue(s.intel.size <= SpyRules.MAX_KEPT)
        assertTrue(s.intel.any { it.revealed })
    }

    @Test
    fun embassiesCreditFactorAndTradeTreatyWithPerk() {
        val s = GameState(treasury = 20_000_000.0)
        s.courtActions = 5
        assertTrue(DiplomacyRules.openEmbassy(s, "britain").ok)
        assertEquals(3, s.countries["britain"]!!.intelQuality)

        // High relations raise creditFactor
        s.countries["britain"]!!.relation = 90.0
        s.countries["france"]!!.relation = 85.0
        s.countries["austria"]!!.relation = 80.0
        assertTrue(DiplomacyRules.creditFactor(s) > 1.15)

        // Treaty with Britain drops France slightly and adds delayed customs increase (1.5x with trade perk)
        s.completed.add("ports") // perk = trade
        val franceBefore = s.countries["france"]!!.relation
        val treaty = DiplomacyRules.tradeTreaty(s, "britain")
        assertTrue(treaty.ok)
        assertTrue(s.countries["france"]!!.relation < franceBefore)
        assertEquals(1, s.pending.size)
        val expectedGain = s.budget.totalRevenue * DiplomacyRules.TREATY_CUSTOMS_GAIN * 1.5
        assertEquals(expectedGain, s.pending.first().lines.first().delta, 1.0)
    }

    @Test
    fun repayDebtReducesTreasuryAndDebtPrioritizingInternalFirst() {
        val engine = GameEngine()
        val s = GameState(treasury = 5_000_000.0, debt = 8_000_000.0, foreignDebt = 3_000_000.0)
        // Internal = 5M, foreign = 3M; paying 5M clears internal and leaves 3M foreign
        val paid = engine.repayDebt(s, 5_000_000.0)
        assertEquals(5_000_000.0, paid, 0.01)
        assertEquals(0.0, s.treasury, 0.01)
        assertEquals(3_000_000.0, s.debt, 0.01)
        assertEquals(3_000_000.0, s.foreignDebt, 0.01)
        assertEquals(0.0, DebtRules.internalDebt(s), 0.01)
    }

    @Test
    fun janissaryDissolutionRequiresReadinessAndTriggersSpecificOutcomes() {
        val unprepared = GameState(seed = 42, treasury = 50_000_000.0)
        val r0 = PowerRules.janissaryReadiness(unprepared)
        assertTrue(r0.hasUlema)
        assertFalse(r0.hasMilitary)
        assertFalse(r0.lowInfluence)
        assertTrue(r0.successChance < 0.20)
        assertEquals("تمرد مرجح", PowerRules.dissolveRisk(unprepared.powers["janissaries"]!!, unprepared))

        val leg0 = unprepared.legitimacy
        val army0 = unprepared.army
        val failRes = PowerRules.dissolve(unprepared, "janissaries")
        assertFalse(failRes.ok)
        assertFalse(unprepared.powers["janissaries"]!!.dissolved)
        assertEquals(leg0 - 10, unprepared.legitimacy)
        assertEquals(army0 - 8, unprepared.army)

        val prepared = GameState(seed = 42, treasury = 50_000_000.0)
        prepared.powers["ulema"]!!.satisfaction = 70.0
        prepared.army = 65
        prepared.powers["janissaries"]!!.influence = 45.0
        assertEquals("جاهز للحل", PowerRules.dissolveRisk(prepared.powers["janissaries"]!!, prepared))

        val armySpend0 = prepared.budget.lineValue("army")
        val okRes = PowerRules.dissolve(prepared, "janissaries")
        assertTrue(okRes.ok)
        assertTrue(prepared.powers["janissaries"]!!.dissolved)
        assertEquals(55, prepared.army)
        assertTrue(prepared.budget.lineValue("army") < armySpend0)
        assertTrue(prepared.world.reforms.any { it.contains("الانكشارية") })

        val modernized = GameState(seed = 42, treasury = 50_000_000.0)
        modernized.completed.add("modern_army")
        modernized.powers["ulema"]!!.satisfaction = 70.0
        modernized.powers["janissaries"]!!.influence = 45.0
        modernized.army = 60
        assertTrue(PowerRules.dissolve(modernized, "janissaries").ok)
        assertEquals(60, modernized.army)
    }

    @Test
    fun powerSpecificDissolutionsAndProjectCompletionRewardsWorkWithScaling() {
        // tax_farmers
        val sTax = GameState(treasury = 50_000_000.0, ppy = 4)
        sTax.powers["tax_farmers"]!!.satisfaction = 90.0
        sTax.powers["tax_farmers"]!!.influence = 20.0
        val corr0 = sTax.econ.corruption
        assertTrue(PowerRules.dissolve(sTax, "tax_farmers").ok)
        assertEquals(corr0 - 8.0, sTax.econ.corruption, 0.01)
        assertEquals(1, sTax.pending.size)
        assertEquals(sTax.round + 4, sTax.pending.first().dueRound)
        assertEquals("land", sTax.pending.first().lines.first().line)

        // mamluks slows egypt drift
        val sMam = GameState(treasury = 50_000_000.0)
        sMam.powers["mamluks"]!!.satisfaction = 90.0
        sMam.powers["mamluks"]!!.influence = 20.0
        sMam.econ.provinces["egypt"]!!.autonomy = 40.0
        assertTrue(PowerRules.dissolve(sMam, "mamluks").ok)
        assertEquals(30.0, sMam.econ.provinces["egypt"]!!.autonomy, 0.01)

        val sMamActive = GameState()
        sMamActive.econ.provinces["egypt"]!!.autonomy = 30.0
        sMam.econ.provinces["egypt"]!!.autonomy = 30.0
        ProvinceRules.step(sMamActive)
        ProvinceRules.step(sMam)
        assertTrue(sMam.econ.provinces["egypt"]!!.autonomy < sMamActive.econ.provinces["egypt"]!!.autonomy)

        // reform reward & danger multiplier
        val sRef = GameState(treasury = 50_000_000.0, ppy = 4)
        val leg0 = sRef.legitimacy
        assertTrue(PowerRules.startProject(sRef, "janissaries", "reform").ok)
        assertEquals(12, sRef.projects.first().remaining)
        for (i in 0 until 12) {
            sRef.round = minOf(i, 7)
            PowerRules.step(sRef, emergency = false)
        }
        assertTrue(sRef.powers["janissaries"]!!.reformed)
        assertEquals(leg0 + 2, sRef.legitimacy)
        val reformedDanger = sRef.powers["janissaries"]!!.danger
        sRef.powers["janissaries"]!!.reformed = false
        assertTrue(reformedDanger < sRef.powers["janissaries"]!!.danger)

        // merge reward & save/load roundtrip
        val sMerge = GameState(treasury = 50_000_000.0)
        val army0 = sMerge.army
        assertTrue(PowerRules.startProject(sMerge, "janissaries", "merge").ok)
        for (i in 0 until 10) {
            sMerge.round = minOf(i, 7)
            PowerRules.step(sMerge, emergency = false)
        }
        assertTrue(sMerge.powers["janissaries"]!!.merged)
        assertTrue(sMerge.powers["janissaries"]!!.dissolved)
        assertEquals(army0 + 5, sMerge.army)
        val restored = GameState.fromJson(sMerge.toJson())
        assertTrue(restored.powers["janissaries"]!!.merged)
    }

    @Test
    fun aiPromptCalibratorAndCatalogSyncWithDissolvedPowers() {
        val s = GameState(treasury = 50_000_000.0)
        s.round = 104 // year 1826 so modern_army (fromYear 1805) is available in catalog
        s.powers["ulema"]!!.satisfaction = 75.0
        s.army = 65
        s.powers["janissaries"]!!.influence = 40.0
        assertTrue(PowerRules.dissolve(s, "janissaries").ok)

        assertTrue(historyAnchorList.size >= 80)
        for (y in 1800..1970 step 5) {
            val list = anchorsFor(y)
            assertTrue("Expected 1..6 anchors for $y, got ${list.size}", list.size in 1..6)
        }
        assertTrue(anchorsFor(1826).any { it.contains("الواقعة الخيرية") })
        assertFalse(anchorsFor(1826, s).any { it.contains("الواقعة الخيرية") })
        assertFalse(anchorsFor(1805).any { it.contains("الحرب الباردة") || it.contains("حروب البلقان") })
        assertTrue(anchorsFor(1808).any { it.contains("علمدار مصطفى باشا") })
        assertTrue(anchorsFor(1815).any { it.contains("مؤتمر فيينا") })
        assertTrue(anchorsFor(1833).any { it.contains("خونكار إسكله سي") })
        assertTrue(anchorsFor(1838).any { it.contains("بالطة ليمان") })
        assertTrue(anchorsFor(1854).any { it.contains("أول قرض خارجي") })
        assertTrue(anchorsFor(1876).any { it.contains("الدستور العثماني الأول") })
        assertTrue(anchorsFor(1881).any { it.contains("مرسوم محرم") })
        assertTrue(anchorsFor(1900).any { it.contains("سكة حديد بغداد") && it.contains("الحجاز") })
        assertTrue(anchorsFor(1912).any { it.contains("البلقان") && it.contains("طرابلس") })
        assertTrue(anchorsFor(1916).any { it.contains("الحرب العالمية الأولى") })
        assertTrue(anchorsFor(1929).any { it.contains("صناعة النفط") && it.contains("1929") })
        assertTrue(anchorsFor(1936).any { it.contains("مونترو") })
        assertTrue(anchorsFor(1942).any { it.contains("الحرب العالمية الثانية") })
        assertTrue(anchorsFor(1953).any { it.contains("الحرب الباردة") })
        assertTrue(anchorsFor(1955).any { it.contains("حلف بغداد") && it.contains("السويس") })
        assertTrue(anchorsFor(1965).any { it.contains("التصنيع والتحديث التكنولوجي") })
        assertTrue(anchorsFor(1969).any { it.contains("البتروكيماويات") && it.contains("الحوسبة") })

        val modern = projectDef("modern_army")!!
        assertTrue(modern.about.contains("يغضب الانكشارية"))
        assertFalse(modern.effectiveAbout(s).contains("يغضب الانكشارية"))
        assertTrue(ProjectRules.suggestions(s, 5).any { it.def.key == "modern_army" })

        val prompt = buildRoundPrompt(s)
        assertTrue(prompt.contains("1800-1970"))
        assertTrue(prompt.contains("يُمنع اختراع حروب أو معاهدات أو أزمات وهمية"))
        assertTrue(prompt.contains("DISSOLVED POWERS"))
        assertTrue(prompt.contains("janissaries=الانكشارية"))

        val opt = DecisionOption(
            text = "خيار",
            result = "نتيجة",
            powerMood = mapOf("janissaries" to 5, "ulema" to 3),
            dissolve = "janissaries",
        )
        val dec = Decision(DecisionCategory.INTERNAL, "عنوان", "مكان", "مشهد", "معضلة", listOf(opt, opt, opt))
        val plan = RoundPlan("رأس", "ملخص", "", null, listOf(dec, dec, dec))
        val calibrated = Calibrator.calibrate(plan, s)
        assertFalse(calibrated.decisions[0].options[0].powerMood.containsKey("janissaries"))
        assertEquals(3, calibrated.decisions[0].options[0].powerMood["ulema"])
        assertEquals("", calibrated.decisions[0].options[0].dissolve)
    }

    @Test
    fun grandVizierEconomyActionsAndSpecialOpsWorkInParity() {
        val s = GameState(seed = 101, treasury = 50_000_000.0, ppy = 4)
        s.courtActions = 6
        assertEquals("traditional", s.grandVizier)

        // Appoint economist -> -10% project cost & +5 rating
        val normalCost = ProjectRules.totalCost(s, projectDef("printing")!!, 12)
        assertTrue(GrandVizierRules.appoint(s, "economist").ok)
        val econCost = ProjectRules.totalCost(s, projectDef("printing")!!, 12)
        assertTrue(econCost < normalCost)
        assertTrue(DebtRules.ratingFactors(s).any { it.label.contains("اقتصادي") && it.value == 5.0 })

        // Military vizier boosts war power & prompt mentions active vizier
        val basePower = WarRules.ourPower(s)
        assertTrue(GrandVizierRules.appoint(s, "military").ok)
        assertEquals(basePower * 1.10, WarRules.ourPower(s), 0.01)
        assertTrue(buildRoundPrompt(s).contains("صدر أعظم عسكري"))

        // Reformer reduces corruption drift
        s.grandVizier = "reformer"
        val corr0 = s.econ.corruption
        DeepEconomyRules.step(s, 0.0)
        assertTrue(s.econ.corruption < corr0)

        // Tactical Economy actions
        assertFalse(EconomyRules.grainSubsidy(s).ok)
        s.econ.grain = 150.0
        assertTrue(EconomyRules.grainSubsidy(s).ok)
        assertEquals(125.0, s.econ.grain, 0.01)

        assertTrue(EconomyRules.antiCorruption(s).ok)
        assertTrue(EconomyRules.palaceAusterity(s).ok)
        assertEquals(16, EconomyRules.austerityCooldownRemaining(s))
        assertFalse(EconomyRules.palaceAusterity(s).ok)

        // Differentiated domestic vs foreign spy previews, upkeep & special ops
        val dom01 = DiplomacyRules.spyCostPreview(s, 0, 1, isDomestic = true)
        val dom12 = DiplomacyRules.spyCostPreview(s, 1, 2, isDomestic = true)
        val dom02 = DiplomacyRules.spyCostPreview(s, 0, 2, isDomestic = true)
        assertEquals(costShare(s, 0.008), dom01.recruitCost, 0.01)
        assertEquals(costShare(s, 0.015), dom01.annualUpkeep, 0.01)
        assertEquals(costShare(s, 0.016), dom12.recruitCost, 0.01)
        assertEquals(costShare(s, 0.024), dom02.recruitCost, 0.01)
        assertEquals(costShare(s, 0.035), dom02.annualUpkeep, 0.01)

        val for01 = DiplomacyRules.spyCostPreview(s, 0, 1, isDomestic = false)
        val for12 = DiplomacyRules.spyCostPreview(s, 1, 2, isDomestic = false)
        val for02 = DiplomacyRules.spyCostPreview(s, 0, 2, isDomestic = false)
        assertEquals(costShare(s, 0.010), for01.recruitCost, 0.01)
        assertEquals(costShare(s, 0.012), for01.annualUpkeep, 0.01)
        assertEquals(costShare(s, 0.020), for12.recruitCost, 0.01)
        assertEquals(costShare(s, 0.030), for02.recruitCost, 0.01)
        assertEquals(costShare(s, 0.030), for02.annualUpkeep, 0.01)

        val admin0 = s.budget.spending["admin"] ?: 0.0
        val t0 = s.treasury
        assertTrue(DiplomacyRules.setDomesticSpy(s, 1).ok)
        assertEquals(costShare(s, 0.008), t0 - s.treasury, 0.01)
        assertEquals(costShare(s, 0.015), (s.budget.spending["admin"] ?: 0.0) - admin0, 0.01)
        val t1 = s.treasury
        assertTrue(DiplomacyRules.setDomesticSpy(s, 2).ok)
        assertEquals(costShare(s, 0.016), t1 - s.treasury, 0.01)
        assertEquals(costShare(s, 0.035), (s.budget.spending["admin"] ?: 0.0) - admin0, 0.01)

        val tDomOp = s.treasury
        assertTrue(DiplomacyRules.domesticSpecialOp(s).ok)
        assertEquals(costShare(s, 0.015), tDomOp - s.treasury, 0.01)

        val tFor = s.treasury
        assertTrue(DiplomacyRules.setSpyLevel(s, "russia", 2).ok)
        assertEquals(costShare(s, 0.030), tFor - s.treasury, 0.01)
        WarRules.declare(s, "russia", byUs = true)
        val wScore0 = WarRules.warWith(s, "russia")!!.score
        val tForOp = s.treasury
        assertTrue(DiplomacyRules.foreignSpecialOp(s, "russia").ok)
        assertEquals(costShare(s, 0.028), tForOp - s.treasury, 0.01)
        assertEquals(wScore0 + 5.0, WarRules.warWith(s, "russia")!!.score, 0.01)

        // dissolveSuccessChance + detail
        assertEquals(1.0, PowerRules.dissolveSuccessChance(s, s.powers["merchants"]!!), 0.001)
        val janFail = PowerRules.dissolve(s, "janissaries")
        assertFalse(janFail.ok)
        assertTrue(!janFail.detail.isNullOrEmpty())
    }

    @Test
    fun defensiveAlliancesTradeoffsAndPersistentDecrees() {
        val s = GameState(seed = 2026, treasury = 100_000_000.0)
        s.courtActions = 20

        // 1) Alliance gating & rival penalties
        s.countries["france"]!!.relation = 79.0
        assertFalse(DiplomacyRules.signAlliance(s, "france").ok)

        s.countries["britain"]!!.relation = 85.0
        val frBefore = s.countries["france"]!!.relation
        val basePower = WarRules.ourPower(s)
        assertTrue(DiplomacyRules.signAlliance(s, "britain").ok)
        assertTrue(s.countries["britain"]!!.alliance)
        assertEquals(frBefore - 8.0, s.countries["france"]!!.relation, 0.01)
        assertEquals(basePower * 1.12, WarRules.ourPower(s), 0.01)
        assertTrue(s.world.alliances.any { it.contains("بريطانيا") })

        // Low relation breaks alliance
        s.countries["france"]!!.relation = 85.0
        s.countries["germany"]!!.relation = 60.0
        assertTrue(DiplomacyRules.signAlliance(s, "france").ok)
        s.countries["france"]!!.relation = 40.0
        val notes = DiplomacyRules.step(s)
        assertFalse(s.countries["france"]!!.alliance)
        assertTrue(notes.any { it.contains("فرنسا") })

        // War against active ally revokes alliance
        WarRules.declare(s, "britain", byUs = false)
        assertFalse(s.countries["britain"]!!.alliance)

        // 2) Interconnected trade-offs
        val rus0 = s.countries["russia"]!!.relation
        val aus0 = s.countries["austria"]!!.relation
        assertTrue(ProvinceRules.suppress(s, "rumelia").ok)
        assertEquals(rus0 - 4.0, s.countries["russia"]!!.relation, 0.01)
        assertEquals(aus0 - 4.0, s.countries["austria"]!!.relation, 0.01)

        s.econ.provinces["egypt"]!!.autonomy = 35.0
        s.econ.provinces["anatolia"]!!.autonomy = 3.0
        s.econ.provinces["anatolia"]!!.mood = 65.0
        assertTrue(ProvinceRules.privileges(s, "syria").ok)
        assertEquals(37.0, s.econ.provinces["egypt"]!!.autonomy, 0.01)
        assertEquals(3.0, s.econ.provinces["anatolia"]!!.autonomy, 0.01)

        val merch0 = s.powers["merchants"]!!.satisfaction
        assertTrue(PowerRules.appease(s, "janissaries").ok)
        assertEquals(merch0 - 2.0, s.powers["merchants"]!!.satisfaction, 0.01)
        s.powers["merchants"]!!.dissolved = true
        val ul0 = s.powers["ulema"]!!.satisfaction
        assertTrue(PowerRules.appease(s, "janissaries").ok)
        assertEquals(ul0 - 2.0, s.powers["ulema"]!!.satisfaction, 0.01)

        // 3) Persistent Royal Decrees, prompt inclusion & JSON roundtrip
        assertTrue(s.decrees.isNotEmpty())
        s.countries["austria"]!!.relation = 85.0
        assertTrue(DiplomacyRules.signAlliance(s, "austria").ok)
        val prompt = buildRoundPrompt(s)
        assertTrue(prompt.contains("تحالفات دفاعية قائمة"))
        assertTrue(prompt.contains("فرمانات السلطان الأخيرة"))

        for (i in 0 until 10) {
            GrandVizierRules.appoint(s, if (i % 2 == 0) "reformer" else "economist")
            s.courtActions = 5
        }
        assertEquals(8, s.decrees.size)

        val restored = GameState.fromJson(s.toJson())
        assertTrue(restored.countries["austria"]!!.alliance)
        assertEquals(8, restored.decrees.size)

        val legacyJson = s.toJson().apply { remove("decrees") }
        val restoredLegacy = GameState.fromJson(legacyJson)
        assertTrue(restoredLegacy.decrees.isEmpty())
    }

    @Test
    fun crisisAlertsMilestonesAndLastReportPersistence() {
        // 1) CrisisAlerts evaluation & Court tab routing
        val sc = GameState(treasury = 50_000_000.0)
        sc.powers["janissaries"]!!.influence = 30.0
        sc.powers["mamluks"]!!.influence = 30.0
        assertEquals(0, CrisisRules.evaluate(sc).size)

        sc.powers["janissaries"]!!.influence = 90.0
        sc.powers["janissaries"]!!.satisfaction = 20.0
        sc.econ.provinces["egypt"]!!.autonomy = 75.0
        sc.econ.grain = 160.0
        sc.econ.inflation = 9.0
        WarRules.declare(sc, "russia", byUs = false)
        sc.wars.first().score = -25.0
        val crises = CrisisRules.evaluate(sc)
        assertEquals(4, crises.size)
        assertEquals(0, crises.first { it.id == "power_danger" }.courtTabIndex)
        assertEquals(4, crises.first { it.id == "province_autonomy" }.courtTabIndex)
        assertEquals(3, crises.first { it.id == "economic_crisis" }.courtTabIndex)
        assertEquals(1, crises.first { it.id == "losing_war" }.courtTabIndex)

        // 2) All 6 Milestones unlock & persist cleanly
        val sm = GameState(seed = 42, treasury = 100_000_000.0)
        sm.powers["ulema"]!!.satisfaction = 70.0
        sm.army = 65
        sm.powers["janissaries"]!!.influence = 40.0
        assertTrue(PowerRules.dissolve(sm, "janissaries").ok)
        assertTrue(sm.milestones.contains("abolish_janissaries"))

        sm.completed.add("railway")
        MilestoneRules.evaluate(sm)
        assertTrue(sm.milestones.contains("build_railway"))

        sm.round = 200 // 1850
        MilestoneRules.evaluate(sm)
        assertTrue(sm.milestones.contains("survive_50_years"))

        sm.foreignDebt = 1_000_000.0
        sm.debt = 1_000_000.0
        MilestoneRules.evaluate(sm)
        assertTrue(sm.hadForeignDebt)
        GameEngine().repayDebt(sm, 1_000_000.0)
        assertTrue(sm.milestones.contains("clear_foreign_debt"))

        WarRules.declare(sm, "persia", byUs = true)
        WarRules.conclude(sm, sm.wars.first(), 25.0)
        assertTrue(sm.milestones.contains("win_major_war"))

        sm.round = 300 // 1875 with all 6 provinces intact
        MilestoneRules.evaluate(sm)
        assertTrue(sm.milestones.contains("preserve_empire_1875"))
        assertEquals(6, sm.milestones.size)

        // 3) Resolve round saves lastFullReport and round-trips through JSON cleanly + legacy fallback
        val engine = GameEngine()
        val opt = DecisionOption(text = "خيار", result = "نتيجة")
        val dec = Decision(DecisionCategory.INTERNAL, "عنوان", "مكان", "مشهد", "معضلة", listOf(opt, opt, opt))
        sm.plan = RoundPlan("رأس", "ملخص", "", null, listOf(dec, dec, dec))
        sm.picks = mutableListOf(0, 0, 0)
        val rep = engine.resolveRound(sm)
        assertNotNull(sm.lastFullReport)
        assertEquals(rep.period, sm.lastFullReport!!.period)

        val restored = GameState.fromJson(sm.toJson())
        assertEquals(6, restored.milestones.size)
        assertNotNull(restored.lastFullReport)
        assertEquals(rep.period, restored.lastFullReport!!.period)

        val legacyJson = sm.toJson().apply {
            remove("milestones")
            remove("hadForeignDebt")
            remove("wonMajorWar")
            remove("lastFullReport")
        }
        val restoredLegacy = GameState.fromJson(legacyJson)
        assertNull(restoredLegacy.lastFullReport)
        assertTrue(restoredLegacy.milestones.contains("abolish_janissaries"))
        assertTrue(restoredLegacy.milestones.contains("build_railway"))
    }

    @Test
    fun industryEconomicSystemsOilDiscoveryAndFrontierRegions() {
        assertEquals(25, subProvinceDefs.size)
        assertEquals(4, frontierRegionDefs.size)

        val s = GameState(ppy = 4, treasury = 200_000_000.0)
        s.courtActions = 50

        // 1) Resource & control gating + max level 3 + slot limits + demolish 20%*lvl refund
        assertFalse(IndustryRules.canBuildInSubProvince(s.econ, "anatolia_istanbul", FactoryKey.COAL_MINE, 1800).ok)
        assertFalse(IndustryRules.canBuildInSubProvince(s.econ, "anatolia_trabzon", FactoryKey.TRADE_DEPOT, 1800).ok)
        assertFalse(IndustryRules.canBuildInSubProvince(s.econ, "algeria_algiers", FactoryKey.TEXTILE, 1800).ok)

        assertTrue(IndustryRules.buildOrUpgradeFactory(s, "syria_aleppo", FactoryKey.TEXTILE).ok)
        assertTrue(IndustryRules.buildOrUpgradeFactory(s, "syria_aleppo", FactoryKey.TEXTILE).ok)
        assertTrue(IndustryRules.buildOrUpgradeFactory(s, "syria_aleppo", FactoryKey.TEXTILE).ok)
        assertFalse(IndustryRules.buildOrUpgradeFactory(s, "syria_aleppo", FactoryKey.TEXTILE).ok)
        assertTrue(IndustryRules.buildOrUpgradeFactory(s, "syria_aleppo", FactoryKey.PAPER_MILL).ok)
        assertFalse(IndustryRules.buildOrUpgradeFactory(s, "syria_aleppo", FactoryKey.FOOD_MILL).ok)

        val tBeforeScrap = s.treasury
        val expectedScrap = costShare(s, 0.055 * 0.2 * 3)
        assertTrue(IndustryRules.demolishFactory(s, "syria_aleppo", FactoryKey.TEXTILE).ok)
        assertEquals(expectedScrap, s.treasury - tBeforeScrap, 0.01)

        // 2) Paper mill discount (5%/lvl cap 25%) + literacy growth + coal 1.25x + arms power/cost + food mill
        val spendBase = EconomyRules.halfYearSpending(s.budget, 0.5, s.grandVizier, s.econ)
        repeat(2) { assertTrue(IndustryRules.buildOrUpgradeFactory(s, "syria_aleppo", FactoryKey.PAPER_MILL).ok) }
        assertEquals(0.15, IndustryRules.paperMillAdminDiscount(s.econ), 1e-6)
        assertTrue(EconomyRules.halfYearSpending(s.budget, 0.5, s.grandVizier, s.econ) < spendBase)
        repeat(3) { assertTrue(IndustryRules.buildOrUpgradeFactory(s, "anatolia_istanbul", FactoryKey.PAPER_MILL).ok) }
        assertEquals(0.25, IndustryRules.paperMillAdminDiscount(s.econ), 1e-6)

        val lit0 = s.demog.literacy
        IndustryRules.step(s)
        assertEquals(lit0 + 0.015 * 6 * 0.5, s.demog.literacy, 1e-6)

        val revBeforeCoal = IndustryRules.annualFactoryRevenue(s.econ, s.budget, 1800, s.demog.literacy)
        assertTrue(IndustryRules.buildOrUpgradeFactory(s, "anatolia_trabzon", FactoryKey.COAL_MINE).ok)
        assertTrue(IndustryRules.hasCoalPower(s.econ))
        assertTrue(IndustryRules.annualFactoryRevenue(s.econ, s.budget, 1800, s.demog.literacy) > revBeforeCoal * 1.25)

        val pwr0 = WarRules.ourPower(s)
        WarRules.declare(s, "russia", byUs = true)
        val warCost0 = WarRules.costs(s)
        assertTrue(IndustryRules.buildOrUpgradeFactory(s, "anatolia_izmir", FactoryKey.ARMS_FACTORY).ok)
        assertTrue(IndustryRules.buildOrUpgradeFactory(s, "anatolia_izmir", FactoryKey.ARMS_FACTORY).ok)
        assertEquals(0.05, IndustryRules.armsPowerBonus(s.econ), 1e-6)
        assertEquals(0.07, IndustryRules.armsCostDiscount(s.econ), 1e-6)
        assertEquals(pwr0 * 1.05, WarRules.ourPower(s), 0.01)
        assertTrue(WarRules.costs(s) < warCost0)

        assertTrue(IndustryRules.buildOrUpgradeFactory(s, "egypt_cairo", FactoryKey.FOOD_MILL).ok)
        s.econ.grain = 150.0
        val egyptMood0 = s.econ.provinces["egypt"]!!.mood
        IndustryRules.step(s)
        assertTrue(s.econ.grain < 150.0)
        assertEquals(egyptMood0 + 0.35 * 0.5, s.econ.provinces["egypt"]!!.mood, 1e-6)

        // 3) Oil discovery in Mosul >= 1890
        s.round = (1890 - 1800) * 4
        val iraqMood0 = s.econ.provinces["iraq"]!!.mood
        val oilNotes = IndustryRules.step(s)
        assertTrue(s.econ.oilDiscovered.contains("iraq_mosul"))
        assertEquals(iraqMood0 + 4.0, s.econ.provinces["iraq"]!!.mood, 1e-6)
        assertTrue(oilNotes.any { it.contains("الموصل") })
        assertTrue(IndustryRules.canBuildInSubProvince(s.econ, "iraq_mosul", FactoryKey.COAL_MINE, 1890).ok)

        // 4) Economic system cooldown (8 * k = 16 quarters), subsidies & clearing, protectionism & laissez-faire
        s.round = 40
        assertTrue(IndustryRules.setEconomicSystem(s, EconomicSystemKey.INTERVENTIONISM).ok)
        assertEquals(16, IndustryRules.econSystemCooldownRemaining(s))
        assertFalse(IndustryRules.setEconomicSystem(s, EconomicSystemKey.LAISSEZ_FAIRE).ok)

        assertTrue(IndustryRules.buildOrUpgradeFactory(s, "hijaz_jeddah", FactoryKey.FOOD_MILL).ok)
        assertTrue(IndustryRules.toggleSubsidy(s, "hijaz_jeddah", FactoryKey.FOOD_MILL).ok)
        assertEquals(18_000_000.0 * 0.002, IndustryRules.annualSubsidyCost(s.econ, s.budget), 0.01)

        s.round += 16
        assertTrue(IndustryRules.setEconomicSystem(s, EconomicSystemKey.PROTECTIONIST).ok)
        assertFalse(s.econ.subProvinces["hijaz_jeddah"]!!.factories.first().subsidized)
        assertEquals(0.0, IndustryRules.annualSubsidyCost(s.econ, s.budget), 0.01)

        s.round += 16
        assertTrue(IndustryRules.setEconomicSystem(s, EconomicSystemKey.LAISSEZ_FAIRE).ok)
        assertFalse(IndustryRules.buildOrUpgradeFactory(s, "anatolia_izmir", FactoryKey.TRADE_DEPOT).ok)

        // 5) Frontier conquest on decisive victory & AI conquer option & lost province factory clear & JSON backward compat
        val sf = GameState(seed = 55, treasury = 100_000_000.0)
        sf.courtActions = 20
        WarRules.declare(sf, "france", byUs = true)
        val w1 = WarRules.conclude(sf, sf.wars.first(), 65.0)
        assertTrue(w1.contains("الجزائر"))
        assertTrue(sf.econ.frontierRegions["algeria"]!!.controlled)
        assertFalse(sf.econ.frontierRegions["tunisia"]!!.controlled)

        WarRules.declare(sf, "france", byUs = true)
        val w2 = WarRules.conclude(sf, sf.wars.first(), 65.0)
        assertTrue(w2.contains("تونس"))
        assertTrue(sf.econ.frontierRegions["tunisia"]!!.controlled)
        assertTrue(ProvinceRules.suppress(sf, "algeria").ok)

        val optConq = DecisionOption(text = "فتح", result = "تم", conquer = "caucasus")
        val dec = Decision(DecisionCategory.FOREIGN, "عنوان", "القوقاز", "مشهد", "معضلة", listOf(optConq, optConq, optConq))
        sf.round = 300 // 1875 -> Baku oil
        sf.plan = RoundPlan("رأس", "ملخص", "", null, listOf(dec, dec, dec))
        sf.picks = mutableListOf(0, 0, 0)
        GameEngine().resolveRound(sf)
        assertTrue(sf.econ.frontierRegions["caucasus"]!!.controlled)
        assertTrue(sf.econ.oilDiscovered.contains("caucasus_baku"))

        val prompt = buildRoundPrompt(sf)
        assertTrue(prompt.contains("اقتصاد: نظام"))
        assertTrue(prompt.contains("الثغور: مفتوحة"))

        assertTrue(IndustryRules.buildOrUpgradeFactory(sf, "egypt_alexandria", FactoryKey.TRADE_DEPOT).ok)
        ProvinceRules.lose(sf, sf.econ.provinces["egypt"]!!, "cession")
        assertTrue(sf.econ.subProvinces["egypt_alexandria"]!!.factories.isEmpty())

        val restored = GameState.fromJson(sf.toJson())
        assertTrue(restored.econ.frontierRegions["caucasus"]!!.controlled)
        assertTrue(restored.econ.oilDiscovered.contains("caucasus_baku"))

        val legacyJson = sf.toJson().apply {
            getJSONObject("econ").apply {
                remove("subProvinces")
                remove("frontierRegions")
                remove("economicSystem")
                remove("lastEconSystemRound")
                remove("oilDiscovered")
            }
        }
        val restoredLegacy = GameState.fromJson(legacyJson)
        assertEquals(EconomicSystemKey.STATE_CAPITALISM, restoredLegacy.econ.economicSystem)
        assertEquals(25, restoredLegacy.econ.subProvinces.size)
        assertFalse(restoredLegacy.econ.frontierRegions["algeria"]!!.controlled)
    }

    @Test
    fun emergingSocialPowersUnlockDynamicsEffectsAndBackwardCompat() {
        val s = GameState(seed = 77, treasury = 200_000_000.0)
        s.courtActions = 30
        assertEquals(11, powerDefs.size)

        // 1) Dormant at 1800: zero danger/influence, actions blocked, omitted from prompt, stripped by Calibrator
        for (k in listOf("modern_officers", "industrialists", "intelligentsia")) {
            val p = s.powers[k]!!
            assertTrue(p.isDormant)
            assertFalse(p.isActive)
            assertEquals(0.0, p.influence, 0.001)
            assertEquals(0.0, p.danger, 0.001)
            assertFalse(PowerRules.appease(s, k).ok)
            assertFalse(PowerRules.startProject(s, k, "reform").ok)
            assertFalse(PowerRules.dissolve(s, k).ok)
        }

        s.powers["modern_officers"]!!.satisfaction = 5.0
        assertEquals(0.0, s.powers["modern_officers"]!!.danger, 0.001)
        assertFalse(CrisisRules.evaluate(s).any { it.title.contains("ضباط الجيش النظامي") })

        val prompt1800 = buildRoundPrompt(s)
        assertFalse(prompt1800.contains("modern_officers="))
        assertFalse(prompt1800.contains("industrialists="))
        assertFalse(prompt1800.contains("intelligentsia="))

        val optDormant = DecisionOption(
            text = "خيار",
            result = "نتيجة",
            powerMood = mapOf("modern_officers" to 5, "industrialists" to -4, "ulema" to 3),
            dissolve = "intelligentsia",
        )
        val decDormant = Decision(DecisionCategory.INTERNAL, "ع", "م", "س", "ض", listOf(optDormant, optDormant, optDormant))
        val calDormant = Calibrator.calibrate(RoundPlan("ر", "م", "", null, listOf(decDormant, decDormant, decDormant)), s)
        assertEquals(mapOf("ulema" to 3), calDormant.decisions[0].options[0].powerMood)
        assertEquals("", calDormant.decisions[0].options[0].dissolve)

        // 2) Organic unlocks via modern_army, textile factory, paper_mill
        s.completed.add("modern_army")
        assertTrue(IndustryRules.buildOrUpgradeFactory(s, "anatolia_izmir", FactoryKey.TEXTILE).ok)
        assertTrue(IndustryRules.buildOrUpgradeFactory(s, "anatolia_istanbul", FactoryKey.PAPER_MILL).ok)
        val unlockNotes = PowerRules.evaluateUnlocks(s)
        assertEquals(3, unlockNotes.size)
        for (k in listOf("modern_officers", "industrialists", "intelligentsia")) {
            assertFalse(s.powers[k]!!.isDormant)
            assertTrue(s.powers[k]!!.isActive)
            assertTrue(s.powers[k]!!.influence > 0.0)
        }

        val promptUnlocked = buildRoundPrompt(s)
        assertTrue(promptUnlocked.contains("modern_officers=ضباط الجيش النظامي"))
        assertTrue(promptUnlocked.contains("industrialists=الصناعيون والمستثمرون"))
        assertTrue(promptUnlocked.contains("intelligentsia=المتعلمون والمثقفون"))
        assertTrue(promptUnlocked.contains("(ناشئة)"))

        // 3) Industrialists yield +12% / -15% & tax sensitivity
        s.powers["industrialists"]!!.satisfaction = 50.0
        s.powers["intelligentsia"]!!.satisfaction = 50.0
        val baseRev = IndustryRules.annualFactoryRevenue(s.econ, s.budget, 1820, s.demog.literacy, s.powers)
        s.powers["industrialists"]!!.satisfaction = 70.0
        val happyRev = IndustryRules.annualFactoryRevenue(s.econ, s.budget, 1820, s.demog.literacy, s.powers)
        assertEquals(1.12, happyRev / baseRev, 1e-6)
        s.powers["industrialists"]!!.satisfaction = 25.0
        val angryRev = IndustryRules.annualFactoryRevenue(s.econ, s.budget, 1820, s.demog.literacy, s.powers)
        assertEquals(0.85, angryRev / baseRev, 1e-6)

        val indBeforeTax = s.powers["industrialists"]!!.satisfaction
        DeepEconomyRules.onLineChange(s, "land", s.budget.lineValue("land") * 0.1)
        assertTrue(s.powers["industrialists"]!!.satisfaction < indBeforeTax)

        // 4) Intelligentsia +6% yield, literacy +0.04, and political stability pressure when angry
        s.powers["industrialists"]!!.satisfaction = 50.0
        s.powers["intelligentsia"]!!.satisfaction = 70.0
        val intelRev = IndustryRules.annualFactoryRevenue(s.econ, s.budget, 1820, s.demog.literacy, s.powers)
        assertEquals(1.06, intelRev / baseRev, 1e-6)

        val litBefore = s.demog.literacy
        DeepEconomyRules.step(s, 0.0)
        val litGainHigh = s.demog.literacy - litBefore

        val sNoBoost = GameState(seed = 77, treasury = 200_000_000.0)
        sNoBoost.powers["intelligentsia"]!!.unlocked = true
        sNoBoost.powers["intelligentsia"]!!.satisfaction = 50.0
        val litNoBefore = sNoBoost.demog.literacy
        DeepEconomyRules.step(sNoBoost, 0.0)
        val litGainBase = sNoBoost.demog.literacy - litNoBefore
        assertTrue(litGainHigh - litGainBase > 0.015)

        s.powers["intelligentsia"]!!.satisfaction = 20.0
        val polBefore = s.stab["political"]
        val stabNotes = StabilityRules.step(s, 0.5, emergency = false)
        assertTrue(stabNotes.any { it.contains("المتعلمين والمثقفين") })
        assertTrue(s.stab["political"] < polBefore)

        // 5) Modern officers war defeat drop & coup explosion at danger >= 60
        s.powers["modern_officers"]!!.influence = 85.0
        s.powers["modern_officers"]!!.satisfaction = 10.0
        val moSat0 = s.powers["modern_officers"]!!.satisfaction
        WarRules.declare(s, "russia", byUs = false)
        WarRules.conclude(s, s.wars.first(), -30.0)
        assertEquals(Math.max(0.0, moSat0 - 7.0), s.powers["modern_officers"]!!.satisfaction, 0.01)

        assertTrue(s.powers["modern_officers"]!!.danger >= 60.0)
        assertTrue(CrisisRules.evaluate(s).any { it.title.contains("ضباط الجيش النظامي") })
        val armyBeforeCoup = s.army
        val coupMsg = PowerRules.explode(s, s.powers["modern_officers"]!!)
        assertTrue(coupMsg.contains("انقلاب"))
        assertTrue(s.army < armyBeforeCoup)

        // 6) Locked quarterly f/k pace emergent drift
        val s4 = GameState(seed = 5, treasury = 50_000_000.0)
        assertEquals(4, s4.ppy)
        assertEquals(0.5, s4.f, 1e-6)
        assertEquals(2, s4.k)
        s4.completed.add("modern_army")
        s4.army = 80
        PowerRules.evaluateUnlocks(s4)
        val inf4Start = s4.powers["modern_officers"]!!.influence
        PowerRules.step(s4, emergency = false)
        val delta4 = s4.powers["modern_officers"]!!.influence - inf4Start
        assertTrue(delta4 > 0.0)

        // 7) JSON roundtrip & backward-compatible legacy load
        val json = s.toJson()
        val restored = GameState.fromJson(json)
        assertTrue(restored.powers["modern_officers"]!!.unlocked)
        assertTrue(restored.powers["industrialists"]!!.unlocked)

        val arr = json.getJSONArray("powers")
        val filtered = org.json.JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val key = obj.optString("key")
            if (key != "modern_officers" && key != "industrialists" && key != "intelligentsia") {
                filtered.put(obj)
            }
        }
        json.put("powers", filtered)
        val restoredLegacy = GameState.fromJson(json)
        assertTrue(restoredLegacy.powers["modern_officers"]!!.isDormant)
        assertTrue(restoredLegacy.powers["industrialists"]!!.isDormant)
        assertTrue(restoredLegacy.powers["intelligentsia"]!!.isDormant)
    }

    @Test
    fun rebalancedCourtNumbersAndFactorySoftCapWorkInParity() {
        val s = GameState(seed = 12, treasury = 500_000_000.0)
        s.courtActions = 200

        val janBefore = s.powers["janissaries"]!!.satisfaction
        assertTrue(PowerRules.appease(s, "janissaries").ok)
        assertEquals(janBefore + 4.0, s.powers["janissaries"]!!.satisfaction, 0.01)

        val briBefore = s.countries["britain"]!!.relation
        assertTrue(DiplomacyRules.gift(s, "britain").ok)
        assertEquals(briBefore + 3.0, s.countries["britain"]!!.relation, 0.01)

        s.econ.provinces["rumelia"]!!.autonomy = 40.0
        val rumMood0 = s.econ.provinces["rumelia"]!!.mood
        val army0 = s.army
        assertTrue(ProvinceRules.suppress(s, "rumelia").ok)
        assertEquals(32.0, s.econ.provinces["rumelia"]!!.autonomy, 0.01)
        assertEquals(rumMood0 - 6.0, s.econ.provinces["rumelia"]!!.mood, 0.01)
        assertEquals(army0 - 1, s.army)

        s.econ.provinces["syria"]!!.autonomy = 30.0
        val syrMood0 = s.econ.provinces["syria"]!!.mood
        assertTrue(ProvinceRules.privileges(s, "syria").ok)
        assertEquals(26.0, s.econ.provinces["syria"]!!.autonomy, 0.01)
        assertEquals(syrMood0 + 5.0, s.econ.provinces["syria"]!!.mood, 0.01)

        val hubs = subProvinceDefs
            .filter { IndustryRules.isSubProvinceControlled(s.econ, it.key) }
            .map { it.key }
        var uncappedSum = 0.0
        for (key in hubs) {
            for (fType in listOf(FactoryKey.TEXTILE, FactoryKey.TRADE_DEPOT, FactoryKey.FOOD_MILL)) {
                repeat(3) { IndustryRules.buildOrUpgradeFactory(s, key, fType) }
            }
        }
        for (key in hubs) {
            for (fac in s.econ.subProvinces[key]!!.factories) {
                uncappedSum += IndustryRules.annualFactoryYield(s.econ, s.budget, 1920, 40.0, key, fac, s.powers)
            }
        }
        val capped = IndustryRules.annualFactoryRevenue(s.econ, s.budget, 1920, 40.0, s.powers)
        assertTrue(capped.isFinite() && capped > 0.0)
        assertTrue(capped < uncappedSum * 0.75)

        s.powers["industrialists"]!!.unlocked = true
        s.powers["industrialists"]!!.satisfaction = 50.0
        val basePow = IndustryRules.annualFactoryRevenue(s.econ, s.budget, 1920, 40.0, s.powers)
        s.powers["industrialists"]!!.satisfaction = 75.0
        val happyPow = IndustryRules.annualFactoryRevenue(s.econ, s.budget, 1920, 40.0, s.powers)
        assertEquals(1.12, happyPow / basePow, 1e-6)
    }

    @Test
    fun populationAdminCivilScalingAndPriceIndexSaveParityWorkInParity() {
        val s = GameState(seed = 21)
        val admin0 = s.budget.lineValue("admin")
        val civil0 = s.budget.lineValue("civil")
        val army0 = s.budget.lineValue("army")
        val palace0 = s.budget.lineValue("palace")
        val pop0 = s.demog.population

        DeepEconomyRules.step(s, 0.0)
        val popGrowth = (s.demog.population - pop0) / pop0
        assertTrue(popGrowth > 0.0)
        assertEquals(1.0 + popGrowth * DeepEconomyRules.ADMIN_POP_ELASTICITY, s.budget.lineValue("admin") / admin0, 1e-9)
        assertEquals(1.0 + popGrowth * DeepEconomyRules.CIVIL_POP_ELASTICITY, s.budget.lineValue("civil") / civil0, 1e-9)
        assertEquals(army0, s.budget.lineValue("army"), 1e-9)
        assertEquals(palace0, s.budget.lineValue("palace"), 1e-9)

        val famine = GameState(seed = 21)
        famine.econ.grain = 300.0
        DeepEconomyRules.step(famine, 0.0)
        assertTrue(famine.demog.population < pop0)
        assertEquals(admin0, famine.budget.lineValue("admin"), 1e-9)
        assertEquals(civil0, famine.budget.lineValue("civil"), 1e-9)

        val sNom = GameState(seed = 33)
        val ecoBefore = ForeignRules.ottomanEconomy(sNom)
        repeat(400) {
            sNom.econ.inflation = 8.0
            DeepEconomyRules.applyNominalGrowth(sNom)
        }
        assertTrue(sNom.econ.priceIndex > 2000.0)
        assertEquals(ecoBefore, ForeignRules.ottomanEconomy(sNom), 1e-6)

        val restored = GameState.fromJson(sNom.toJson())
        assertEquals(sNom.econ.priceIndex, restored.econ.priceIndex, 1e-6)
        assertEquals(ecoBefore, ForeignRules.ottomanEconomy(restored), 1e-6)

        val sInf = GameState(seed = 33)
        sInf.econ.grain = 200.0
        repeat(60) { DeepEconomyRules.step(sInf, -1_000_000.0) }
        assertEquals(DeepEconomyRules.MAX_INFLATION, sInf.econ.inflation, 1e-9)
        sInf.econ.grain = 100.0
        repeat(60) { DeepEconomyRules.step(sInf, 1_000_000.0) }
        assertEquals(DeepEconomyRules.TARGET_INFLATION, sInf.econ.inflation, 1e-6)
    }

    @Test
    fun scientificResearchAndTechAcquisitionWorkInParity() {
        // 1. 15 techs across 4 categories & 3 realism ratings (1800-1970)
        assertEquals(15, techCatalog.size)
        assertEquals(4, techCatalog.map { it.category }.toSet().size)
        assertEquals(3, techCatalog.map { it.realismRating }.toSet().size)

        // 2. Points breakdown & +15% free press bonus
        val sPts = GameState(seed = 5)
        val basePts = ResearchRules.pointsBreakdown(sPts)
        sPts.pressPolicy = "free"
        val freePts = ResearchRules.pointsBreakdown(sPts)
        assertEquals(1.15, freePts.totalPerPeriod / basePts.totalPerPeriod, 1e-6)

        sPts.pressPolicy = "censored"
        sPts.education = 45
        sPts.demog.literacy = 15.0
        sPts.demog.schools = 12000.0
        sPts.econ.subProvinces["anatolia_istanbul"]!!.factories.add(
            com.ottoman.empire1800.models.FactoryInstance(type = "paper_mill", level = 2, subsidized = false),
        )
        sPts.completed.add("universities")
        val boostedPts = ResearchRules.pointsBreakdown(sPts)
        assertTrue(boostedPts.fromPaper > 0.0)
        assertTrue(boostedPts.fromUniv > 0.0)
        assertTrue(boostedPts.totalPerPeriod > basePts.totalPerPeriod * 3.0)

        // 3. Domestic research completion -> unlocks tech & records decree
        val sDom = GameState(seed = 7)
        sDom.courtActions = 10
        assertFalse(ResearchRules.canResearch(sDom, "steam_engine").ok)
        assertTrue(ResearchRules.startDomestic(sDom, "translation_bureau").ok)
        assertFalse(ResearchRules.startDomestic(sDom, "nizam_artillery").ok)
        var stepNotes: List<String> = emptyList()
        for (i in 0 until 20) {
            if (sDom.activeResearch == null) break
            stepNotes = ResearchRules.step(sDom)
        }
        assertNull(sDom.activeResearch)
        assertTrue(sDom.unlockedTechs.contains("translation_bureau"))
        assertTrue(stepNotes.any { it.contains("الترجمة والعلوم الحديثة") })
        assertTrue(sDom.decrees.any { it.contains("الترجمة والعلوم الحديثة") })

        // 4. Foreign mission speed 1.6x, quarterly fee, stall & auto-cancel (relation < 40 & war)
        val sMis = GameState(seed = 9, treasury = 50_000_000.0)
        sMis.courtActions = 10
        sMis.countries["britain"]!!.embassy = false
        assertFalse(ResearchRules.startForeignMission(sMis, "translation_bureau", "britain").ok)
        sMis.countries["britain"]!!.embassy = true
        sMis.countries["britain"]!!.relation = 60.0
        assertTrue(ResearchRules.startForeignMission(sMis, "translation_bureau", "britain").ok)
        val treas0 = sMis.treasury
        val fee = ResearchRules.missionQuarterlyFee(sMis)
        ResearchRules.step(sMis)
        assertEquals(treas0 - fee, sMis.treasury, 1e-4)
        assertEquals(ResearchRules.pointsPerPeriod(sMis) * 1.6, sMis.activeResearch!!.progress, 1e-6)

        sMis.treasury = 0.0
        val prog0 = sMis.activeResearch!!.progress
        val stallNotes = ResearchRules.step(sMis)
        assertEquals(prog0, sMis.activeResearch!!.progress, 1e-6)
        assertTrue(stallNotes.any { it.contains("تعثر") })

        sMis.treasury = 50_000_000.0
        sMis.countries["britain"]!!.relation = 35.0
        val cancelNotes = ResearchRules.step(sMis)
        assertNull(sMis.activeResearch)
        assertTrue(cancelNotes.any { it.contains("أُلغيت البعثة") })

        sMis.countries["britain"]!!.relation = 70.0
        assertTrue(ResearchRules.startForeignMission(sMis, "translation_bureau", "britain").ok)
        sMis.wars.add(com.ottoman.empire1800.models.War(enemy = "britain", startRound = sMis.round, byUs = false))
        val warCancelNotes = ResearchRules.step(sMis)
        assertNull(sMis.activeResearch)
        assertTrue(warCancelNotes.any { it.contains("اندلاع الحرب") })

        // 5. Import patent pre-1880 (-3 ulema) vs 1880+ (no ulema drop)
        val sImp = GameState(seed = 11, treasury = 100_000_000.0)
        sImp.courtActions = 5
        val ulema0 = sImp.powers["ulema"]!!.satisfaction
        assertTrue(ResearchRules.importPatent(sImp, "translation_bureau").ok)
        assertEquals(ulema0 - 3.0, sImp.powers["ulema"]!!.satisfaction, 1e-6)

        val sImpLate = GameState(seed = 11, round = 80 * 4, education = 40, treasury = 100_000_000.0)
        sImpLate.courtActions = 5
        sImpLate.demog.literacy = 20.0
        val ulemaLate0 = sImpLate.powers["ulema"]!!.satisfaction
        assertTrue(ResearchRules.importPatent(sImpLate, "scientific_agronomy").ok)
        assertEquals(ulemaLate0, sImpLate.powers["ulema"]!!.satisfaction, 1e-6)

        // 6. All 7 gameplay bonuses & RoundPrompt compact summary
        val sBon = GameState(seed = 15, round = 160 * 4, education = 80)
        sBon.demog.literacy = 45.0
        sBon.econ.subProvinces["anatolia_istanbul"]!!.factories.add(
            com.ottoman.empire1800.models.FactoryInstance(type = "textile", level = 2, subsidized = false),
        )
        val baseRev = IndustryRules.annualFactoryRevenue(sBon.econ, sBon.budget, 1960, 45.0, sBon.powers, emptyList())
        val baseWar = WarRules.ourPower(sBon)
        val baseSpend = EconomyRules.halfYearSpending(sBon.budget, 0.5, sBon.grandVizier, sBon.econ, "reformer", emptyList())

        sBon.unlockedTechs.addAll(
            listOf(
                "translation_bureau",
                "nizam_artillery",
                "quarantine_medicine",
                "steam_engine",
                "modern_accounting",
                "scientific_agronomy",
            ),
        )
        val techRev = IndustryRules.annualFactoryRevenue(sBon.econ, sBon.budget, 1960, 45.0, sBon.powers, sBon.unlockedTechs)
        val techWar = WarRules.ourPower(sBon)
        val techSpend = EconomyRules.halfYearSpending(sBon.budget, 0.5, sBon.grandVizier, sBon.econ, "reformer", sBon.unlockedTechs)
        assertEquals(1.0 + ResearchRules.factoryOutputBonus(sBon.unlockedTechs), techRev / baseRev, 1e-6)
        assertEquals(1.0 + ResearchRules.warPowerBonus(sBon.unlockedTechs), techWar / baseWar, 1e-6)
        assertTrue(techSpend < baseSpend)
        assertTrue(ResearchRules.plagueReduction(sBon.unlockedTechs) > 0.0)
        assertTrue(ResearchRules.droughtReduction(sBon.unlockedTechs) > 0.0)
        assertTrue(ResearchRules.corruptionDriftBonus(sBon.unlockedTechs) > 0.0)
        assertTrue(ResearchRules.literacySpeedBonus(sBon.unlockedTechs) > 0.0)

        val promptText = buildRoundPrompt(sBon)
        assertTrue(promptText.contains("البحث العلمي:"))
        assertTrue(promptText.contains(techDef("scientific_agronomy")!!.name))

        // 7. Save/load roundtrip & ppy=2->4 migration + legacy save fallback
        sBon.activeResearch = ActiveResearch(
            key = "oil_chemistry",
            method = "foreign_mission",
            partnerKey = "germany",
            progress = 14.5,
            target = 52.0,
            startRound = 600,
        )
        val rawJson = sBon.toJson()
        val restored = GameState.fromJson(rawJson)
        assertEquals(sBon.unlockedTechs, restored.unlockedTechs)
        assertEquals("oil_chemistry", restored.activeResearch?.key)
        assertEquals("germany", restored.activeResearch?.partnerKey)
        assertEquals(600, restored.activeResearch?.startRound)

        rawJson.put("ppy", 2)
        rawJson.getJSONObject("activeResearch").put("startRound", 12)
        val migrated = GameState.fromJson(rawJson)
        assertEquals(4, migrated.ppy)
        assertEquals(24, migrated.activeResearch?.startRound)

        rawJson.remove("activeResearch")
        rawJson.remove("unlockedTechs")
        val legacy = GameState.fromJson(rawJson)
        assertNull(legacy.activeResearch)
        assertTrue(legacy.unlockedTechs.isEmpty())
    }
}
