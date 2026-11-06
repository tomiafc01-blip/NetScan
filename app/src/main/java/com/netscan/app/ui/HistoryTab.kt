package com.netscan.app.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.netscan.app.ScanViewModel
import com.netscan.app.data.KnownDevice
import com.netscan.app.data.ScanRecord
import java.text.DateFormat
import java.util.Date

/** Pestaña Historial: vigilancia, aparatos conocidos y escaneos anteriores. */
@Composable
fun HistoryTab(vm: ScanViewModel) {
    val ctx = LocalContext.current
    val known by vm.known.collectAsState()
    val records by vm.records.collectAsState()
    val events by vm.events.collectAsState()
    val watch by vm.watch.collectAsState()
    val network by vm.network.collectAsState()
    var forgetting by remember { mutableStateOf<KnownDevice?>(null) }

    // Para avisar con notificación hace falta el permiso en Android 13+.
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { vm.setWatch(ctx, true) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 24.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Vigilar mi red", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Escanea cada 30 min con Wi-Fi y te avisa si se conecta un aparato desconocido.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    Switch(
                        checked = watch,
                        onCheckedChange = { on ->
                            if (on && Build.VERSION.SDK_INT >= 33) {
                                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                vm.setWatch(ctx, on)
                            }
                        }
                    )
                }
            }
        }

        if (events.isNotEmpty()) {
            item { SectionTitle("Novedades · aparatos nuevos") }
            items(events.take(15), key = { "e${it.time}${it.ip}" }) { e -> EventRow(e) }
        }

        item {
            SectionTitle(
                if (network != null) "Dispositivos conocidos en $network (${known.size})"
                else "Dispositivos conocidos"
            )
        }
        if (known.isEmpty()) {
            item {
                Text(
                    "Aún no hay historial. Haz un escaneo y aquí aparecerán todos los aparatos que se hayan conectado.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
        items(known, key = { it.key }) { k ->
            KnownRow(k, onForget = { forgetting = k })
        }

        if (records.isNotEmpty()) {
            item { SectionTitle("Escaneos recientes") }
            items(records.take(20), key = { "r${it.time}" }) { r -> RecordRow(r) }
        }
    }

    forgetting?.let { k ->
        AlertDialog(
            onDismissRequest = { forgetting = null },
            title = { Text("¿Olvidar «${k.displayName()}»?") },
            text = { Text("Se borra su nombre e historial. Si vuelve a conectarse aparecerá como nuevo.") },
            confirmButton = {
                TextButton(onClick = { vm.forget(k.key); forgetting = null }) { Text("Olvidar") }
            },
            dismissButton = { TextButton(onClick = { forgetting = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun KnownRow(k: KnownDevice, onForget: () -> Unit) {
    val df = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(iconForType(k.type), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(k.displayName(), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (k.trusted) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Verified, "de confianza",
                            tint = androidx.compose.ui.graphics.Color(0xFF66BB6A), modifier = Modifier.size(14.dp))
                    }
                }
                Text(
                    "Última vez: ${df.format(Date(k.lastSeen))} · visto ${k.timesSeen}×",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Text(
                    "Desde ${df.format(Date(k.firstSeen))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(k.ip, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                IconButton(onClick = onForget, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Olvidar", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun EventRow(e: com.netscan.app.data.DeviceEvent) {
    val rel = android.text.format.DateUtils.getRelativeTimeSpanString(
        e.time, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS
    )
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.NewReleases, null, modifier = Modifier.size(16.dp), tint = androidx.compose.ui.graphics.Color(0xFFFF9800))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(e.label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$rel · ${e.ip}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun RecordRow(r: ScanRecord) {
    val df = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Radar, null, modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(df.format(Date(r.time)), style = MaterialTheme.typography.bodySmall)
            Text(r.network, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        Text("${r.total} disp.", style = MaterialTheme.typography.bodySmall)
        if (r.newCount > 0) {
            Spacer(Modifier.width(6.dp))
            Badge("+${r.newCount} nuevos", androidx.compose.ui.graphics.Color(0xFFFF9800))
        }
    }
}
