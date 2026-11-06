package com.netscan.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.netscan.app.ui.theme.Brand

/** Clic con rebote: escala suave al presionar. Da sensación táctil premium. */
@Composable
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, tween(120), label = "press")
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interaction,
            indication = ripple(),
            enabled = enabled,
            onClick = onClick
        )
}

/** Número que se anima al cambiar. */
@Composable
fun AnimatedCount(
    value: Int,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.headlineLarge,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    val v by animateIntAsState(value, tween(650), label = "count")
    Text("$v", modifier = modifier, style = style, color = color, fontWeight = FontWeight.Bold)
}

/** Botón flotante con degradado de marca. */
@Composable
fun GradientFab(scanning: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Brand.gradient)
            .pressable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 15.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (scanning) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF042029))
            } else {
                Icon(Icons.Filled.Radar, null, tint = Color(0xFF042029), modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(if (scanning) "Escaneando…" else "Escanear",
                color = Color(0xFF042029), fontWeight = FontWeight.Bold)
        }
    }
}

/** Encabezado tipo hero con degradado, datos de la red y conteo animado. */
@Composable
fun HeroHeader(
    ssid: String?,
    ip: String?,
    phase: String,
    deviceCount: Int,
    riskCount: Int,
    untrusted: Int,
    onScanSubnet: () -> Unit,
    composition: @Composable () -> Unit
) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .background(Brand.gradientSoft)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Wifi, null, tint = Brand.cyan, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(ssid ?: "Red Wi-Fi", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    ip?.let {
                        Text("Tu IP: $it", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (phase.isNotEmpty()) {
                        Text(phase, style = MaterialTheme.typography.bodySmall, color = Brand.cyan)
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedCount(deviceCount, color = Brand.cyan)
                    Text("dispositivos", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .pressable(onClick = onScanSubnet),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Lan, "Escanear otra subred",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            if (deviceCount > 0) {
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatChip("$deviceCount", "equipos", Brand.cyan)
                    if (riskCount > 0) { Spacer(Modifier.width(8.dp)); StatChip("$riskCount", "con riesgo", Brand.bad) }
                    if (untrusted > 0) { Spacer(Modifier.width(8.dp)); StatChip("$untrusted", "sin confiar", Brand.muted) }
                }
                Spacer(Modifier.width(2.dp))
                Box(Modifier.padding(top = 10.dp)) { composition() }
            }
        }
    }
}

@Composable
private fun StatChip(value: String, label: String, color: Color) {
    Row(
        Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(value, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
