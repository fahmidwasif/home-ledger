package nz.afhome.ledger.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sqrt

/**
 * The loading animation: the three Deathly Hallows draw themselves in turn:
 * the Cloak of Invisibility (triangle), the Resurrection Stone (circle), then the Elder Wand (line),
 * glow for a moment, and start again.
 */
@Composable
fun HallowsLoader(size: Dp = 48.dp, color: Color = MaterialTheme.colorScheme.secondary) {
    val t by rememberInfiniteTransition(label = "hallows").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    val measure = remember { PathMeasure() }
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val side = minOf(w, h / (sqrt(3f) / 2f)) * 0.92f
        val triH = side * sqrt(3f) / 2f
        val top = (h - triH) / 2f
        val base = top + triH
        val cx = w / 2f
        val apex = Offset(cx, top)
        val left = Offset(cx - side / 2f, base)
        val right = Offset(cx + side / 2f, base)
        val r = side / (2f * sqrt(3f))                // incircle radius
        val centre = Offset(cx, base - r)
        val stroke = Stroke(width = w * 0.05f, cap = StrokeCap.Round, join = StrokeJoin.Round)

        fun phase(from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)
        val cloak = phase(0f, 0.35f)
        val stone = phase(0.35f, 0.6f)
        val wand = phase(0.6f, 0.78f)
        // Glow while complete, then fade out before the loop restarts.
        val alpha = when {
            t < 0.78f -> 1f
            t < 0.9f -> 1f
            else -> 1f - (t - 0.9f) / 0.1f
        }
        val glow = if (t in 0.78f..0.9f) 0.25f + 0.25f * kotlin.math.sin((t - 0.78f) / 0.12f * Math.PI.toFloat()) else 0f

        // Soft glow behind the finished symbol.
        if (glow > 0f) drawCircle(color.copy(alpha = glow * 0.6f), radius = minOf(w, h) / 2f, center = Offset(w / 2f, h / 2f))

        // Cloak: the triangle, traced from the apex.
        val tri = Path().apply { moveTo(apex.x, apex.y); lineTo(right.x, right.y); lineTo(left.x, left.y); close() }
        measure.setPath(tri, false)
        val part = Path()
        measure.getSegment(0f, measure.length * cloak, part, true)
        drawPath(part, color.copy(alpha = alpha), style = stroke)

        // Stone: the circle.
        if (stone > 0f) drawArc(
            color.copy(alpha = alpha), startAngle = -90f, sweepAngle = 360f * stone, useCenter = false,
            topLeft = Offset(centre.x - r, centre.y - r), size = Size(2 * r, 2 * r), style = stroke,
        )

        // Wand: the line from the apex down.
        if (wand > 0f) drawLine(
            color.copy(alpha = alpha), apex, Offset(cx, top + triH * wand),
            strokeWidth = stroke.width, cap = StrokeCap.Round,
        )
    }
}

/** Loader with a line of text beside it. */
@Composable
fun MagicLoading(text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        HallowsLoader(40.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
