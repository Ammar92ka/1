package com.ottoman.empire1800.services

import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.clampD

/** الاقتصاد العميق: مزاج الولايات، سعر الحبوب، التضخم، الفساد، السكان والمدارس. */
object DeepEconomyRules {
    const val SCHOOL_COST: Double = 1600.0
    const val SCHOOL_SHARE: Double = 0.30
    const val MAX_INFLATION: Double = 8.5
    const val TARGET_INFLATION: Double = 1.6
    const val ADMIN_POP_ELASTICITY: Double = 0.65
    const val CIVIL_POP_ELASTICITY: Double = 0.55

    fun onLineChange(s: GameState, line: String, delta: Double) {
        val rev = s.budget.totalRevenue
        if (rev <= 0.0 || delta == 0.0 || !delta.isFinite()) return
        val share = delta / rev
        if (line == "land" || line == "provinces" || line == "other") {
            val d = if (share > 0.0) -share * 60.0 else -share * 30.0
            for (p in s.econ.provinces.values) {
                if (!p.isLost) p.mood = clampD(p.mood + d, 0.0, 100.0)
            }
            if ((line == "land" || line == "other") && share > 0.0) {
                s.powers["industrialists"]?.let { ind ->
                    if (ind.isActive) ind.satisfaction = clampD(ind.satisfaction - share * 25.0, 0.0, 100.0)
                }
            }
        } else if (line == "admin") {
            s.econ.corruption = clampD(s.econ.corruption - share * 10.0, 5.0, 95.0)
        }
    }

    fun applyNominalGrowth(s: GameState) {
        val g = s.econ.nominalGrowth * s.f
        s.budget.grow(g)
        s.econ.priceIndex *= 1.0 + g
    }

    fun step(s: GameState, net: Double): List<String> {
        val e = s.econ
        val f = s.f
        val k = s.k
        val notes = mutableListOf<String>()

        if (net < 0.0) {
            e.deficitStreak += 1
            e.surplusStreak = 0
        } else {
            e.surplusStreak += 1
            e.deficitStreak = 0
        }

        if (e.deficitStreak >= 3 * k) {
            e.inflation += 0.30 * f
        } else if (net >= 0.0 && e.inflation > TARGET_INFLATION) {
            val coolStep = if (e.inflation > 4.0) 0.55 else 0.35
            e.inflation = maxOf(TARGET_INFLATION, e.inflation - coolStep * f)
        } else if (e.inflation > 4.5 && e.grain <= 150.0) {
            e.inflation -= 0.15 * f
        }
        if (e.grain > 150.0) e.inflation += 0.25 * f
        e.inflation = clampD(e.inflation, 1.0, MAX_INFLATION)
        if (e.deficitStreak == 4 * k) {
            notes.add("أزمة نقدية: العجز المتواصل يضعف العملة ويرفع الأسعار")
        }

        for (p in e.provinces.values) {
            if (p.isLost) continue
            var d = 0.05 * (s.stability - 55) + 0.03 * (p.def.mood - p.mood)
            if (e.grain > 130.0) {
                d -= (e.grain - 130.0) / 40.0
            } else if (e.grain < 90.0) {
                d += 0.5
            }
            p.mood = clampD(p.mood + d * f, 0.0, 100.0)
            if (p.mood < 15.0) notes.add("اضطرابات وتمرد في ${p.name}")
        }

        val rev = s.budget.totalRevenue
        if (rev > 0.0) {
            val adminShare = s.budget.lineValue("admin") / rev
            var c = 0.0
            if (adminShare < 0.20) c += 0.5
            if (adminShare > 0.30) c -= 0.5
            val farmers = s.powers["tax_farmers"]
            if (farmers != null && !farmers.dissolved && farmers.influence > 70.0) c += 0.3
            if (s.education > 40) c -= 0.2
            if (s.grandVizier == "reformer") c -= 0.5
            if (s.sultan.trait == "reformer") c -= 0.25
            c -= ResearchRules.corruptionDriftBonus(s.unlockedTechs)
            e.corruption = clampD(e.corruption + c * f, 5.0, 95.0)
        }

        demography(s)
        return notes
    }

    private fun demography(s: GameState) {
        val d = s.demog
        val e = s.econ
        val popBefore = d.population
        var g = 0.0020 + 0.00001 * (s.stability - 55)
        if (e.grain > 130.0) g -= 0.0008 * ((e.grain - 130.0) / 50.0)
        if (g < -0.005) g = -0.005
        d.population = clampD(d.population * (1.0 + g * s.f), 1.0, 500.0)
        val popGrowth = if (popBefore > 0.0) (d.population - popBefore) / popBefore else 0.0
        if (popGrowth > 0.0) {
            s.budget.spending["admin"] = clampD(
                (s.budget.spending["admin"] ?: 0.0) * (1.0 + popGrowth * ADMIN_POP_ELASTICITY),
                0.0,
                1e15,
            )
            s.budget.spending["civil"] = clampD(
                (s.budget.spending["civil"] ?: 0.0) * (1.0 + popGrowth * CIVIL_POP_ELASTICITY),
                0.0,
                1e15,
            )
        }

        val civilHalf = s.budget.lineValue("civil") / 2.0 * s.f
        val newSchools = civilHalf * SCHOOL_SHARE / (SCHOOL_COST * e.priceIndex)
        d.schools = clampD(d.schools + newSchools, 0.0, 1e7)

        val perMillion = d.schools / d.population
        var dl = (0.02 + 0.0008 * (s.education - 15) + 0.00002 * (perMillion - 346.0))
            .coerceIn(-0.02, 0.15)
        val intel = s.powers["intelligentsia"]
        if (intel != null && intel.isActive && intel.satisfaction >= 65.0) {
            dl = (dl + 0.04).coerceIn(-0.02, 0.19)
        }
        if (s.sultan.trait == "reformer") {
            dl = (dl + 0.025).coerceIn(-0.02, 0.22)
        }
        if (s.pressPolicy == "free") {
            dl = (dl + 0.025).coerceIn(-0.02, 0.24)
            if (intel != null && intel.isActive) {
                intel.satisfaction = clampD(intel.satisfaction + 0.35 * s.f, 0.0, 100.0)
            }
        } else if (GazetteRules.isUnlocked(s)) {
            dl = (dl - 0.008).coerceIn(-0.02, 0.22)
        }
        val techLitSpeed = ResearchRules.literacySpeedBonus(s.unlockedTechs)
        if (techLitSpeed > 0.0) {
            dl = (dl + techLitSpeed).coerceIn(-0.02, 0.24 + techLitSpeed)
        }
        d.literacy = clampD(d.literacy + dl * s.f, 1.0, 80.0)
    }
}
