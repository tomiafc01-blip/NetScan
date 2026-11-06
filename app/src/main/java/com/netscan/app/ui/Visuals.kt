package com.netscan.app.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.netscan.app.scan.Confidence
import com.netscan.app.scan.Device
import com.netscan.app.scan.WifiAp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Radar animado: puro adorno. Barre en círculo y dibuja un punto por
 * dispositivo encontrado. Más rápido mientras escanea.
 */
@Composable
fun ScanRadar(deviceCount: Int, running: Boolean, modifier: Modifier = Modifier) {
    val accent = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "radar")
    val sweep by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(if (running) 1400 else 4200, easing = LinearEasing)
        ), label = "sweep"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    Canvas(modifier) {
        val r = min(size.width, size.height) / 2f * 0.92f
        val c = Offset(size.width / 2f, size.height / 2f)
        val grid = accent.copy(alpha = 0.18f)

        // Anillos concéntricos + ejes.
        for (i in 1..3) drawCircle(grid, r * i / 3f, c, style = Stroke(width = 2f))
        drawLine(grid, Offset(c.x - r, c.y), Offset(c.x + r, c.y), 2f)
        drawLine(grid, Offset(c.x, c.y - r), Offset(c.x, c.y + r), 2f)

        // Barrido con estela.
        rotate(sweep, c) {
            drawArc(
                brush = Brush.sweepGradient(
                    0f to Color.Transparent,
                    0.85f to accent.copy(alpha = 0.03f),
                    1f to accent.copy(alpha = 0.33f),
                    center = c
                ),
                startAngle = 0f, sweepAngle = 60f, useCenter = true,
                topLeft = Offset(c.x - r, c.y - r),
                size = androidx.compose.ui.geometry.Size(r * 2, r * 2)
            )
            drawLine(accent.copy(alpha = 0.7f), c, Offset(c.x + r, c.y), 3f)
        }

        // Un punto por dispositivo, repartido con el ángulo áureo.
        val n = deviceCount.coerceAtMost(60)
        for (i in 0 until n) {
            val ang = (i * 137.5) * PI / 180.0
            val rad = r * (0.30f + 0.62f * ((i % 7) / 7f))
            val p = Offset(c.x + (rad * cos(ang)).toFloat(), c.y + (rad * sin(ang)).toFloat())
            drawCircle(accent.copy(alpha = 0.25f), 7f * pulse, p)
            drawCircle(accent, 3.5f, p)
        }
    }
}

/**
 * Barra de composición: segmentos por nivel de confianza (verde/amarillo/gris).
 * Las anchuras se animan al cambiar los conteos. Solo decorativa/analítica.
 */
@Composable
fun CompositionBar(devices: List<Device>, modifier: Modifier = Modifier) {
    val total = devices.size.coerceAtLeast(1)
    val high = devices.count { it.confidence() == Confidence.HIGH }
    val med = devices.count { it.confidence() == Confidence.MEDIUM }
    val low = devices.size - high - med

    val hF by animateFloatAsState(high / total.toFloat(), tween(600), label = "h")
    val mF by animateFloatAsState(med / total.toFloat(), tween(600), label = "m")
    val lF by animateFloatAsState(low / total.toFloat(), tween(600), label = "l")

    Row(
        modifier
            .clip(RoundedCornerShape(5.dp))
            .height(8.dp)
    ) {
        if (hF > 0f) Box(Modifier.weight(hF.coerceAtLeast(0.0001f)).fillMaxHeight().background(Color(0xFF66BB6A)))
        if (mF > 0f) Box(Modifier.weight(mF.coerceAtLeast(0.0001f)).fillMaxHeight().background(Color(0xFFFFCA28)))
        if (lF > 0f) Box(Modifier.weight(lF.coerceAtLeast(0.0001f)).fillMaxHeight().background(Color(0xFF9E9E9E)))
    }
}

/**
 * Espectro de ocupación de canales 2.4 GHz. Barras animadas; adorno analítico.
 */
@Composable
fun ChannelSpectrum(aps: List<WifiAp>, modifier: Modifier = Modifier) {
    val accent = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val band24 = aps.filter { it.band == "2.4 GHz" }
    val occupancy = (1..13).map { ch ->
        band24.count { kotlin.math.abs(it.channel - ch) <= 2 }
    }
    val maxOcc = (occupancy.maxOrNull() ?: 0).coerceAtLeast(1)
    val anim by animateFloatAsState(if (band24.isEmpty()) 0f else 1f, tween(700), label = "spec")

    Canvas(modifier) {
        val n = occupancy.size
        val gap = size.width * 0.02f
        val bw = (size.width - gap * (n - 1)) / n
        val base = size.height
        occupancy.forEachIndexed { i, occ ->
            val h = base * (occ.toFloat() / maxOcc) * anim
            val x = i * (bw + gap)
            // Pista de fondo.
            drawRoundRect(
                color = accent.copy(alpha = 0.10f),
                topLeft = Offset(x, 0f),
                size = androidx.compose.ui.geometry.Size(bw, base),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
            )
            if (h > 0f) drawRoundRect(
                brush = Brush.verticalGradient(
                    0f to accent, 1f to accent.copy(alpha = 0.55f),
                    startY = base - h, endY = base
                ),
                topLeft = Offset(x, base - h),
                size = androidx.compose.ui.geometry.Size(bw, h),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
            )
        }
    }
}
