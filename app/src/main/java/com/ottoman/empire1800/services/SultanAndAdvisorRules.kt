package com.ottoman.empire1800.services

import com.ottoman.empire1800.models.AdvisorHint
import com.ottoman.empire1800.models.Decision
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.SuccessionCandidate
import com.ottoman.empire1800.models.SultanState
import com.ottoman.empire1800.models.grandVizierDef
import com.ottoman.empire1800.models.isRevenueLine
import com.ottoman.empire1800.models.powerDef

object SultanRules {
    private val traitLabels = mapOf(
        "reformer" to "إصلاحي",
        "conservative" to "محافظ",
        "military" to "عسكري",
        "economist" to "اقتصادي",
    )

    private val traitBonuses = mapOf(
        "reformer" to "إصلاحي: تسريع محو الأمية وخفض الفساد تدريجيا (+0.3 رضا للمثقفين)",
        "conservative" to "محافظ: +1 شرعية كل عام وتهدئة العلماء (+0.3 رضا)",
        "military" to "عسكري: +5% قوة في المعارك وحماية تعبئة الجيش (+0.3 رضا للضباط)",
        "economist" to "اقتصادي: +3% عوائد الجمارك والمصانع وخفض التبذير الإداري -3%",
    )

    fun traitLabel(trait: String): String = traitLabels[trait] ?: traitLabels["reformer"]!!

    fun traitBonus(trait: String): String = traitBonuses[trait] ?: traitBonuses["reformer"]!!

    fun yearsOnThrone(s: GameState): Int = maxOf(1, s.year - s.sultan.enthronedYear + 1)

    fun reignDurationYears(sultan: SultanState, seed: Int): Int =
        25 + (stableHash("${sultan.name}:${sultan.reignOrder}:$seed") % 6)

    fun shouldTriggerSuccession(s: GameState): Boolean {
        if (s.pendingSuccession) return false
        val elapsed = s.year - s.sultan.enthronedYear
        return elapsed >= reignDurationYears(s.sultan, s.seed)
    }

    fun candidatesFor(sultan: SultanState): List<SuccessionCandidate> {
        return when (sultan.reignOrder) {
            1 -> listOf(
                SuccessionCandidate(
                    name = "السلطان محمود الثاني",
                    trait = "reformer",
                    summary = "سلطان التنظيمات الأول ومؤسس الإدارة والجيش الحديث.",
                    bonus = traitBonus("reformer"),
                ),
                SuccessionCandidate(
                    name = "السلطان مصطفى الرابع",
                    trait = "conservative",
                    summary = "تمسك بالتوازن التقليدي والتحالف مع العلماء والأعيان.",
                    bonus = traitBonus("conservative"),
                ),
            )
            2 -> listOf(
                SuccessionCandidate(
                    name = "السلطان عبد المجيد الأول",
                    trait = "reformer",
                    summary = "راعي خط كلخانة وإصلاح التعليم والقضاء والإدارة.",
                    bonus = traitBonus("reformer"),
                ),
                SuccessionCandidate(
                    name = "السلطان عبد العزيز",
                    trait = "military",
                    summary = "عزز قدرات الجيش النظامي وبناء الأسطول الإمبراطوري.",
                    bonus = traitBonus("military"),
                ),
            )
            3 -> listOf(
                SuccessionCandidate(
                    name = "السلطان مراد الخامس",
                    trait = "economist",
                    summary = "مال إلى إصلاح المالية العامة وتنشيط الصناعة والتجارة.",
                    bonus = traitBonus("economist"),
                ),
                SuccessionCandidate(
                    name = "السلطان عبد الحميد الثاني",
                    trait = "conservative",
                    summary = "شدد قبضة الخلافة وركز على الشرعية وتماسك الولايات.",
                    bonus = traitBonus("conservative"),
                ),
            )
            4 -> listOf(
                SuccessionCandidate(
                    name = "السلطان محمد رشاد الخامس",
                    trait = "economist",
                    summary = "سعى لتهدئة الأزمات المالية وتطوير الإنتاج المحلي.",
                    bonus = traitBonus("economist"),
                ),
                SuccessionCandidate(
                    name = "السلطان محمد وحيد الدين",
                    trait = "military",
                    summary = "ركز على انضباط القيادة العسكرية في أوقات التوتر.",
                    bonus = traitBonus("military"),
                ),
            )
            else -> {
                val dynPairs = listOf(
                    listOf(
                        "السلطان عثمان الثالث",
                        "reformer",
                        "وريث إصلاحي يتبنى التحديث الإداري والتعليمي.",
                        "السلطان سليمان الثالث",
                        "military",
                        "وريث عسكري يعطي الأولوية لردع الخصوم وتقوية الجيش.",
                    ),
                    listOf(
                        "السلطان سليم الرابع",
                        "economist",
                        "وريث اقتصادي يركز على موازنة الخزينة والجمارك.",
                        "السلطان أحمد الرابع",
                        "conservative",
                        "وريث محافظ يحافظ على الشرعية وتوازن القوى التقليدية.",
                    ),
                )
                val pair = dynPairs[(maxOf(0, sultan.reignOrder - 5)) % dynPairs.size]
                listOf(
                    SuccessionCandidate(
                        name = pair[0],
                        trait = pair[1],
                        summary = pair[2],
                        bonus = traitBonus(pair[1]),
                    ),
                    SuccessionCandidate(
                        name = pair[3],
                        trait = pair[4],
                        summary = pair[5],
                        bonus = traitBonus(pair[4]),
                    ),
                )
            }
        }
    }

