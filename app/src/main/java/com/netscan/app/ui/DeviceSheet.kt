package com.netscan.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.netscan.app.ScanViewModel
import com.netscan.app.scan.Confidence
import com.netscan.app.scan.Device
import java.text.DateFormat
import java.util.Date

/** Hoja inferior con todo lo que sabemos de un dispositivo y sus acciones. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DeviceSheet(device: Device, rev: Int, vm: ScanViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val ping by vm.ping.collectAsState()
    val portScan by vm.portScan.collectAsState()
    var renaming by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        key(rev) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
            ) {
                // --- Cabecera ---
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        iconFor(device), null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            device.bestLabel(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        val sub = device.subLabel()
                        if (sub.isNotEmpty()) {
                            Text(
                                sub,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    IconButton(onClick = { renaming = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Renombrar")
                    }
                }

                Row(
                    Modifier.padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val c = device.confidence()
                    ConfidenceDot(c)
                    Text(
                        "  Confianza ${c.label.lowercase()} · ${confidenceHint(c)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                Row {
                    if (device.isNew) Badge("NUEVO", androidx.compose.ui.graphics.Color(0xFFFF9800))
                    if (device.isGateway) { Spacer(Modifier.width(4.dp)); Badge("router") }
                    if (device.isThisDevice) { Spacer(Modifier.width(4.dp)); Badge("este equipo") }
                }

                // --- Confianza ---
                if (!device.isThisDevice) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (device.trusted) Icons.Default.Verified else Icons.Default.ShieldMoon,
                            null,
                            tint = if (device.trusted) androidx.compose.ui.graphics.Color(0xFF66BB6A)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Aparato de confianza", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Los de confianza no generan alertas de «nuevo».",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Switch(checked = device.trusted, onCheckedChange = { vm.setTrusted(device, it) })
                    }
                }

                // --- Seguridad ---
                if (device.risks.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Seguridad", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    device.risks.forEach { r -> RiskCard(r, onOpenWeb = { openUrl(ctx, device.webUrl ?: "http://${device.ip}/") }) }
                }

                // --- Acciones ---
                Spacer(Modifier.height(14.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ActionChip(Icons.Default.Speed, "Ping") { vm.runPing(device.ip) }
                    ActionChip(Icons.Default.Scanner, "Escanear puertos") { vm.runPortScan(device.ip) }
                    if (device.mac != null && !device.isThisDevice) {
                        ActionChip(Icons.Default.PowerSettingsNew, "Encender (WOL)") { vm.wake(device) }
                    }
                    device.webUrl?.let { url ->
                        ActionChip(Icons.Default.OpenInBrowser, "Abrir web") { openUrl(ctx, url) }
                    }
                    ActionChip(Icons.Default.ContentCopy, "Copiar IP") { copy(ctx, device.ip) }
                    device.mac?.let { mac ->
                        ActionChip(Icons.Default.ContentCopy, "Copiar MAC") { copy(ctx, mac) }
                    }
                }

                // --- Resultado del ping ---
                val p = ping
                if (p != null && p.first == device.ip) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            val r = p.second
                            if (r == null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text("  Midiendo latencia (10 paquetes)…", style = MaterialTheme.typography.bodySmall)
                                }
                            } else if (r.received == 0) {
                                Text(
                                    "Sin respuesta (100 % perdidos). Puede que bloquee el ping.",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            } else {
                                Text(
                                    "Promedio ${fmt(r.avgMs)} ms",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "Mín ${fmt(r.minMs)} · Máx ${fmt(r.maxMs)} · Perdidos ${r.lossPercent} % (${r.received}/${r.sent})",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                Text(
                                    latencyVerdict(r.avgMs ?: 0.0, r.lossPercent),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // --- Resultado del escaneo de puertos ---
                val ps = portScan
                if (ps != null && ps.ip == device.ip) {
                    Spacer(Modifier.height(10.dp))
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            val open = ps.open
                            if (open == null) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                                    val pct = if (ps.total > 0) ps.done * 100 / ps.total else 0
                                    Text("  Escaneando puertos 1–1024… $pct %", style = MaterialTheme.typography.bodySmall)
                                }
                            } else if (open.isEmpty()) {
                                Text("No se encontraron puertos abiertos (1–1024).", style = MaterialTheme.typography.bodySmall)
                            } else {
                                Text("Puertos abiertos (1–1024):", style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text(
                                    open.joinToString(", ") { p -> com.netscan.app.tools.PortScanner.names[p]?.let { "$p ($it)" } ?: "$p" },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // --- Detalle ---
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(Modifier.height(8.dp))
                device.customName?.let { DetailRow("Tu nombre", it) }
                DetailRow("IP", device.ip, mono = true)
                device.mac?.let {
                    DetailRow("MAC", it + if (Device.isRandomMac(it)) " (aleatoria)" else "", mono = true)
                }
                device.vendor?.let { DetailRow("Fabricante", it) }
                device.model?.let { DetailRow("Modelo", it) }
                device.hostname?.let { DetailRow("Nombre de red", it) }
                device.deviceType?.let { DetailRow("Tipo", it) }
                device.latencyMs?.let { DetailRow("Respuesta", "$it ms") }
                if (device.openPorts.isNotEmpty()) {
                    DetailRow("Puertos abiertos", device.openPorts.joinToString(", ") { portName(it) })
                }
                if (device.services.isNotEmpty()) {
                    DetailRow("Servicios", device.services.joinToString(", "))
                }
                device.webUrl?.let { DetailRow("Web", it, mono = true) }
                device.firstSeen?.let {
                    DetailRow("Visto por 1.ª vez", DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)))
                }
                if (device.sources.isNotEmpty()) {
                    DetailRow("Detectado por", device.sources.joinToString(", "))
                }

                // --- Actividad ---
                val seen = remember(device.ip, rev) { vm.activity(device) }
                if (seen.size >= 2) {
                    Spacer(Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(Modifier.height(8.dp))
                    Text("Actividad", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Detectado en ${seen.size} escaneos. Últimas conexiones:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    seen.sortedDescending().take(6).forEach { t ->
                        val rel = android.text.format.DateUtils.getRelativeTimeSpanString(
                            t, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS
                        )
                        Text("· $rel", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (renaming) {
        RenameDialog(
            current = device.customName ?: "",
            suggestion = device.autoLabel(),
            onSave = { vm.rename(device, it); renaming = false },
            onDismiss = { renaming = false }
        )
    }
}

@Composable
private fun RenameDialog(
    current: String,
    suggestion: String,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nombre del dispositivo") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(40) },
                    singleLine = true,
                    placeholder = { Text(suggestion) },
                    label = { Text("Por ejemplo: TV sala") }
                )
                Text(
                    "Déjalo vacío para usar el nombre automático.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.ifBlank { null }) }) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun RiskCard(r: com.netscan.app.scan.Risk, onOpenWeb: () -> Unit) {
    val color = riskColor(r.level)
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
    ) {
        Row(Modifier.padding(12.dp)) {
            Icon(Icons.Default.Warning, null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(r.detail, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
                if (r.title.contains("router", ignoreCase = true)) {
                    TextButton(onClick = onOpenWeb, contentPadding = PaddingValues(0.dp)) {
                        Text("Abrir panel del router")
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) }
    )
}

@Composable
internal fun DetailRow(label: String, value: String, mono: Boolean = false) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default
        )
    }
}

private fun confidenceHint(c: Confidence) = when (c) {
    Confidence.HIGH -> "el aparato publica su modelo"
    Confidence.MEDIUM -> "nombre o fabricante real"
    Confidence.LOW -> "suposición por puertos"
}

private fun fmt(v: Double?): String = v?.let { if (it < 10) "%.1f".format(it) else "%.0f".format(it) } ?: "-"

private fun latencyVerdict(avg: Double, loss: Int): String = when {
    loss >= 30 -> "Conexión inestable: se pierden muchos paquetes."
    avg < 10 -> "Excelente: respuesta casi instantánea."
    avg < 40 -> "Buena conexión."
    avg < 100 -> "Algo lenta: puede estar lejos del router o en ahorro de energía."
    else -> "Lenta: posible interferencia o el aparato está dormido."
}

/** Nombre corto de puertos conocidos para que se entiendan. */
private fun portName(p: Int): String = when (p) {
    22 -> "22 SSH"; 23 -> "23 Telnet"; 53 -> "53 DNS"; 80 -> "80 HTTP"; 443 -> "443 HTTPS"
    139, 445 -> "$p SMB"; 515 -> "515 LPD"; 631 -> "631 IPP"; 9100 -> "9100 Impresión"
    548 -> "548 AFP"; 554, 8554 -> "$p RTSP"; 1400 -> "1400 Sonos"; 1883, 8883 -> "$p MQTT"
    3389 -> "3389 Escritorio remoto"; 5555 -> "5555 ADB"; 7000 -> "7000 AirPlay"
    8009 -> "8009 Cast"; 8080, 8443 -> "$p Web"; 32469 -> "32469 Plex"; 62078 -> "62078 iOS"
    else -> "$p"
}

private fun copy(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("NetScan", text))
    Toast.makeText(ctx, "Copiado: $text", Toast.LENGTH_SHORT).show()
}

private fun openUrl(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        Toast.makeText(ctx, "No hay navegador para abrir $url", Toast.LENGTH_SHORT).show()
    }
}
