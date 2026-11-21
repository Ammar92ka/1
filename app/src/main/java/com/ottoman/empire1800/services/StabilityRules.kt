package com.ottoman.empire1800.services

import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.StabilityState

/** قواعد الاستقرار: كل بعد من الستة يتحرك بأسبابه، والكلي متوسطها. */
object StabilityRules {
    fun step(s: GameState, debtRatio: Double, emergency: Boolean): List<String> {
        val st = s.stab
        val e = s.econ
        val f = s.f
        val k = s.k
        fun sh(key: String, d: Double) = st.shift(key, d * f)
        val notes = mutableListOf<String>()

        val u = EconomyRules.debtUnrest(debtRatio).toDouble()
        if (u > 0.0) {
            sh("economic", -3.0 * u)
            sh("social", -1.5 * u)
            sh("political", -1.5 * u)
        }

        if (e.deficitStreak >= 8 * k) {
            sh("economic", -2.0)
        } else if (e.deficitStreak >= 4 * k) {
            sh("economic", -1.0)
        }
        if (e.inflation > 8.0) sh("economic", -1.0)
        if (e.surplusStreak >= 3 * k && debtRatio < 1.5) sh("economic", 1.0)

        if (e.grain >= 180.0) {
            sh("social", -2.0)
            notes.add("مجاعة وغلاء فاحش يهزان المجتمع")
        } else if (e.grain >= 140.0) {
            sh("social", -1.0)
        }
        val mood = e.avgMood
        if (mood < 35.0) sh("social", -1.0)
        if (mood > 65.0 && e.grain < 110.0) sh("social", 1.0)

        val jan = s.powers["janissaries"]
        if (jan != null && !jan.dissolved && jan.satisfaction < 25.0) sh("military", -1.0)
        if (s.army < 25) sh("military", -1.0)

        if (s.legitimacy < 35) sh("political", -1.0)
        if (s.legitimacy > 75) sh("political", 1.0)
        if (averageDanger(s) > 70.0) sh("political", -1.0)

        val ulema = s.powers["ulema"]
        if (ulema != null && !ulema.dissolved) {
            if (ulema.satisfaction < 30.0) sh("religious", -1.0)
            if (ulema.satisfaction > 70.0) sh("religious", 1.0)
        }

        val intel = s.powers["intelligentsia"]
        if (intel != null && intel.isActive) {
            if (intel.satisfaction < 40.0) {
                sh("political", if (intel.satisfaction < 25.0 || s.dadActive) -1.8 else -1.0)
                if (intel.satisfaction < 35.0) {
                    notes.add("ضغوط متصاعدة من المتعلمين والمثقفين للمطالبة بالدستور وإصلاح الحكم")
                }
            } else if (intel.satisfaction >= 75.0 && s.legitimacy >= 50) {
                sh("political", 0.5)
            }
        }

        if (mood < 35.0) sh("regional", -1.0)
        if (mood > 65.0) sh("regional", 1.0)
        if (e.provinces.values.any { !it.isLost && it.mood < 20.0 }) sh("regional", -1.0)
        val gov = s.powers["governors"]
        if (gov != null && !gov.dissolved && gov.satisfaction < 30.0) sh("regional", -1.0)
        if (s.grandVizier == "economist") sh("political", -0.4)

        if (s.pressPolicy == "free") {
            if (e.corruption >= 55.0 || emergency || e.deficitStreak >= 4 * k || u > 0.0) {
                sh("political", -0.8)
                if (e.corruption >= 55.0 || emergency) {
                    notes.add("الصحافة الحرة تسلط الضوء على الفساد والعجز وتزيد الجدل السياسي")
                }
            }
        } else if (u > 0.0 || e.deficitStreak >= 4 * k || averageDanger(s) > 70.0 || s.legitimacy < 35) {
            sh("political", 0.5)
        }

        for (key in StabilityState.keys) {
            val value = st[key]
            if (value > 75.0) {
                sh(key, -0.035 * (value - 75.0))
            }
        }

        return notes
    }

    fun averageDanger(s: GameState): Double {
        val active = s.powers.values.filter { it.isActive }
        if (active.isEmpty()) return 0.0
        return active.sumOf { it.danger } / active.size
    }
}