    fun chooseHeir(s: GameState, candidateIdx: Int): SuccessionCandidate {
        val current = s.sultan
        val cands = candidatesFor(current)
        val chosen = cands[if (candidateIdx == 1) 1 else 0]
        s.sultan = SultanState(
            name = chosen.name,
            trait = chosen.trait,
            enthronedYear = s.year,
            reignOrder = current.reignOrder + 1,
        )
        s.pendingSuccession = false
        s.legitimacy = (s.legitimacy + 4).coerceIn(0, 100)
        s.stab.shift("political", 2.0)
        recordRoyalDecree(s, "تولّي ${chosen.name} عرش السلطنة (${traitLabel(chosen.trait)})")
        return chosen
    }
}

object GrandVizierAdvisor {
    private val letters = listOf("أ", "ب", "ج")

    fun advise(s: GameState, dec: Decision): AdvisorHint {
        val vizier = grandVizierDef(s.grandVizier)
        val annualRev = maxOf(1.0, s.budget.totalRevenue)
        val debtRatio = EconomyRules.debtRatio(s.debt, s.budget)
        val riskyPowers = s.powers.values
            .filter { it.isActive && it.danger >= 60.0 }
            .map { it.key }
            .toSet()
        val liquidityFloor = annualRev * 0.08

        var bestIdx = 0
        var bestScore = Double.NEGATIVE_INFINITY

        for (i in dec.options.indices) {
            val opt = dec.options[i]
            var score = 0.0
            val effLegit = opt.now.legitimacy + opt.later.legitimacy * 0.75
            val effStab = opt.now.stability + opt.later.stability * 0.75
            val effArmy = opt.now.army + opt.later.army * 0.75
            val effForeign = opt.now.foreign + opt.later.foreign * 0.75
            val effEdu = opt.now.education + opt.later.education * 0.75
            val lineNet = opt.lineChanges.sumOf { if (isRevenueLine(it.line)) it.delta else -it.delta }

            when (vizier.key) {
                "reformer" -> {
                    score += effEdu * 2.1 + effForeign * 1.2 + effStab * 1.0 + effLegit * 0.6
                    score += (lineNet / annualRev) * 18.0
                }
                "military" -> {
                    score += effArmy * 2.3 + effStab * 0.8 + effLegit * 0.7 + if (opt.war.isNotEmpty()) 1.5 else 0.0
                    if (s.army < 50) score += effArmy * 0.8
                }
                "economist" -> {
                    score += (lineNet / annualRev) * 35.0 + effStab * 1.1 + effForeign * 0.7
                    score -= (opt.cost / annualRev) * 28.0
                    score -= (opt.loan / annualRev) * if (debtRatio >= 2.0) 35.0 else 18.0
                }
                else -> {
                    score += effLegit * 2.1 + effStab * 1.5 + effArmy * 0.5
                    score += (opt.powerMood["ulema"] ?: 0) * 0.8
                }
            }

            val cashLeft = s.treasury - opt.cost + opt.loan
            if (cashLeft < 0.0) score -= 12.0
            else if (cashLeft < liquidityFloor && opt.cost > 0.0) score -= 4.0
            if (opt.loan > 0.0 && debtRatio >= 3.0) score -= 5.0

            for (pk in riskyPowers) {
                val md = opt.powerMood[pk] ?: 0
                if (md > 0) score += md * 1.2
                else if (md < 0) score += md * 1.5
            }

            if (score > bestScore) {
                bestScore = score
                bestIdx = i
            }
        }

        val picked = dec.options.getOrNull(bestIdx) ?: dec.options.firstOrNull()
        val reasons = mutableListOf<String>()

        if (picked != null) {
            when (vizier.key) {
                "reformer" -> if (picked.now.education + picked.later.education > 0) {
                    reasons.add("يدفع هذا المسار نحو التحديث والتعليم وبناء القدرات الإدارية.")
                } else {
                    reasons.add("يتلاءم هذا التوجه مع نهج التنظيمات والإصلاح المتدرج.")
                }
                "military" -> if (picked.now.army + picked.later.army > 0) {
                    reasons.add("يعزز الجاهزية العسكرية ويرفع هيبة الجيش النظامي.")
                } else {
                    reasons.add("يصون تماسك الجبهة الداخلية ويحفظ قدرة الدولة على الردع.")
                }
                "economist" -> if (picked.loan <= 0.0 && picked.cost <= s.treasury * 0.4) {
                    reasons.add("يحفظ توازن الخزينة ويتجنب إثقال الميزانية بديون جديدة.")
                } else {
                    reasons.add("يحقق أفضل عائد مالي ممكن مقارنة بكلفته على الخزينة.")
                }
                else -> if (picked.now.legitimacy + picked.later.legitimacy > 0 || (picked.powerMood["ulema"] ?: 0) > 0) {
                    reasons.add("يرسخ الشرعية العثمانية ويهدئ مراكز النفوذ التقليدية.")
                } else {
                    reasons.add("يحفظ الاستقرار الاجتماعي ويمنع الهزات المفاجئة.")
                }
            }

            for (pk in riskyPowers) {
                if ((picked.powerMood[pk] ?: 0) > 0) {
                    val pName = powerDef(pk)?.name ?: pk
                    reasons.add("يساعد أيضاً في تهدئة $pName المتوترة.")
                    break
                }
            }
        }

        var warning: String? = null
        if (picked != null) {
            val postCash = s.treasury - picked.cost + picked.loan
            val prj = s.project
            if (postCash < 0.0) {
                warning = "تحذير مالي: كلفة هذا الخيار قد تدفع الخزينة للاقتراض الطارئ بنسبة فائدة إضافية."
            } else if (prj != null && !prj.paused && postCash < prj.installment) {
                warning = "تنبيه: الإنفاق هنا قد يؤخر قسط المشروع القومي الجاري ما لم يتوفر فائض."
            } else if (picked.loan > 0.0 && debtRatio >= 2.5) {
                warning = "تنبيه: نسبة الدين مرتفعة؛ الاستدانة الإضافية ترفع كلفة الفوائد السنوية."
            } else {
                for (pk in riskyPowers) {
                    if ((picked.powerMood[pk] ?: 0) < 0) {
                        val pName = powerDef(pk)?.name ?: pk
                        warning = "تحذير: هذا الخيار يزيد غضب $pName وقد يرفع خطر التمرد."
                        break
                    }
                }
            }
        }

        val letter = letters.getOrElse(bestIdx) { "${bestIdx + 1}" }
        return AdvisorHint(
            vizierName = vizier.name,
            recommendedIndex = bestIdx,
            recommendationLabel = "الأرجح عند ${vizier.name}: الخيار ($letter)",
            summary = reasons.joinToString(" "),
            warning = warning,
        )
    }
}
