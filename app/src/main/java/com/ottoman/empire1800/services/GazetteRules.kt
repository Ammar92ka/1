package com.ottoman.empire1800.services

import com.ottoman.empire1800.data.projectDef
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.GazetteIssue
import com.ottoman.empire1800.models.clampD
import com.ottoman.empire1800.models.countryDef
import java.util.Locale
import kotlin.math.roundToInt

/** قواعد «وقائع إسطنبول» وسياسة الصحافة (توليد محلي بالكامل بدون أي استدعاء ذكاء اصطناعي). */
object GazetteRules {
    const val MAX_ISSUES: Int = 8

    fun isUnlocked(s: GameState): Boolean =
        s.completed.contains("printing") ||
            s.education >= 25 ||
            IndustryRules.totalFactoryLevels(s.econ, "paper_mill") >= 1

    fun policyLabel(policy: String): String =
        if (policy == "free") "حرية الصحافة" else "رقابة الديوان"

    fun policyBonus(policy: String): String =
        if (policy == "free") {
            "تسرّع محو الأمية والتعليم (+1 تعليم ومكانة دوريا) وتكسب ودّ المثقفين"
        } else {
            "تهدّئ الاضطراب السياسي عند الأزمات وترضي المحافظين والعلماء"
        }

    fun policyTradeoff(policy: String): String =
        if (policy == "free") {
            "ترفع حساسية الشارع تجاه الفساد والعجز المالي وتغضب المحافظين قليلا"
        } else {
            "تبطئ نمو التعليم والصحافة وتخصم قليلا من المكانة الدولية بعد انتشار المطابع"
        }

    fun setPressPolicy(s: GameState, policy: String): CourtResult {
        val target = if (policy == "free") "free" else "censored"
        val label = policyLabel(target)
        if (s.pressPolicy == target) {
            return CourtResult(false, "سياسة \"$label\" مطبقة بالفعل")
        }
        if (s.courtActions <= 0) return CourtResult(false, exhaustedCourtMsg(s))
        s.courtActions -= 1
        s.pressPolicy = target

        fun adj(key: String, d: Double) {
            val p = s.powers[key]
            if (p != null && p.isActive) {
                p.satisfaction = clampD(p.satisfaction + d, 0.0, 100.0)
            }
        }
        if (target == "free") {
            adj("intelligentsia", 4.0)
            adj("ulema", -2.0)
        } else {
            adj("ulema", 2.0)
            adj("intelligentsia", -4.0)
        }
        s.courtLog.add("اعتماد سياسة الصحافة: $label")
        recordRoyalDecree(s, "فرمان المطبوعات: $label")
        return CourtResult(
            ok = true,
            message = if (target == "free") {
                "أُعلنت حرية الصحافة: تتسارع المعارف وتنكشف أخبار الفساد في وقائع إسطنبول."
            } else {
                "فُرضت رقابة الديوان: تهدأ السجالات السياسية وتُراجع الأخبار قبل النشر."
            },
        )
    }

