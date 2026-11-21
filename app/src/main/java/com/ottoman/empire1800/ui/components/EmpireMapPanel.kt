package com.ottoman.empire1800.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ottoman.empire1800.data.economicSystemDef
import com.ottoman.empire1800.data.effectiveSubProvinceResources
import com.ottoman.empire1800.data.factoryDef
import com.ottoman.empire1800.data.factoryDefs
import com.ottoman.empire1800.data.findSubProvinceAtPoint
import com.ottoman.empire1800.data.frontierRegionDef
import com.ottoman.empire1800.data.resourceLabels
import com.ottoman.empire1800.data.subProvinceDef
import com.ottoman.empire1800.data.subProvinceDefs
import com.ottoman.empire1800.data.subProvinceMapGeometries
import com.ottoman.empire1800.models.GameState
import com.ottoman.empire1800.models.ProvinceState
import com.ottoman.empire1800.models.countryDef
import com.ottoman.empire1800.models.provinceDefs
import com.ottoman.empire1800.services.CourtResult
import com.ottoman.empire1800.services.IndustryRules
import com.ottoman.empire1800.services.ProvinceRules
import com.ottoman.empire1800.services.costShare
import com.ottoman.empire1800.ui.theme.AppColors
import com.ottoman.empire1800.ui.util.formatMoney
import java.util.Locale
import kotlin.math.roundToInt

private val primaryResourceShort: Map<String, String> = mapOf(
    "grain" to "حبوب",
    "cotton" to "قطن",
    "silk" to "حرير",
    "wool" to "صوف",
    "coal" to "فحم",
    "minerals" to "معادن",
    "timber" to "خشب",
    "port" to "ميناء",
    "coffee" to "بن",
    "sugar" to "سكر",
    "religious" to "مقام",
    "oil" to "نفط",
)

private data class RegionMapVisual(
    val label: String,
    val fill: Color,
    val stroke: Color,
    val textColor: Color,
    val badgeBg: Color,
)

private val mapVisualByStatus: Map<String, RegionMapVisual> = mapOf(
    ProvinceState.LOYAL to RegionMapVisual(
        label = "موالية",
        fill = Color(0xFF123730),
        stroke = Color(0xFF6FCF97),
        textColor = Color(0xFF6FCF97),
        badgeBg = Color(0xFF102926),
    ),
    ProvinceState.AUTONOMOUS_STATUS to RegionMapVisual(
        label = "حكم ذاتي",
        fill = Color(0xFF352A10),
        stroke = Color(0xFFC9A227),
        textColor = Color(0xFFC9A227),
        badgeBg = Color(0xFF292212),
    ),
    ProvinceState.INDEPENDENT to RegionMapVisual(
        label = "مفقودة",
        fill = Color(0xFF3A1822),
        stroke = Color(0xFFF0868A),
        textColor = Color(0xFFF0868A),
        badgeBg = Color(0xFF2B141C),
    ),
    "locked" to RegionMapVisual(
        label = "ثغر خارج السيطرة",
        fill = Color(0xFF0D192E),
        stroke = Color(0xFF4E6288),
        textColor = Color(0xFF9FB0CC),
        badgeBg = Color(0xFF0F1F3D),
    ),
)

private data class RegionStatusInfo(
    val status: String,
    val isFrontier: Boolean,
    val controlled: Boolean,
    val mood: Double,
    val autonomy: Double,
    val parentName: String,
)

