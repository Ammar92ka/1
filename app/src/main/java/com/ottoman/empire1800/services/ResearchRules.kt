package com.ottoman.empire1800.services

import com.ottoman.empire1800.data.TechDef
import com.ottoman.empire1800.data.techCatalog
import com.ottoman.empire1800.data.techDef
import com.ottoman.empire1800.models.ActiveResearch
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.clampD
import com.ottoman.empire1800.models.countryDef
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.roundToInt

/** البحث العلمي واكتساب التقنية (1800–1970). */
object ResearchRules {
    val TECH_PARTNERS: List<String> = listOf("britain", "france", "germany")
    const val MISSION_SPEED_MULT: Double = 1.60
    const val MISSION_QUARTERLY_FEE_SHARE: Double = 0.015
    const val MIN_MISSION_RELATION: Double = 55.0
    const val CANCEL_MISSION_RELATION: Double = 40.0
    const val FREE_PRESS_RESEARCH_BONUS: Double = 0.15

    data class PointsBreakdown(
        val fromEdu: Double,
        val fromLit: Double,
        val fromSchools: Double,
        val fromPaper: Double,
        val fromUniv: Double,
        val freePressActive: Boolean,
        val totalPerPeriod: Double,
    )

    data class CheckResult(
        val ok: Boolean,
        val reason: String? = null,
        val def: TechDef? = null,
    )

    data class BonusesSummary(
        val factoryPct: Int,
        val warPct: Int,
        val adminPct: Int,
        val plaguePct: Int,
        val droughtPct: Int,
        val corruptionDrift: Double,
        val literacySpeed: Double,
    )

    fun pointsBreakdown(s: GameState): PointsBreakdown {
        val f = s.f
        val pressMult = if (s.pressPolicy == "free") 1.0 + FREE_PRESS_RESEARCH_BONUS else 1.0
        val rawEdu = s.education * 0.12
        val rawLit = s.demog.literacy * 0.16
        val rawSchools = min(4.0, s.demog.schools / 6000.0)
        val paperLvls = IndustryRules.totalFactoryLevels(s.econ, "paper_mill")
        val rawPaper = paperLvls * 0.70
        val rawUniv = if (s.completed.contains("universities")) 4.0 else 0.0
        val totalPerPeriod = max(0.5, rawEdu + rawLit + rawSchools + rawPaper + rawUniv) * pressMult * f
        return PointsBreakdown(
            fromEdu = rawEdu * pressMult * f,
            fromLit = rawLit * pressMult * f,
            fromSchools = rawSchools * pressMult * f,
            fromPaper = rawPaper * pressMult * f,
            fromUniv = rawUniv * pressMult * f,
            freePressActive = s.pressPolicy == "free",
            totalPerPeriod = totalPerPeriod,
        )
    }

    fun pointsPerPeriod(s: GameState): Double = pointsBreakdown(s).totalPerPeriod

    fun canResearch(s: GameState, key: String): CheckResult {
        val def = techDef(key) ?: return CheckResult(false, "تقنية غير معروفة")
        if (s.unlockedTechs.contains(key)) {
            return CheckResult(false, "هذه التقنية مكتسبة بالفعل", def)
        }
        val year = s.year
        if (year < def.fromYear) {
            return CheckResult(false, "تُتاح من عام ${def.fromYear}", def)
        }
        if (s.demog.literacy < def.minLiteracy) {
            return CheckResult(false, "يتطلب محو الأمية ${def.minLiteracy}% على الأقل", def)
        }
        if (s.education < def.minEducation) {
            return CheckResult(false, "يتطلب التعليم ${def.minEducation} على الأقل", def)
        }
        return CheckResult(true, null, def)
    }

    fun canStartForeignMission(s: GameState, partnerKey: String): CheckResult {
        if (!TECH_PARTNERS.contains(partnerKey)) {
            return CheckResult(false, "الشريك العلمي يجب أن يكون بريطانيا أو فرنسا أو ألمانيا")
        }
        val c = s.countries[partnerKey] ?: return CheckResult(false, "دولة غير معروفة")
        val name = countryDef(partnerKey)?.name ?: partnerKey
        if (WarRules.warWith(s, partnerKey) != null) {
            return CheckResult(false, "لا يمكن إرسال بعثة أثناء الحرب مع $name")
        }
        if (!c.embassy) {
            return CheckResult(false, "يتطلب إيفاد البعثة فتح سفارة دائمة في $name أولا")
        }
        if (c.relation < MIN_MISSION_RELATION) {
            return CheckResult(false, "يتطلب علاقة ≥${MIN_MISSION_RELATION.toInt()} مع $name")
        }
        return CheckResult(true)
    }

    fun missionQuarterlyFee(s: GameState): Double =
        max(0.0, costShare(s, MISSION_QUARTERLY_FEE_SHARE * 0.5 * s.f))

