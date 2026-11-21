package com.ottoman.empire1800

import com.ottoman.empire1800.engine.Calibrator
import com.ottoman.empire1800.models.Decision
import com.ottoman.empire1800.models.DecisionCategory
import com.ottoman.empire1800.models.DecisionOption
import com.ottoman.empire1800.models.Effects
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.LineChange
import com.ottoman.empire1800.models.RoundPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibratorTest {
    private val revenue = 18_000_000.0

    private fun opt(
        cost: Double = 0.0,
        loan: Double = 0.0,
        now: Effects = Effects(),
        later: Effects = Effects(),
        delay: Int = 0,
        lines: List<LineChange> = emptyList(),
    ) = DecisionOption(
        text = "خيار",
        result = "نتيجة",
        cost = cost,
        loan = loan,
        now = now,
        later = later,
        delay = delay,
        lineChanges = lines,
    )

    @Test
    fun reasonableNumbersUnchangedWithoutCalibratedFlag() {
        val o = Calibrator.calibrateOption(
            opt(
                cost = 2_000_000.0,
                loan = 3_000_000.0,
                now = Effects(stability = 1, army = -1),
                later = Effects(education = 1),
                delay = 4,
                lines = listOf(LineChange("civil", 400_000.0)),
            ),
            revenue,
        )
        assertEquals(2_000_000.0, o.cost, 1.0)
        assertEquals(3_000_000.0, o.loan, 1.0)
        assertEquals(1, o.now.stability)
        assertEquals(4, o.delay)
        assertFalse(o.calibrated)
    }

    @Test
    fun costAndLoanCappedToRevenueShare() {
        val o = Calibrator.calibrateOption(opt(cost = 50_000_000.0, loan = 90_000_000.0), revenue)
        assertEquals(revenue * Calibrator.MAX_COST_SHARE, o.cost, 1.0)
        assertEquals(revenue * Calibrator.MAX_LOAN_SHARE, o.loan, 1.0)
        assertTrue(o.calibrated)
    }

    @Test
    fun effectsAndTotalPositiveGainAreClamped() {
        val neg = Calibrator.calibrateOption(
            opt(now = Effects(legitimacy = -30), later = Effects(stability = -40)),
            revenue,
        )
        assertEquals(-Calibrator.MAX_NOW, neg.now.legitimacy)
        assertEquals(-Calibrator.MAX_LATER, neg.later.stability)
        assertTrue(neg.calibrated)

        val pos = Calibrator.calibrateOption(
            opt(
                now = Effects(legitimacy = 8, stability = 8, army = 8),
                later = Effects(foreign = 12, education = 12),
            ),
            revenue,
        )
        val gain = pos.now.legitimacy + pos.now.stability + pos.now.army + pos.later.foreign + pos.later.education
        assertTrue(gain <= Calibrator.MAX_TOTAL_GAIN)
        assertTrue(pos.calibrated)
    }

    @Test
    fun lineChangesFilteredMergedAndDelayed() {
        val o = Calibrator.calibrateOption(
            opt(lines = listOf(LineChange("nonsense", 100_000.0), LineChange("army", 300_000.0), LineChange("army", 200_000.0))),
            revenue,
        )
        assertEquals(1, o.lineChanges.size)
        assertEquals("army", o.lineChanges.first().line)
        assertEquals(500_000.0, o.lineChanges.first().delta, 1.0)
        assertTrue(o.calibrated)

        val revRaise = Calibrator.calibrateOption(opt(delay = 0, lines = listOf(LineChange("customs", 500_000.0))), revenue)
        assertTrue(revRaise.delay >= 2)
        assertTrue(revRaise.calibrated)
    }

    @Test
    fun longTextsTruncated() {
        val long = "ك".repeat(5000)
        val plan = Calibrator.calibrate(
            RoundPlan(
                headline = long,
                briefing = long,
                chronicle = long,
                decisions = DecisionCategory.entries.map { c ->
                    Decision(
                        category = c,
                        title = "عنوان",
                        scene = long,
                        dilemma = long,
                        options = List(3) { opt() },
                    )
                },
            ),
            GameState(),
        )
        assertTrue(plan.briefing.length <= Calibrator.MAX_BRIEFING)
        assertTrue(plan.chronicle.length <= Calibrator.MAX_CHRONICLE)
        assertTrue(plan.decisions.first().scene.length <= Calibrator.MAX_SCENE)
    }

    @Test
    fun quarterlyCalibratorScalesRelationsPowerMoodAndRevenueDelay() {
        val raw = DecisionOption(
            text = "خيار ربع سنوي",
            result = "نتيجة",
            cost = 50_000_000.0,
            loan = 50_000_000.0,
            now = Effects(legitimacy = 8),
            later = Effects(army = 12),
            delay = 0,
            lineChanges = listOf(LineChange("customs", 2_000_000.0)),
            relations = mapOf("britain" to 8, "france" to -8),
            powerMood = mapOf("janissaries" to 10, "ulema" to -10),
        )
        val cal = Calibrator.calibrateOption(raw, revenue, f = 0.5)
        assertEquals(revenue * Calibrator.MAX_COST_SHARE, cal.cost, 1.0)
        assertEquals(revenue * Calibrator.MAX_LOAN_SHARE, cal.loan, 1.0)
        assertEquals(revenue * Calibrator.MAX_LINE_SHARE, cal.lineChanges.first().delta, 1.0)
        assertEquals(2, cal.delay)
        assertTrue(cal.now.legitimacy <= Calibrator.MAX_NOW)
        assertTrue(cal.later.army <= Calibrator.MAX_LATER)
        assertTrue(cal.now.legitimacy + cal.later.army <= Calibrator.MAX_TOTAL_GAIN)
        assertEquals(3, cal.relations["britain"])
        assertEquals(-3, cal.relations["france"])
        assertEquals(4, cal.powerMood["janissaries"])
        assertEquals(-4, cal.powerMood["ulema"])
        assertTrue(cal.calibrated)
    }

    @Test
    fun noFreeLunchAndPromptBoundsEnforced() {
        val freeCal = Calibrator.calibrateOption(
            opt(
                cost = 0.0,
                now = Effects(legitimacy = 2, stability = 1),
                later = Effects(army = 1),
                delay = 2,
            ),
            revenue,
            f = 0.5,
        )
        assertTrue(freeCal.calibrated)
        assertEquals(1, freeCal.now.legitimacy + freeCal.now.stability + freeCal.later.army)
        assertEquals(1, freeCal.now.legitimacy)
        assertEquals(0, freeCal.now.stability)
        assertEquals(0, freeCal.later.army)

        val tradedCal = Calibrator.calibrateOption(
            DecisionOption(
                text = "مقايضة",
                result = "نتيجة",
                now = Effects(legitimacy = 1, army = 2),
                powerMood = mapOf("janissaries" to -2),
            ),
            revenue,
            f = 0.5,
        )
        assertFalse(tradedCal.calibrated)
        assertEquals(3, tradedCal.now.legitimacy + tradedCal.now.army)

        val delayedCal = Calibrator.calibrateOption(
            opt(cost = 500_000.0, now = Effects(legitimacy = 2), later = Effects(army = 3), delay = 30),
            revenue,
            f = 0.5,
        )
        assertTrue(delayedCal.calibrated)
        assertEquals(16, delayedCal.delay)
        assertEquals(3, delayedCal.now.legitimacy + delayedCal.later.army)

        val prompt = com.ottoman.empire1800.services.buildRoundPrompt(GameState())
        assertTrue(prompt.contains("now بين ±2"))
        assertTrue(prompt.contains("later بين ±3"))
        assertTrue(prompt.contains("مجموع المكاسب الموجبة في الخيار ≤ 3"))
        assertTrue(prompt.contains("تُمنع الخيارات المجانية"))
    }
}