private fun resolveRegionStatus(state: GameState, parentKey: String): RegionStatusInfo {
    val core = state.econ.provinces[parentKey]
    if (core != null) {
        val defName = provinceDefs.firstOrNull { it.key == parentKey }?.name ?: core.name
        return RegionStatusInfo(
            status = core.status,
            isFrontier = false,
            controlled = !core.isLost,
            mood = core.mood,
            autonomy = core.autonomy,
            parentName = defName,
        )
    }
    val fr = state.econ.frontierRegions[parentKey]
    val fDef = frontierRegionDef(parentKey)
    if (fr == null || !fr.controlled) {
        return RegionStatusInfo(
            status = "locked",
            isFrontier = true,
            controlled = false,
            mood = fDef?.mood ?: 55.0,
            autonomy = fDef?.autonomy ?: 20.0,
            parentName = fDef?.name ?: parentKey,
        )
    }
    return RegionStatusInfo(
        status = fr.status,
        isFrontier = true,
        controlled = !fr.isLost,
        mood = fr.mood,
        autonomy = fr.autonomy,
        parentName = fDef?.name ?: parentKey,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmpireMapPanel(
    state: GameState,
    selectedSubKey: String,
    onSelectSubKey: (String) -> Unit,
    noActions: Boolean,
    onRunAction: ((GameState) -> CourtResult) -> Unit,
) {
    val year = state.year
    val sys = economicSystemDef(state.econ.economicSystem)
    val activeKey = if (subProvinceDef(selectedSubKey) != null) selectedSubKey else "anatolia_istanbul"

    val litMult = IndustryRules.literacyMultiplier(state.demog.literacy)
    val hasCoal = IndustryRules.hasCoalPower(state.econ)
    val coalMult = if (hasCoal) 1.25 else 1.0
    val powMult = IndustryRules.powerYieldMultiplier(state.powers)
    val baseEfficiency = sys.yieldMult * litMult * coalMult * powMult

    val selDef = subProvinceDef(activeKey)!!
    val selState = state.econ.subProvinces[selDef.key]
    val selFacs = selState?.factories ?: emptyList()
    val selResources = effectiveSubProvinceResources(selDef, year, state.econ.oilDiscovered)
    val selParentInfo = resolveRegionStatus(state, selDef.parentKey)
    val selVisual = mapVisualByStatus[selParentInfo.status] ?: mapVisualByStatus.getValue(ProvinceState.LOYAL)
    val selFrontierDef = if (selParentInfo.isFrontier) frontierRegionDef(selDef.parentKey) else null
    val selLost = selParentInfo.status == ProvinceState.INDEPENDENT
    val selLocked = selParentInfo.status == "locked"
    val canManageFactories = !selLocked && !selLost

    val textMeasurer = rememberTextMeasurer()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = AppColors.Card,
        border = BorderStroke(1.dp, AppColors.Gold.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        tint = AppColors.Gold,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "خريطة السلطنة والسناجق الـ25",
                        color = AppColors.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp,
                    )
                }
                Text(
                    text = "اضغط على السنجق",
                    color = AppColors.Gold,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Legend
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LegendChip("موالية", Color(0xFF123730), Color(0xFF6FCF97), Color(0xFF6FCF97))
                LegendChip("حكم ذاتي", Color(0xFF352A10), Color(0xFFC9A227), Color(0xFFC9A227))
                LegendChip("مفقودة", Color(0xFF3A1822), Color(0xFFF0868A), Color(0xFFF0868A))
                LegendChip("ثغر خارج السيطرة", Color(0xFF0D192E), Color(0xFF4E6288), Color(0xFF9FB0CC))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(
                        imageVector = Icons.Default.LocalFireDepartment,
                        contentDescription = null,
                        tint = Color(0xFFF7D774),
                        modifier = Modifier.size(12.dp),
                    )
                    Text("نفط مكتشف", color = Color(0xFFF7D774), fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive Vector Canvas (LTR coordinate frame for accurate geographic polygons)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(840f / 480f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(Color(0xFF102140), Color(0xFF071020)),
                            ),
                        )
                        .border(1.dp, AppColors.Gold.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                ) {
                    Canvas(
                        modifier = Modifier
                            .matchParentSize()
                            .pointerInput(activeKey) {
                                detectTapGestures { offset ->
                                    if (size.width > 0 && size.height > 0) {
                                        val nx = offset.x / size.width.toFloat()
                                        val ny = offset.y / size.height.toFloat()
                                        findSubProvinceAtPoint(nx, ny)?.let(onSelectSubKey)
                                    }
                                }
                            },
                    ) {
                        val w = size.width
                        val h = size.height

                        // Cartographic graticule grid lines
                        val gridStepX = w / 14f
                        val gridStepY = h / 8f
                        for (i in 1..13) {
                            val gx = gridStepX * i
                            drawLine(
                                color = AppColors.Gold.copy(alpha = 0.06f),
                                start = Offset(gx, 0f),
                                end = Offset(gx, h),
                                strokeWidth = 1f,
                            )
                        }
                        for (j in 1..7) {
                            val gy = gridStepY * j
                            drawLine(
                                color = AppColors.Gold.copy(alpha = 0.06f),
                                start = Offset(0f, gy),
                                end = Offset(w, gy),
                                strokeWidth = 1f,
                            )
                        }

                        // Sea water labels
                        val seaLabelStyle = TextStyle(
                            color = Color(0xFF7DD3FC).copy(alpha = 0.30f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textDirection = TextDirection.Rtl,
                        )
                        val seaLabels = listOf(
                            Triple("البحر الأسود", 0.56f, 0.08f),
                            Triple("البحر المتوسط", 0.24f, 0.32f),
                            Triple("البحر الأحمر", 0.57f, 0.87f),
                            Triple("الخليج", 0.89f, 0.72f),
                        )
                        for ((txt, rx, ry) in seaLabels) {
                            val m = textMeasurer.measure(txt, seaLabelStyle)
                            drawText(
                                textLayoutResult = m,
                                topLeft = Offset(rx * w - m.size.width / 2f, ry * h - m.size.height / 2f),
                            )
                        }

                        // Draw the 25 sub-provinces
                        for (geom in subProvinceMapGeometries) {
                            val spDef = subProvinceDef(geom.key) ?: continue
                            val parentInfo = resolveRegionStatus(state, spDef.parentKey)
                            val vis = mapVisualByStatus[parentInfo.status] ?: mapVisualByStatus.getValue(ProvinceState.LOYAL)
                            val isSelected = activeKey == geom.key
                            val res = effectiveSubProvinceResources(spDef, year, state.econ.oilDiscovered)
                            val hasOil = res.contains("oil")
                            val facs = state.econ.subProvinces[geom.key]?.factories ?: emptyList()
                            val facLevels = facs.sumOf { it.level }
                            val primaryRes = primaryResourceShort[res.firstOrNull() ?: "grain"] ?: ""

                            val path = Path().apply {
                                geom.polygon.forEachIndexed { idx, pt ->
                                    val px = pt.x * w
                                    val py = pt.y * h
                                    if (idx == 0) moveTo(px, py) else lineTo(px, py)
                                }
                                close()
                            }

                            drawPath(
                                path = path,
                                color = vis.fill.copy(
                                    alpha = when {
                                        parentInfo.status == "locked" -> 0.65f
                                        isSelected -> 0.98f
                                        else -> 0.88f
                                    },
                                ),
                            )

                            val strokeEffect = if (parentInfo.status == "locked" && !isSelected) {
                                PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                            } else {
                                null
                            }
                            drawPath(
                                path = path,
                                color = if (isSelected) Color(0xFFF7D774) else vis.stroke,
                                style = Stroke(
                                    width = if (isSelected) 2.5.dp.toPx() else 1.2.dp.toPx(),
                                    join = StrokeJoin.Round,
                                    pathEffect = strokeEffect,
                                ),
                            )

                            // Region short Arabic name + micro status/oil/factory row
                            val cx = geom.labelPos.x * w
                            val cy = geom.labelPos.y * h
                            val titleStyle = TextStyle(
                                color = if (parentInfo.status == "locked") Color(0xFFB8C5DB) else Color(0xFFF4EFE6),
                                fontSize = if (geom.key == "crete_heraklion" || geom.key == "egypt_alexandria") 7.8.sp else 8.6.sp,
                                fontWeight = FontWeight.Bold,
                                textDirection = TextDirection.Rtl,
                            )
                            val titleLayout = textMeasurer.measure(geom.shortLabel, titleStyle)
                            drawText(
                                textLayoutResult = titleLayout,
                                topLeft = Offset(cx - titleLayout.size.width / 2f, cy - titleLayout.size.height + 1f),
                            )

                            val subLabel = if (parentInfo.status == "locked") {
                                "مقفل·$primaryRes"
                            } else {
                                (if (hasOil) "★نفط" else primaryRes) + if (facLevels > 0) "·م$facLevels" else ""
                            }
                            val subColor = when {
                                hasOil -> Color(0xFFF7D774)
                                facLevels > 0 -> Color(0xFF6FCF97)
                                else -> Color(0xFF9FB0CC)
                            }
                            val subStyle = TextStyle(
                                color = subColor,
                                fontSize = 7.2.sp,
                                fontWeight = FontWeight.SemiBold,
                                textDirection = TextDirection.Rtl,
                            )
                            val subLayout = textMeasurer.measure(subLabel, subStyle)
                            drawText(
                                textLayoutResult = subLayout,
                                topLeft = Offset(cx - subLayout.size.width / 2f, cy + 2f),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Horizontal Sub-Province Chip Bar (always 42dp+ height for reliable touch access)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                for (sp in subProvinceDefs) {
                    val isSel = activeKey == sp.key
                    val pInfo = resolveRegionStatus(state, sp.parentKey)
                    val fLvls = state.econ.subProvinces[sp.key]?.factories?.sumOf { it.level } ?: 0
                    val label = sp.name + if (fLvls > 0) " ($fLvls)" else ""
                    Surface(
                        modifier = Modifier
                            .heightIn(min = 38.dp)
                            .clickable { onSelectSubKey(sp.key) },
                        shape = RoundedCornerShape(10.dp),
                        color = when {
                            isSel -> AppColors.Gold
                            pInfo.status == "locked" -> AppColors.Navy.copy(alpha = 0.65f)
                            else -> AppColors.Navy
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSel) AppColors.Gold else AppColors.CardBorder,
                        ),
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color = when {
                                    isSel -> AppColors.DeepBg
                                    pInfo.status == "locked" -> AppColors.Muted
                                    else -> AppColors.White
                                },
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Selected Sub-Province Inspection & Factory Construction Panel
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = AppColors.Navy,
                border = BorderStroke(1.dp, AppColors.Gold.copy(alpha = 0.35f)),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Header row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Place,
                                    contentDescription = null,
                                    tint = AppColors.Gold,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = selDef.name,
                                    color = AppColors.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                )
                                Text(
                                    text = "(${selParentInfo.parentName})",
                                    color = AppColors.Muted,
                                    fontSize = 12.sp,
                                )
                            }
                            if (!selLocked) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "المزاج ${selParentInfo.mood.roundToInt()}/100 • الانفصال ${selParentInfo.autonomy.roundToInt()}% • خانات المصانع ${selFacs.size}/${selDef.slots}",
                                    color = if (selParentInfo.autonomy >= 70.0) AppColors.Bad else AppColors.Muted,
                                    fontSize = 11.5.sp,
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = selVisual.badgeBg,
                            border = BorderStroke(1.dp, selVisual.stroke.copy(alpha = 0.55f)),
                        ) {
                            Text(
                                text = selVisual.label,
                                color = selVisual.textColor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }

                    // Governance Quick Actions or Frontier Unlock Hints
                    if (selLocked && selFrontierDef != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = AppColors.Card.copy(alpha = 0.7f),
                            border = BorderStroke(1.dp, AppColors.CardBorder),
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = AppColors.Gold,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = "ثغر لم يُضم للسلطنة بعد",
                                        color = AppColors.Gold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = selFrontierDef.about,
                                    color = AppColors.Muted,
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val enemiesStr = selFrontierDef.conquerEnemies.joinToString(" أو ") { countryDef(it)?.name ?: it }
                                val reqStr = selFrontierDef.requiresRegion?.let { " — يتطلب ضم ${frontierRegionDef(it)?.name ?: it} أولا" } ?: ""
                                Text(
                                    text = "الأعداء المستهدفون (+60 نقاط حرب): $enemiesStr$reqStr",
                                    color = Color(0xFF7DD3FC),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    } else if (!selLost) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedButton(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp),
                                enabled = !noActions,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                onClick = { onRunAction { ProvinceRules.privileges(it, selDef.parentKey) } },
                            ) {
                                Text(
                                    text = "منح امتيازات (${formatMoney(costShare(state, ProvinceRules.PRIVILEGES_SHARE))})",
                                    fontSize = 11.sp,
                                )
                            }
                            OutlinedButton(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 42.dp),
                                enabled = !noActions,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                                onClick = { onRunAction { ProvinceRules.suppress(it, selDef.parentKey) } },
                            ) {
                                Text(
                                    text = "قمع التمرد (${formatMoney(costShare(state, ProvinceRules.SUPPRESS_SHARE))})",
                                    fontSize = 11.sp,
                                    color = AppColors.Bad,
                                )
                            }
                        }
                    }

                    // Historical Resources + Oil Discovery Status
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "الموارد التاريخية للسنجق",
                            color = AppColors.Gold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                        )
                        if (selDef.oilFromYear != null && !selResources.contains("oil")) {
                            Text(
                                text = "اكتشاف النفط متاح من ${selDef.oilFromYear}",
                                color = AppColors.Muted,
                                fontSize = 10.5.sp,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        for (rk in selResources) {
                            val isOil = rk == "oil"
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isOil) Color(0xFF2E240E) else AppColors.Card,
                                border = BorderStroke(1.dp, if (isOil) AppColors.Gold else AppColors.CardBorder),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (isOil) {
                                        Icon(
                                            imageVector = Icons.Default.LocalFireDepartment,
                                            contentDescription = null,
                                            tint = Color(0xFFF7D774),
                                            modifier = Modifier.size(12.dp),
                                        )
                                    }
                                    Text(
                                        text = (resourceLabels[rk] ?: rk) + if (isOil) " (مكتشف!)" else "",
                                        color = if (isOil) Color(0xFFF7D774) else AppColors.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }

                    // Efficiency Modifiers Card
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = AppColors.Card.copy(alpha = 0.75f),
                        border = BorderStroke(1.dp, AppColors.CardBorder),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Icon(
                                        imageVector = Icons.Default.TrendingUp,
                                        contentDescription = null,
                                        tint = AppColors.Gold,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = "معدلات الكفاءة وإنتاجية المصانع",
                                        color = AppColors.Gold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                    )
                                }
                                Text(
                                    text = "الإجمالي: ×${String.format(Locale.US, "%.2f", baseEfficiency)}",
                                    color = AppColors.Good,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.5.sp,
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                EfficiencyMiniTile("النظام الاقتصادي", "×${String.format(Locale.US, "%.2f", sys.yieldMult)}", AppColors.White, Modifier.weight(1f))
                                EfficiencyMiniTile("القراءة (${String.format(Locale.US, "%.1f", state.demog.literacy)}%)", "×${String.format(Locale.US, "%.2f", litMult)}", AppColors.Gold, Modifier.weight(1f))
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                EfficiencyMiniTile("طاقة الفحم", if (hasCoal) "×1.25 (نشطة)" else "×1.00", if (hasCoal) AppColors.Good else AppColors.Muted, Modifier.weight(1f))
                                EfficiencyMiniTile("المورد / القوى", "+30% · ×${String.format(Locale.US, "%.2f", powMult)}", AppColors.Good, Modifier.weight(1f))
                            }
                        }
                    }

                    // Existing Factories (Levels 1-3)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(
                                imageVector = Icons.Default.Construction,
                                contentDescription = null,
                                tint = AppColors.Gold,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "المصانع القائمة (${selFacs.size}/${selDef.slots})",
                                color = AppColors.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            )
                        }
                        Text(
                            text = if (sys.canStateBuild) "البناء/الترقية: 1 إجراء ديوان" else "السوق الحر: استثمار خاص",
                            color = AppColors.Muted,
                            fontSize = 10.5.sp,
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    if (selFacs.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            color = AppColors.Card.copy(alpha = 0.45f),
                            border = BorderStroke(1.dp, AppColors.CardBorder),
                        ) {
                            Text(
                                text = if (selLocked) {
                                    "ضم هذا الثغر أولا لفتح خانات المصانع والاستفادة من موارده."
                                } else {
                                    "لا توجد مصانع قائمة في هذا السنجق بعد."
                                },
                                color = AppColors.Muted,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (fac in selFacs) {
                                val fDef = factoryDef(fac.type) ?: continue
                                val annualYield = IndustryRules.annualFactoryYield(
                                    state.econ,
                                    state.budget,
                                    year,
                                    state.demog.literacy,
                                    selDef.key,
                                    fac,
                                    state.powers,
                                    state.unlockedTechs,
                                )
                                val upgradeCost = IndustryRules.factoryBuildCost(state, selDef.key, fac.type)
                                val refundAmount = costShare(state, fDef.costShare * 0.2 * fac.level)
                                val isMax = fac.level >= IndustryRules.MAX_FACTORY_LEVEL
                                val bonusMatch = fDef.bonusResources.any { selResources.contains(it) }

                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    color = AppColors.Card,
                                    border = BorderStroke(1.dp, AppColors.CardBorder),
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${fDef.name} — المستوى ${fac.level}/${IndustryRules.MAX_FACTORY_LEVEL}",
                                                    color = AppColors.White,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                )
                                                if (bonusMatch) {
                                                    Text(
                                                        text = "+30% توافق المورد المحلي",
                                                        color = AppColors.Good,
                                                        fontSize = 10.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "+${formatMoney(annualYield)}/سنة",
                                                color = AppColors.Good,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.5.sp,
                                            )
                                        }

                                        if (canManageFactories) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            FlowRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                            ) {
                                                if (sys.canStateBuild && !isMax) {
                                                    OutlinedButton(
                                                        enabled = !noActions,
                                                        modifier = Modifier.heightIn(min = 38.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        onClick = { onRunAction { IndustryRules.buildOrUpgradeFactory(it, selDef.key, fac.type) } },
                                                    ) {
                                                        Text("ترقية إلى م${fac.level + 1} (${formatMoney(upgradeCost)})", fontSize = 11.sp)
                                                    }
                                                }
                                                if (sys.canSubsidize) {
                                                    OutlinedButton(
                                                        modifier = Modifier.heightIn(min = 38.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        onClick = { onRunAction { IndustryRules.toggleSubsidy(it, selDef.key, fac.type) } },
                                                    ) {
                                                        Text(
                                                            text = if (fac.subsidized) "دعم مالي: مفعل" else "تفعيل الدعم المالي",
                                                            fontSize = 11.sp,
                                                            color = if (fac.subsidized) AppColors.Good else AppColors.Muted,
                                                        )
                                                    }
                                                }
                                                OutlinedButton(
                                                    modifier = Modifier.heightIn(min = 38.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    onClick = { onRunAction { IndustryRules.demolishFactory(it, selDef.key, fac.type) } },
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Delete,
                                                        contentDescription = null,
                                                        tint = AppColors.Bad,
                                                        modifier = Modifier.size(13.dp),
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "تفكيك (استرداد 20%: ${formatMoney(refundAmount)})",
                                                        fontSize = 11.sp,
                                                        color = AppColors.Bad,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Buildable Factory Slots
                    if (canManageFactories && selFacs.size < selDef.slots) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "بناء مصنع جديد في ${selDef.name}",
                                color = AppColors.Gold,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                            )
                            Text(
                                text = "خانات شاغرة: ${selDef.slots - selFacs.size}",
                                color = AppColors.Muted,
                                fontSize = 10.5.sp,
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        if (!sys.canStateBuild) {
                            Text(
                                text = "نظام السوق الحر يمنع البناء الحكومي المباشر؛ يستثمر التجار تلقائيا أو يمكنك تبديل النظام الاقتصادي من تبويب «الاقتصاد والدين» بالديوان.",
                                color = AppColors.Muted,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (fd in factoryDefs.filter { fd -> selFacs.none { it.type == fd.key } }) {
                                    val chk = IndustryRules.canBuildInSubProvince(state.econ, selDef.key, fd.key, year)
                                    val cost = IndustryRules.factoryBuildCost(state, selDef.key, fd.key)
                                    val bonusMatch = fd.bonusResources.any { selResources.contains(it) }
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(10.dp),
                                        color = AppColors.Card,
                                        border = BorderStroke(1.dp, AppColors.CardBorder),
                                    ) {
                                        Column(modifier = Modifier.padding(10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Text(fd.name, color = AppColors.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                                OutlinedButton(
                                                    enabled = chk.ok && !noActions,
                                                    modifier = Modifier.heightIn(min = 38.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    onClick = { onRunAction { IndustryRules.buildOrUpgradeFactory(it, selDef.key, fd.key) } },
                                                ) {
                                                    Text("بناء (${formatMoney(cost)})", fontSize = 11.sp)
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(
                                                text = if (!chk.ok) {
                                                    chk.reason
                                                } else {
                                                    (if (bonusMatch) "★ +30% مورد مناسب · " else "") + fd.shortDesc
                                                },
                                                color = if (chk.ok) AppColors.Muted else AppColors.Bad,
                                                fontSize = 10.5.sp,
                                                lineHeight = 15.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendChip(label: String, fill: Color, stroke: Color, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(fill)
                .border(1.dp, stroke, RoundedCornerShape(2.dp)),
        )
        Text(label, color = textColor, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EfficiencyMiniTile(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = AppColors.Navy,
        border = BorderStroke(1.dp, AppColors.CardBorder),
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(label, color = AppColors.Muted, fontSize = 10.sp)
            Text(value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}