    fun importCost(s: GameState, key: String): Double {
        val def = techDef(key) ?: return 0.0
        val share = clampD(0.10 + (def.researchCost / 160.0) * 0.14, 0.10, 0.25)
        return round(s.budget.totalRevenue * share)
    }

    fun startDomestic(s: GameState, key: String): CourtResult {
        if (s.activeResearch != null) {
            return CourtResult(false, "يوجد بحث علمي جارٍ بالفعل؛ أتمّه أو ألغه قبل بدء غيره")
        }
        val chk = canResearch(s, key)
        val def = chk.def
        if (!chk.ok || def == null) {
            return CourtResult(false, chk.reason ?: "تعذر بدء البحث")
        }
        if (s.courtActions <= 0) return CourtResult(false, exhaustedCourtMsg(s))
        s.courtActions -= 1
        s.activeResearch = ActiveResearch(
            key = key,
            method = "domestic",
            partnerKey = "",
            progress = 0.0,
            target = def.researchCost.toDouble(),
            startRound = s.round,
        )
        s.courtLog.add("بدء البحث الوطني: ${def.name}")
        return CourtResult(true, "بدأ البحث الوطني في «${def.name}» عبر دار الفنون (${def.researchCost} نقطة)")
    }

    fun startForeignMission(s: GameState, key: String, partnerKey: String): CourtResult {
        if (s.activeResearch != null) {
            return CourtResult(false, "يوجد بحث علمي جارٍ بالفعل؛ أتمّه أو ألغه قبل بدء غيره")
        }
        val chk = canResearch(s, key)
        val def = chk.def
        if (!chk.ok || def == null) {
            return CourtResult(false, chk.reason ?: "تعذر بدء البعثة")
        }
        val pChk = canStartForeignMission(s, partnerKey)
        if (!pChk.ok) return CourtResult(false, pChk.reason ?: "شروط البعثة غير مكتملة")
        if (s.courtActions <= 0) return CourtResult(false, exhaustedCourtMsg(s))
        s.courtActions -= 1
        val partnerName = countryDef(partnerKey)?.name ?: partnerKey
        s.activeResearch = ActiveResearch(
            key = key,
            method = "foreign_mission",
            partnerKey = partnerKey,
            progress = 0.0,
            target = def.researchCost.toDouble(),
            startRound = s.round,
        )
        s.courtLog.add("بعثة علمية إلى $partnerName: ${def.name}")
        return CourtResult(true, "أُوفدت بعثة علمية إلى $partnerName لبحث «${def.name}» (+60% سرعة، كلفة ربعية مخفضة)")
    }

    fun importPatent(s: GameState, key: String): CourtResult {
        val chk = canResearch(s, key)
        val def = chk.def
        if (!chk.ok || def == null) {
            return CourtResult(false, chk.reason ?: "تعذر الاستيراد")
        }
        if (s.courtActions <= 0) return CourtResult(false, exhaustedCourtMsg(s))
        val cost = importCost(s, key)
        if (s.treasury < cost) return CourtResult(false, "الخزينة لا تكفي لاستيراد البراءة والمعدات الجاهزة")

        s.treasury -= cost
        s.courtActions -= 1
        if (s.activeResearch?.key == key) {
            s.activeResearch = null
        }
        s.unlockedTechs.add(key)

        var ulemaNote = ""
        if (s.year < 1880) {
            val ulema = s.powers["ulema"]
            if (ulema != null && ulema.isActive) {
                ulema.satisfaction = clampD(ulema.satisfaction - 3.0, 0.0, 100.0)
                ulemaNote = " (مع تحفظ العلماء -3)"
            }
        }
        s.courtLog.add("استيراد تقنية: ${def.name}")
        recordRoyalDecree(s, "اقتناء براءة ومعدات: ${def.name}")
        return CourtResult(true, "تم استيراد وتركيب «${def.name}» فورا (${def.bonusSummary})$ulemaNote")
    }

    fun cancel(s: GameState): CourtResult {
        val r = s.activeResearch ?: return CourtResult(false, "لا يوجد بحث علمي جارٍ لإلغائه")
        val def = techDef(r.key)
        s.activeResearch = null
        s.courtLog.add("إلغاء البحث: ${def?.name ?: ""}")
        return CourtResult(true, "أُلغي البحث العلمي في «${def?.name ?: ""}»")
    }

