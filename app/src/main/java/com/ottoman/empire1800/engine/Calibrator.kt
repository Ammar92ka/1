package com.ottoman.empire1800.engine

import com.ottoman.empire1800.models.Decision
import com.ottoman.empire1800.models.DecisionOption
import com.ottoman.empire1800.models.Effects
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.LineChange
import com.ottoman.empire1800.models.RoundPlan
import com.ottoman.empire1800.models.isBudgetLine
import com.ottoman.empire1800.models.isRevenueLine
import com.ottoman.empire1800.services.DiplomacyRules
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToDouble
import kotlin.math.roundToInt

/** معايرة ما يكتبه الذكاء الاصطناعي مع الواقع الاقتصادي للحملة. */
object Calibrator {
    const val MAX_COST_SHARE: Double = 0.20
    const val MAX_LOAN_SHARE: Double = 0.35
    const val MAX_LINE_SHARE: Double = 0.04

    const val MAX_NOW: Int = 2
    const val MAX_LATER: Int = 3
    const val MAX_DELAY: Int = 16
    const val MAX_LINE_CHANGES: Int = 3

    const val MAX_RELATION: Int = 3
    const val MAX_POWER_MOOD: Int = 4
    const val MAX_MAP_KEYS: Int = 3

    const val MAX_TOTAL_GAIN: Int = 3

    const val MAX_TITLE: Int = 90
    const val MAX_SCENE: Int = 800
    const val MAX_DILEMMA: Int = 500
    const val MAX_OPTION_TEXT: Int = 420
    const val MAX_RESULT: Int = 500
    const val MAX_REACTION: Int = 420
    const val MAX_NOTE: Int = 300
    const val MAX_BRIEFING: Int = 1000
    const val MAX_CHRONICLE: Int = 700

    fun capNow(@Suppress("UNUSED_PARAMETER") f: Double = 0.5): Int = MAX_NOW
    fun capLater(@Suppress("UNUSED_PARAMETER") f: Double = 0.5): Int = MAX_LATER
    fun capGain(@Suppress("UNUSED_PARAMETER") f: Double = 0.5): Int = MAX_TOTAL_GAIN
    fun capRelation(@Suppress("UNUSED_PARAMETER") f: Double = 0.5): Int = MAX_RELATION
    fun capPowerMood(@Suppress("UNUSED_PARAMETER") f: Double = 0.5): Int = MAX_POWER_MOOD
    fun capDelay(@Suppress("UNUSED_PARAMETER") f: Double = 0.5): Int = MAX_DELAY

    fun calibrate(raw: RoundPlan, s: GameState): RoundPlan {
        val revenue = s.budget.totalRevenue
        val credit = DiplomacyRules.creditFactor(s)
        return RoundPlan(
            headline = cut(raw.headline, MAX_TITLE),
            briefing = cut(raw.briefing, MAX_BRIEFING),
            chronicle = cut(raw.chronicle, MAX_CHRONICLE),
            world = raw.world,
            decisions = raw.decisions.map { d ->
                Decision(
                    category = d.category,
                    title = cut(d.title, MAX_TITLE),
                    place = cut(d.place, 70),
                    scene = cut(d.scene, MAX_SCENE),
                    dilemma = cut(d.dilemma, MAX_DILEMMA),
                    options = d.options.map { o ->
                        val cal = calibrateOption(o, revenue, creditFactor = credit, f = s.f)
                        val activeMood = cal.powerMood.filterKeys { k -> s.powers[k]?.isActive == true }
                        val activeDissolve = if (cal.dissolve.isNotEmpty() && s.powers[cal.dissolve]?.isActive != true) {
                            ""
                        } else {
                            cal.dissolve
                        }
                        val activeConquer = if (cal.conquer.isNotEmpty() && s.econ.frontierRegions[cal.conquer]?.controlled == true) {
                            ""
                        } else {
                            cal.conquer
                        }
                        if (activeMood.size != cal.powerMood.size || activeDissolve != cal.dissolve || activeConquer != cal.conquer) {
                            cal.copy(powerMood = activeMood, dissolve = activeDissolve, conquer = activeConquer)
                        } else {
                            cal
                        }
                    },
                )
            },
        )
    }

