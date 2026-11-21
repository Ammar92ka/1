package com.ottoman.empire1800.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** ختم الطغراء العثماني (رسم متجهي خالص عبر Jetpack Compose Canvas). */
@Composable
fun TughraSeal(
    size: Dp = 40.dp,
    contentDescriptionText: String = "الطغراء الهمايونية",
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = contentDescriptionText },
    ) {
        val w = this.size.width
        val h = this.size.height
        fun x(v: Float) = v / 84f * w
        fun y(v: Float) = v / 84f * h
        fun s(v: Float) = v / 84f * minOf(w, h)

        val coreBrush = Brush.radialGradient(
            colors = listOf(Color(0xFF1D3563), Color(0xFF0B162C)),
            center = Offset(x(42f), y(38f)),
            radius = s(42f),
        )
        val goldBrush = Brush.linearGradient(
            colors = listOf(Color(0xFFF3DA82), Color(0xFFC9A227), Color(0xFF9A7816)),
            start = Offset(x(12f), y(10f)),
            end = Offset(x(72f), y(74f)),
        )
        val goldLight = Color(0xFFF3DA82)
        val goldMid = Color(0xFFC9A227)

        // Outer Seal Background & Concentric Rings
        drawCircle(brush = coreBrush, radius = s(39f), center = Offset(x(42f), y(42f)))
        drawCircle(
            brush = goldBrush,
            radius = s(39f),
            center = Offset(x(42f), y(42f)),
            style = Stroke(width = s(2.2f)),
        )
        drawCircle(
            color = goldMid.copy(alpha = 0.6f),
            radius = s(34.5f),
            center = Offset(x(42f), y(42f)),
            style = Stroke(
                width = s(1.2f),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(s(2.4f), s(2.4f)), 0f),
            ),
        )
        drawCircle(
            color = goldMid.copy(alpha = 0.3f),
            radius = s(31f),
            center = Offset(x(42f), y(42f)),
            style = Stroke(width = s(0.8f)),
        )

        // Three Vertical Tuğ Shafts & Zülfe Pennants
        val tug1 = Path().apply {
            moveTo(x(36f), y(53f))
            lineTo(x(36f), y(18.5f))
            cubicTo(x(36f), y(15.8f), x(39.2f), y(15.8f), x(40.2f), y(19.2f))
            cubicTo(x(41f), y(22.5f), x(38.6f), y(26.5f), x(36.2f), y(29f))
        }
        drawPath(tug1, brush = goldBrush, style = Stroke(width = s(2.1f), cap = StrokeCap.Round, join = StrokeJoin.Round))

        val tug2 = Path().apply {
            moveTo(x(43.5f), y(53f))
            lineTo(x(43.5f), y(15.5f))
            cubicTo(x(43.5f), y(12.8f), x(47f), y(12.8f), x(48f), y(16.5f))
            cubicTo(x(48.8f), y(19.8f), x(46.2f), y(24.5f), x(43.7f), y(27.5f))
        }
        drawPath(tug2, brush = goldBrush, style = Stroke(width = s(2.2f), cap = StrokeCap.Round, join = StrokeJoin.Round))

        val tug3 = Path().apply {
            moveTo(x(51f), y(53f))
            lineTo(x(51f), y(19.5f))
            cubicTo(x(51f), y(16.8f), x(54.4f), y(16.8f), x(55.2f), y(20.3f))
            cubicTo(x(56f), y(23.4f), x(53.5f), y(27.2f), x(51.2f), y(30f))
        }
        drawPath(tug3, brush = goldBrush, style = Stroke(width = s(2.0f), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // Beyze: Twin Nested Teardrop Loops (Left)
        val beyzeOuter = Path().apply {
            moveTo(x(35.5f), y(55.5f))
            cubicTo(x(22.5f), y(56.5f), x(15.5f), y(49f), x(16.5f), y(39.5f))
            cubicTo(x(17.4f), y(31.5f), x(25.5f), y(28.5f), x(32.5f), y(32.5f))
            cubicTo(x(37f), y(35f), x(37.5f), y(41.5f), x(33.2f), y(44.5f))
            cubicTo(x(29f), y(47.2f), x(23f), y(44.5f), x(23.2f), y(39.8f))
        }
        drawPath(beyzeOuter, brush = goldBrush, style = Stroke(width = s(2.1f), cap = StrokeCap.Round))

        val beyzeInner = Path().apply {
            moveTo(x(35.5f), y(51f))
            cubicTo(x(26.5f), y(51.8f), x(20.5f), y(46.8f), x(21.2f), y(40.5f))
            cubicTo(x(21.8f), y(35.5f), x(27.2f), y(33.8f), x(31.5f), y(36.2f))
        }
        drawPath(beyzeInner, color = goldLight.copy(alpha = 0.85f), style = Stroke(width = s(1.5f), cap = StrokeCap.Round))

        // Sere: Calligraphic Base & Kol Sweeps (Right)
        val sere1 = Path().apply {
            moveTo(x(27.5f), y(56.5f))
            cubicTo(x(35.5f), y(59.5f), x(45.5f), y(59.2f), x(55.5f), y(54.5f))
            cubicTo(x(63.5f), y(50.8f), x(68.5f), y(45f), x(69.5f), y(39.5f))
        }
        drawPath(sere1, brush = goldBrush, style = Stroke(width = s(2.3f), cap = StrokeCap.Round))

        val sere2 = Path().apply {
            moveTo(x(32f), y(51.5f))
            cubicTo(x(39.5f), y(54.5f), x(48.5f), y(53.8f), x(56.5f), y(49.5f))
            cubicTo(x(61.8f), y(46.8f), x(65.5f), y(42.5f), x(66.2f), y(37.5f))
        }
        drawPath(sere2, color = goldLight.copy(alpha = 0.9f), style = Stroke(width = s(1.7f), cap = StrokeCap.Round))

        val sere3 = Path().apply {
            moveTo(x(36f), y(46.5f))
            cubicTo(x(41.5f), y(48f), x(48f), y(47.5f), x(54f), y(44.2f))
            cubicTo(x(56.8f), y(42.6f), x(58.6f), y(40f), x(58f), y(37.5f))
            cubicTo(x(57.4f), y(35f), x(54.5f), y(34.5f), x(52.8f), y(36.2f))
        }
        drawPath(sere3, brush = goldBrush, style = Stroke(width = s(1.6f), cap = StrokeCap.Round))

        // Diacritic Dots
        drawCircle(color = goldMid, radius = s(1.4f), center = Offset(x(42f), y(63.5f)))
        drawCircle(color = goldMid, radius = s(1.2f), center = Offset(x(47.5f), y(62.5f)))
        drawCircle(color = goldMid, radius = s(1.2f), center = Offset(x(36.5f), y(62.5f)))
    }
}