    fun step(s: GameState): List<String> {
        val r = s.activeResearch ?: return emptyList()
        val def = techDef(r.key)
        if (def == null || s.unlockedTechs.contains(r.key)) {
            s.activeResearch = null
            return emptyList()
        }

        if (r.method == "foreign_mission") {
            val c = s.countries[r.partnerKey]
            val partnerName = countryDef(r.partnerKey)?.name ?: r.partnerKey
            val atWar = WarRules.warWith(s, r.partnerKey) != null
            if (c == null || atWar || !c.embassy || c.relation < CANCEL_MISSION_RELATION) {
                val why = when {
                    atWar -> "اندلاع الحرب"
                    c == null || !c.embassy -> "إغلاق السفارة"
                    else -> "تدهور العلاقات دون 40"
                }
                s.activeResearch = null
                return listOf("أُلغيت البعثة العلمية لدى $partnerName لبحث «${def.name}» بسبب $why.")
            }
            val fee = missionQuarterlyFee(s)
            if (s.treasury < fee) {
                return listOf("تعثر تقدم بعثة «${def.name}» لدى $partnerName مؤقتا لنقص التمويل.")
            }
            s.treasury -= fee
        }

        val mult = if (r.method == "foreign_mission") MISSION_SPEED_MULT else 1.0
        r.progress += pointsPerPeriod(s) * mult
        if (r.progress >= r.target) {
            s.unlockedTechs.add(r.key)
            s.activeResearch = null
            recordRoyalDecree(s, "إتمام البحث العلمي: ${def.name}")
            return listOf("أنجزت السلطنة تقنية «${def.name}»: ${def.bonusSummary}.")
        }
        return emptyList()
    }

    fun factoryOutputBonus(unlockedTechs: List<String>?): Double {
        if (unlockedTechs.isNullOrEmpty()) return 0.0
        var total = 0.0
        for (k in unlockedTechs) {
            total += techDef(k)?.bonuses?.factoryOutputPct ?: 0.0
        }
        return clampD(total, 0.0, 0.80)
    }

    fun warPowerBonus(unlockedTechs: List<String>?): Double {
        if (unlockedTechs.isNullOrEmpty()) return 0.0
        var total = 0.0
        for (k in unlockedTechs) {
            total += techDef(k)?.bonuses?.warPowerPct ?: 0.0
        }
        return clampD(total, 0.0, 0.60)
    }

    fun plagueReduction(unlockedTechs: List<String>?): Double {
        if (unlockedTechs.isNullOrEmpty()) return 0.0
        var total = 0.0
        for (k in unlockedTechs) {
            total += techDef(k)?.bonuses?.plagueLossReducePct ?: 0.0
        }
        return clampD(total, 0.0, 0.75)
    }

    fun droughtReduction(unlockedTechs: List<String>?): Double {
        if (unlockedTechs.isNullOrEmpty()) return 0.0
        var total = 0.0
        for (k in unlockedTechs) {
            total += techDef(k)?.bonuses?.droughtLossReducePct ?: 0.0
        }
        return clampD(total, 0.0, 0.75)
    }

    fun adminDiscountBonus(unlockedTechs: List<String>?): Double {
        if (unlockedTechs.isNullOrEmpty()) return 0.0
        var total = 0.0
        for (k in unlockedTechs) {
            total += techDef(k)?.bonuses?.adminDiscountPct ?: 0.0
        }
        return clampD(total, 0.0, 0.25)
    }

    fun corruptionDriftBonus(unlockedTechs: List<String>?): Double {
        if (unlockedTechs.isNullOrEmpty()) return 0.0
        var total = 0.0
        for (k in unlockedTechs) {
            total += techDef(k)?.bonuses?.corruptionDrift ?: 0.0
        }
        return clampD(total, 0.0, 0.60)
    }

    fun literacySpeedBonus(unlockedTechs: List<String>?): Double {
        if (unlockedTechs.isNullOrEmpty()) return 0.0
        var total = 0.0
        for (k in unlockedTechs) {
            total += techDef(k)?.bonuses?.literacySpeedAdd ?: 0.0
        }
        return clampD(total, 0.0, 0.10)
    }

    fun summaryBonuses(unlockedTechs: List<String>?): BonusesSummary = BonusesSummary(
        factoryPct = (factoryOutputBonus(unlockedTechs) * 100.0).roundToInt(),
        warPct = (warPowerBonus(unlockedTechs) * 100.0).roundToInt(),
        adminPct = (adminDiscountBonus(unlockedTechs) * 100.0).roundToInt(),
        plaguePct = (plagueReduction(unlockedTechs) * 100.0).roundToInt(),
        droughtPct = (droughtReduction(unlockedTechs) * 100.0).roundToInt(),
        corruptionDrift = corruptionDriftBonus(unlockedTechs),
        literacySpeed = literacySpeedBonus(unlockedTechs),
    )

    fun promptSummary(s: GameState): String {
        val unlocked = s.unlockedTechs
        val total = techCatalog.size
        val latestDef = if (unlocked.isNotEmpty()) techDef(unlocked.last()) else null
        val active = s.activeResearch
        val activeDef = if (active != null) techDef(active.key) else null
        val activePart = if (active != null && activeDef != null) {
            val pct = min(99, ((active.progress / max(1.0, active.target)) * 100.0).roundToInt())
            "جارٍ: ${activeDef.name} $pct%"
        } else {
            ""
        }
        val unlockedPart = if (latestDef != null) {
            "${unlocked.size}/$total (آخرها ${latestDef.name})"
        } else {
            "0/$total"
        }
        return if (activePart.isNotEmpty()) "البحث العلمي: $unlockedPart · $activePart" else "البحث العلمي: $unlockedPart"
    }
}