    private data class GainSlot(
        val isNow: Boolean,
        val idx: Int,
        val raw: Int,
        val floored: Int,
        val rem: Double,
    )

    fun calibrateOption(
        o: DecisionOption,
        annualRevenue: Double,
        creditFactor: Double = 1.0,
        f: Double = 0.5,
    ): DecisionOption {
        var changed = false
        val nowCap = capNow(f)
        val laterCap = capLater(f)
        val gainCap = capGain(f)
        val minRevDelay = max(2, (1.0 / max(0.25, f)).roundToInt())

        fun clampMoney(v: Double, maxVal: Double): Double {
            val c = if (v.isFinite()) v.coerceIn(0.0, maxVal) else 0.0
            if (abs(c - v) > maxVal * 0.005) changed = true
            return c.roundToDouble()
        }

        val cost = clampMoney(o.cost, annualRevenue * MAX_COST_SHARE)
        val loan = clampMoney(o.loan, annualRevenue * MAX_LOAN_SHARE * creditFactor)

        var now = o.now.clampTo(nowCap)
        var later = o.later.clampTo(laterCap)
        if (differs(now, o.now) || differs(later, o.later)) changed = true

        fun effValues(e: Effects): IntArray =
            intArrayOf(e.legitimacy, e.stability, e.army, e.foreign, e.education)

        fun buildEffects(arr: IntArray): Effects = Effects(
            legitimacy = arr[0],
            stability = arr[1],
            army = arr[2],
            foreign = arr[3],
            education = arr[4],
        )

        val gain = positiveSum(now) + positiveSum(later)
        if (gain > gainCap) {
            val nowArr = effValues(now)
            val laterArr = effValues(later)
            val slots = mutableListOf<GainSlot>()
            for (i in 0 until 5) {
                if (nowArr[i] > 0) {
                    val scaled = (nowArr[i] * gainCap).toDouble() / gain
                    val fl = floor(scaled).toInt()
                    slots.add(GainSlot(true, i, nowArr[i], fl, scaled - fl))
                }
            }
            for (i in 0 until 5) {
                if (laterArr[i] > 0) {
                    val scaled = (laterArr[i] * gainCap).toDouble() / gain
                    val fl = floor(scaled).toInt()
                    slots.add(GainSlot(false, i, laterArr[i], fl, scaled - fl))
                }
            }
            var remBudget = gainCap - slots.sumOf { it.floored }
            val sorted = slots.sortedWith(compareByDescending<GainSlot> { it.rem }.thenByDescending { it.raw })
            for (sl in sorted) {
                val nextVal = sl.floored + if (remBudget > 0) {
                    remBudget -= 1
                    1
                } else {
                    0
                }
                if (sl.isNow) nowArr[sl.idx] = nextVal else laterArr[sl.idx] = nextVal
            }
            now = buildEffects(nowArr)
            later = buildEffects(laterArr)
            changed = true
        }

        val merged = linkedMapOf<String, Double>()
        for (c in o.lineChanges) {
            if (!isBudgetLine(c.line)) {
                changed = true
                continue
            }
            merged[c.line] = (merged[c.line] ?: 0.0) + c.delta
        }
        val lines = mutableListOf<LineChange>()
        val limit = annualRevenue * MAX_LINE_SHARE
        for ((key, rawVal) in merged) {
            if (lines.size >= MAX_LINE_CHANGES) {
                changed = true
                break
            }
            val v = if (rawVal.isFinite()) rawVal.coerceIn(-limit, limit) else 0.0
            if (abs(v - rawVal) > limit * 0.005) changed = true
            if (abs(v) >= 1.0) lines.add(LineChange(key, v.roundToDouble()))
        }

        var delay = o.delay.coerceIn(0, MAX_DELAY)
        if (delay != o.delay) changed = true
        val raisesRevenue = lines.any { isRevenueLine(it.line) && it.delta > 0.0 }
        if (raisesRevenue && delay < minRevDelay) {
            delay = minRevDelay
            changed = true
        }

        fun limitMap(raw: Map<String, Int>, maxVal: Int): Map<String, Int> {
            val out = linkedMapOf<String, Int>()
            for ((k, rawV) in raw) {
                if (out.size >= MAX_MAP_KEYS) {
                    changed = true
                    break
                }
                val v = rawV.coerceIn(-maxVal, maxVal)
                if (v != rawV) changed = true
                if (v != 0) out[k] = v
            }
            return out
        }

        val relations = limitMap(o.relations, capRelation(f))
        val powerMood = limitMap(o.powerMood, capPowerMood(f))

        // قاعدة "لا غداء مجانيا": خيار بمكسب >= 2 بدون كلفة ولا تضحية سلبية يُخفَّض إلى +1 فقط
        val nowArr = effValues(now)
        val laterArr = effValues(later)
        val hasNegEff = nowArr.any { it < 0 } || laterArr.any { it < 0 }
        val hasNegRel = relations.values.any { it < 0 }
        val hasNegPow = powerMood.values.any { it < 0 }
        val hasBudgetTradeOff = lines.any {
            (com.ottoman.empire1800.models.isSpendingLine(it.line) && it.delta > 0.0) ||
                (isRevenueLine(it.line) && it.delta < 0.0)
        }
        val hasWarTradeOff = o.war.trim().isNotEmpty()
        val finalGain = positiveSum(now) + positiveSum(later)
        if (finalGain >= 2 && cost <= 0.0 && !hasNegEff && !hasNegRel && !hasNegPow && !hasBudgetTradeOff && !hasWarTradeOff) {
            var bestIsNow = true
            var bestIdx = 0
            var bestVal = -1
            for (i in 0 until 5) {
                if (nowArr[i] > bestVal) {
                    bestVal = nowArr[i]
                    bestIsNow = true
                    bestIdx = i
                }
                if (nowArr[i] > 0) nowArr[i] = 0
            }
            for (i in 0 until 5) {
                if (laterArr[i] > bestVal) {
                    bestVal = laterArr[i]
                    bestIsNow = false
                    bestIdx = i
                }
                if (laterArr[i] > 0) laterArr[i] = 0
            }
            if (bestVal > 0) {
                if (bestIsNow) nowArr[bestIdx] = 1 else laterArr[bestIdx] = 1
            }
            now = buildEffects(nowArr)
            later = buildEffects(laterArr)
            changed = true
        }

        return DecisionOption(
            text = cut(o.text, MAX_OPTION_TEXT),
            result = cut(o.result, MAX_RESULT),
            reaction = cut(o.reaction, MAX_REACTION),
            note = cut(o.note, MAX_NOTE),
            cost = cost,
            loan = loan,
            now = now,
            later = later,
            delay = delay,
            lineChanges = lines,
            relations = relations,
            powerMood = powerMood,
            dissolve = o.dissolve,
            war = o.war,
            peace = o.peace,
            project = o.project,
            conquer = o.conquer,
            calibrated = changed,
        )
    }

    private fun differs(a: Effects, b: Effects): Boolean =
        a.legitimacy != b.legitimacy ||
            a.stability != b.stability ||
            a.army != b.army ||
            a.foreign != b.foreign ||
            a.education != b.education

    private fun positiveSum(e: Effects): Int =
        listOf(e.legitimacy, e.stability, e.army, e.foreign, e.education)
            .filter { it > 0 }
            .sum()

    private fun cut(s: String, maxLen: Int): String {
        val t = s.trim()
        if (t.length <= maxLen) return t
        return "${t.substring(0, maxLen - 1).trimEnd()}…"
    }
}
