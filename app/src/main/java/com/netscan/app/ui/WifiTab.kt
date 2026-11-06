package com.netscan.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.netscan.app.ScanViewModel
import com.netscan.app.scan.WifiAp
import com.netscan.app.tools.SpeedTest

@Composable
fun WifiTab(vm: ScanViewModel) {
    val ctx = LocalContext.current
    val speed by vm.speed.collectAsState()
    val aps by vm.aps.collectAsState()
    val signal by vm.signalMap.collectAsState()

    LaunchedEffect(Unit) { vm.refreshAps(ctx) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 30.dp)
    ) {
        item { SpeedCard(speed, onRun = { vm.runSpeedTest() }) }
        item { SignalCard(signal, onSample = { vm.sampleSignal(ctx, it) }, onClear = { vm.clearSignalMap() }) }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Redes Wi-Fi cercanas", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.refreshAps(ctx) }) { Text("Actualizar") }
            }
        }
        if (aps.isEmpty()) {
            item {
                Text(
                    "No veo otras redes. Activa la ubicación del teléfono (Android la exige para esto) y vuelve a actualizar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        } else {
            aps.firstOrNull { it.isCurrent }?.let { cur ->
                if (cur.isInsecure) item { InsecureWarn(cur) }
            }
            item { ChannelAdvice(aps) }
            items(aps, key = { it.bssid }) { ap -> ApRow(ap) }
        }
    }
}

@Composable
private fun SpeedCard(state: ScanViewModel.SpeedState, onRun: () -> Unit) {
    Card(shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Velocidad de internet", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (!state.running) {
                    Button(onClick = onRun) { Text(if (state.result == null) "Probar" else "Repetir") }
                }
            }
            Spacer(Modifier.height(10.dp))
            when {
                state.running -> {
                    val label = when (state.phase) {
                        SpeedTest.Phase.PING -> "Midiendo latencia…"
                        SpeedTest.Phase.DOWNLOAD -> "Descarga…"
                        SpeedTest.Phase.UPLOAD -> "Subida…"
                        else -> "Preparando…"
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                            state.liveMbps?.let {
                                Text("%.1f Mbps".format(it), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                state.result != null -> {
                    val r = state.result
                    if (r.error != null) {
                        Text("No se pudo medir: ${r.error}", style = MaterialTheme.typography.bodySmall)
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Metric("Descarga", r.downMbps, "Mbps", Icons.Default.Download)
                            Metric("Subida", r.upMbps, "Mbps", Icons.Default.Upload)
                            Metric("Latencia", r.pingMs, "ms", Icons.Default.Timer)
                        }
                    }
                }
                else -> Text(
                    "Mide tu velocidad real de bajada, subida y latencia. Usa unos 15 MB de datos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: Double?, unit: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(value?.let { if (it >= 100) "%.0f".format(it) else "%.1f".format(it) } ?: "-",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("$unit · $label", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
private fun SignalCard(
    samples: List<ScanViewModel.SignalSample>,
    onSample: (String) -> Unit,
    onClear: () -> Unit
) {
    var room by remember { mutableStateOf("") }
    Card(shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NetworkCheck, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Mapa de señal", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (samples.isNotEmpty()) TextButton(onClick = onClear) { Text("Borrar") }
            }
            Text(
                "Camina por la casa: escribe el cuarto y toca «Medir aquí» para registrar la señal en ese punto.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = room, onValueChange = { room = it.take(24) },
                    placeholder = { Text("Cuarto") }, singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSample(room.ifBlank { "Punto ${samples.size + 1}" }); room = "" }) {
                    Text("Medir aquí")
                }
            }
            samples.forEach { s ->
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.room, modifier = Modifier.width(100.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall)
                    QualityBar(s.quality, Modifier.weight(1f))
                    Text("  ${s.rssi} dBm", style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun InsecureWarn(ap: WifiAp) {
    Surface(shape = RoundedCornerShape(10.dp),
        color = Color(0xFFE57373).copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.LockOpen, null, tint = Color(0xFFE57373))
            Spacer(Modifier.width(10.dp))
            Text(
                "Estás conectado a una red ${ap.security.lowercase()}. El tráfico puede ir sin cifrar; evita banca o contraseñas y, si es tuya, ponle WPA2/WPA3.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ChannelAdvice(aps: List<WifiAp>) {
    // En 2.4 GHz solo 1, 6 y 11 no se pisan. Recomendamos el menos usado.
    val band24 = aps.filter { it.band == "2.4 GHz" }
    if (band24.size < 2) return
    val counts = listOf(1, 6, 11).associateWith { ch ->
        band24.count { kotlin.math.abs(it.channel - ch) <= 2 }
    }
    val best = counts.minByOrNull { it.value }?.key ?: return
    Surface(shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lightbulb, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(
                "En 2.4 GHz el canal menos saturado es el $best. Si tu Wi-Fi va lento, cámbialo en el router.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun ApRow(ap: WifiAp) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ap.isCurrent) Icons.Default.Wifi else Icons.Default.WifiTethering,
            null, modifier = Modifier.size(20.dp),
            tint = if (ap.isCurrent) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ap.ssid, fontWeight = if (ap.isCurrent) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium)
                if (ap.isCurrent) { Spacer(Modifier.width(6.dp)); Badge("conectado") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${ap.band} · canal ${ap.channel} · ", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Icon(
                    if (ap.isInsecure) Icons.Default.LockOpen else Icons.Default.Lock,
                    null, modifier = Modifier.size(11.dp),
                    tint = if (ap.isInsecure) Color(0xFFE57373)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(" ${ap.security}", style = MaterialTheme.typography.labelSmall,
                    color = if (ap.isInsecure) Color(0xFFE57373)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
        QualityBar(ap.quality, Modifier.width(70.dp))
        Text(if (ap.hasSignal) "  ${ap.rssi}" else "  —",
            style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun QualityBar(quality: Int, modifier: Modifier = Modifier) {
    // quality < 0 = sin lectura de señal: barra gris vacía.
    val color = when {
        quality < 0 -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        quality >= 66 -> Color(0xFF66BB6A)
        quality >= 33 -> Color(0xFFFFCA28)
        else -> Color(0xFFE57373)
    }
    val fill = if (quality < 0) 0f else quality / 100f
    Box(
        modifier
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(fill)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
    }
}
