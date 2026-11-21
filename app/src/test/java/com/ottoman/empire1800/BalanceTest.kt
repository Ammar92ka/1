package com.ottoman.empire1800

import com.ottoman.empire1800.engine.GameEngine
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.services.GrandVizierRules
import com.ottoman.empire1800.services.PowerRules
import com.ottoman.empire1800.services.SultanRules
import com.ottoman.empire1800.support.playBot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BalanceTest {

    @Test
    fun sameSeedProducesIdenticalCampaignOutcomes() {
        val a = GameState(seed = 77)
        val b = GameState(seed = 77)
        val runA = playBot(a, prudent = true, maxRounds = 40)
        val runB = playBot(b, prudent = true, maxRounds = 40)
        assertEquals(runA.rounds, runB.rounds)
        assertEquals(a.treasury, b.treasury, 0.01)
        assertEquals(a.debt, b.debt, 0.01)
        assertEquals(a.legitimacy, b.legitimacy)
        assertEquals(a.stability, b.stability)
    }

    @Test
    fun noopBotSurvivesEarlyYearsInQuarterlyPace() {
        val quarter = playBot(GameState(seed = 5), prudent = false, maxRounds = 60)
        assertTrue("Expected ppy=4 no-op survival >=40, got ${quarter.rounds}", quarter.rounds >= 40)
    }

    @Test
    fun prudentBotSurvivesLongTermAcrossMostSeeds() {
        var survived100 = 0
        for (seed in 1..10) {
            val res = playBot(GameState(seed = seed), prudent = true, maxRounds = 100)
            if (res.rounds >= 100 || res.ending?.isVictory == true) {
                survived100++
            }
        }
        assertTrue("Expected >=8/10 seeds to survive 100 rounds, got $survived100", survived100 >= 8)
    }

    @Test
    fun full684RoundCampaignStaysBoundedWithoutAllStatSaturation() {
        val engine = GameEngine()
        var victories = 0
        var nonSaturated = 0
        for (seed in listOf(1, 7, 42)) {
            val s = GameState(seed = seed)
            var ended = false
            for (r in 0 until GameEngine.totalRounds) {
                if (s.pendingSuccession) SultanRules.chooseHeir(s, r % 2)
                if (s.courtActions > 0 && s.grandVizier == "traditional" && s.treasury > s.budget.totalRevenue * 0.2) {
                    GrandVizierRules.appoint(s, "reformer")
                }
                val res = playBot(s, prudent = true, maxRounds = 1) { st ->
                    assertTrue(st.treasury.isFinite() && st.treasury >= 0.0)
                    assertTrue(st.debt.isFinite() && st.debt >= 0.0)
                    assertTrue(st.econ.inflation.isFinite() && st.econ.inflation <= 12.0)
                    assertTrue(st.demog.population.isFinite() && st.demog.population in 1.0..500.0)
                    assertTrue(st.demog.literacy.isFinite() && st.demog.literacy in 0.0..80.0)
                }
                if (res.ending != null) {
                    if (res.ending.isVictory) victories++
                    ended = true
                    break
                }
            }
            if (!ended) {
                val fin = engine.evaluate(s)
                if (fin?.isVictory == true) victories++
            }
            val pinned100 = s.legitimacy == 100 && s.stability == 100 && s.army == 100 && s.foreign == 100 && s.education == 100
            if (!pinned100) nonSaturated++
            for (c in s.countries.values) {
                assertTrue(c.economy.isFinite() && c.economy in 1.0..25_000.0)
            }
            // Ensure active powers stay bounded
            for (p in s.powers.values.filter { it.isActive }) {
                assertTrue(PowerRules.dissolveSuccessChance(s, p) in 0.0..1.0)
            }
        }
        assertTrue("Expected at least 2/3 full campaigns to succeed, got $victories", victories >= 2)
        assertEquals(3, nonSaturated)
    }
}