    fun generateHeadlines(
        s: GameState,
        events: List<String>,
        net: Double,
        emergencyBorrowed: Double,
    ): List<String> {
        val out = mutableListOf<String>()
        fun pushUnique(line: String) {
            val clean = line.trim()
            if (clean.isNotEmpty() && !out.contains(clean) && out.size < 3) {
                out.add(clean)
            }
        }
        val isFree = s.pressPolicy == "free"
        val k = s.k
        val totalFac = IndustryRules.totalFactoryLevels(s.econ)
        val paperLvls = IndustryRules.totalFactoryLevels(s.econ, "paper_mill")

        // 1. Oil discovery
        val oilEvent = events.firstOrNull { it.contains("النفط") }
        if (oilEvent != null) {
            pushUnique("بشرى اقتصادية: ${oilEvent.removeSuffix("!")} ومضاعفة عوائد التعدين.")
        }

        // 2. Wars / frontier conquests
        val warVictory = events.firstOrNull {
            it.contains("انتصر") || it.contains("ضم إقليم") || it.contains("فتح وضم")
        }
        val warDefeat = events.firstOrNull {
            it.contains("هزيمة") || it.contains("تراجع") || it.contains("صلح")
        }
        if (warVictory != null) {
            pushUnique("الديوان يعلن أنباء الجبهة: $warVictory")
        } else if (warDefeat != null) {
            pushUnique(
                if (isFree) "وقائع الجبهات تنقل قلق العواصم: $warDefeat"
                else "بلاغ عسكري مقتضب حول تطورات الحدود وإعادة التموضع.",
            )
        } else if (s.wars.isNotEmpty()) {
            val w = s.wars.first()
            val eName = countryDef(w.enemy)?.name ?: w.enemy
            pushUnique(
                if (w.score >= 10.0) {
                    "تقدم القوات العثمانية على جبهة $eName وسط استنفار الولايات."
                } else {
                    "استمرار التعبئة والمناوشات على حدود $eName وترقّب في الآستانة."
                },
            )
        }

        // 3. Rebellions / provincial unrest
        val troubledProv = s.econ.provinces.values.firstOrNull {
            !it.isLost && (it.mood < 25.0 || it.autonomy >= 70.0)
        }
        val unrestEv = events.firstOrNull {
            it.contains("تمرد") || it.contains("انفصل") || it.contains("حكما ذاتيا")
        }
        if (unrestEv != null) {
            pushUnique(
                if (isFree) "رسائل الأقاليم: $unrestEv"
                else "تنبيهات إدارية من الولاة لضبط الأمن وتحصيل الأرزاق.",
            )
        } else if (troubledProv != null) {
            val pName = troubledProv.name
            pushUnique(
                if (isFree) {
                    "احتقان متصاعد في ولاية $pName ومطالب بتخفيف الأعباء عن الأهالي."
                } else {
                    "إجراءات أمنية في $pName لصون هيبة الدولة وانتظام الخراج."
                },
            )
        }

        // 4. Grain & prices
        val grainVal = s.econ.grain.roundToInt()
        if (s.econ.grain >= 180.0) {
            pushUnique("غلاء فاحش في أسواق الخبز بالآستانة وبلوغ الحبوب مستوى $grainVal!")
        } else if (s.econ.grain >= 140.0) {
            pushUnique(
                if (isFree) {
                    "شكاوى التجار والأهالي من موجة غلاء الحبوب ($grainVal) وضغط المعيشة."
                } else {
                    "لجنة الحسبة تراقب مخازن الحبوب لضبط الأسعار عند مستوى $grainVal."
                },
            )
        } else if (s.econ.grain < 88.0) {
            pushUnique("موسم حصاد وفير يُرخص الخبز في إسطنبول والأناضول والشام.")
        }

        // 5. Debt / DAD / Emergency borrowing / Surplus
        if (s.dadActive) {
            pushUnique(
                if (isFree) {
                    "ضغوط إدارة الديون العمومية تثقل الميزانية وتثير نقاشا حول السيادة المالية."
                } else {
                    "تنظيم دفعات الدين العام عبر إدارة الموارد المخصصة للخزينة."
                },
            )
        } else if (emergencyBorrowed > 0.0 || s.econ.deficitStreak >= 4 * k) {
            val infText = String.format(Locale.US, "%.1f", s.econ.inflation)
            pushUnique(
                if (isFree) {
                    "عجز الخزينة يضطر النظارة لاقتراض عاجل وسط تحذيرات الدفتردار من التضخم ($infText%)."
                } else {
                    "تدابير مالية لتغطية نفقات الفترة وموازنة التزامات الخزينة."
                },
            )
        } else if (s.econ.surplusStreak >= 3 * k && net > 0.0) {
            pushUnique("انتعاش ملحوظ في الخزينة الهمايونية بعد سلسلة فوائض منتظمة.")
        }

        // 6. Factories & Paper Mills
        if (totalFac >= 2 || paperLvls >= 1) {
            val coalNote = if (IndustryRules.hasCoalPower(s.econ)) "بتشغيل الفحم الحجري" else "في الولايات الصناعية"
            val paperSuffix = if (paperLvls >= 1) " وازدهار مطاحن الورق" else ""
            pushUnique("توسّع ورش الإنتاج والمصانع ($totalFac مستويات) $coalNote$paperSuffix.")
        }

        // 7. Reforms / Projects / General fill to guarantee 2-3 headlines
        if (out.size < 2) {
            val reformEv = events.firstOrNull {
                it.contains("انضباط") || it.contains("مشروع") || it.contains("المتعلمين") || it.contains("الصناعيون")
            }
            val activeProj = s.project
            if (reformEv != null) {
                pushUnique(reformEv)
            } else if (activeProj != null) {
                val pName = projectDef(activeProj.key)?.name ?: activeProj.key
                pushUnique("تواصل الأشغال في مشروع \"$pName\" (${activeProj.funded}/${activeProj.halves}).")
            }
        }

        if (out.size < 2) {
            val vizier = grandVizierDef(s.grandVizier)
            pushUnique(
                if (isFree) {
                    "مقالة افتتاحية في عهد ${s.sultan.name}: جدل في مجالس الآستانة حول خطط ${vizier.name}."
                } else {
                    "النشرة الرسمية للسلطنة في عهد ${s.sultan.name} تؤكد استقرار الدواوين وانتظام العمل."
                },
            )
        }

        if (out.size < 2) {
            pushUnique("حركة الموانئ والأسواق العثمانية تسير بوتيرة هادئة مع متابعة أخبار العواصم الأوروبية.")
        }

        return out.take(3)
    }

    fun recordIssue(
        s: GameState,
        round: Int,
        period: String,
        events: List<String>,
        net: Double,
        emergencyBorrowed: Double,
    ): GazetteIssue {
        val issue = GazetteIssue(
            round = round,
            period = period,
            headlines = generateHeadlines(s, events, net, emergencyBorrowed),
            policy = s.pressPolicy,
        )
        val filtered = s.gazette.filter { it.round != issue.round }
        s.gazette.clear()
        s.gazette.add(issue)
        for (item in filtered) {
            if (s.gazette.size >= MAX_ISSUES) break
            s.gazette.add(item)
        }
        return issue
    }

    fun latestOrPreview(s: GameState, period: String): GazetteIssue? {
        if (!isUnlocked(s)) return null
        if (s.gazette.isNotEmpty()) return s.gazette.first()
        return GazetteIssue(
            round = s.round,
            period = period,
            headlines = generateHeadlines(s, s.events, 0.0, 0.0),
            policy = s.pressPolicy,
        )
    }
}
