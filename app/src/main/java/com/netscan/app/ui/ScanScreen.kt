package com.netscan.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.netscan.app.ScanViewModel
import com.netscan.app.scan.Confidence
import com.netscan.app.scan.Device

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(vm: ScanViewModel) {
    val state by vm.state.collectAsState()
    val toast by vm.toast.collectAsState()
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedIp by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toast) {
        toast?.let {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            vm.toastShown()
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("NetScan", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { vm.export(ctx) }) {
                            Icon(Icons.Default.Share, contentDescription = "Exportar escaneo")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                    Tab(selected = tab == 0, onClick = { tab = 0 },
                        text = { Text("Red") }, icon = { Icon(Icons.Default.Radar, null) })
                    Tab(selected = tab == 1, onClick = { tab = 1; vm.refreshHistory() },
                        text = { Text("Historial") }, icon = { Icon(Icons.Default.History, null) })
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                ExtendedFloatingActionButton(
                    onClick = { vm.startScan() },
                    icon = {
                        if (state.running) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Radar, contentDescription = null)
                        }
                    },
                    text = { Text(if (state.running) "Escaneando…" else "Escanear") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            if (tab == 0) {
                NetworkTab(vm, state.rev, onSelect = { selectedIp = it.ip })
            } else {
                HistoryTab(vm)
            }
        }
    }

    // Hoja de detalle del dispositivo seleccionado.
    val selected = selectedIp?.let { ip -> state.devices.firstOrNull { it.ip == ip } }
    if (selected != null) {
        DeviceSheet(
            device = selected,
            rev = state.rev,
            vm = vm,
            onDismiss = { selectedIp = null; vm.clearPing() }
        )
    }
}

@Composable
private fun NetworkTab(vm: ScanViewModel, rev: Int, onSelect: (Device) -> Unit) {
    val state by vm.state.collectAsState()
    val update by vm.update.collectAsState()
    val updatePhase by vm.updatePhase.collectAsState()
    val checkStatus by vm.checkStatus.collectAsState()
    val newDevices by vm.newDevices.collectAsState()
    val ctx = LocalContext.current

    Column(Modifier.fillMaxSize()) {
        NetHeader(state.net?.ssid, state.net?.localIp, state.phase, state.devices)

        VersionRow(
            version = vm.currentVersion,
            status = checkStatus,
            onCheck = { vm.checkForUpdate(manual = true) }
        )

        update?.let { rel ->
            UpdateBanner(
                versionName = rel.versionName,
                phase = updatePhase,
                onUpdate = { vm.runUpdate(ctx) },
                onDismiss = { vm.dismissUpdate() }
            )
        }

        if (newDevices.isNotEmpty()) {
            NewDevicesBanner(newDevices, onDismiss = { vm.dismissNew() })
        }

        if (state.devices.isEmpty() && !state.running) {
            EmptyHint()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(state.devices, key = { it.ip }) { d ->
                    // rev obliga a redibujar cuando cambia un nombre.
                    key(rev) { DeviceCard(d, onClick = { onSelect(d) }) }
                }
            }
        }
    }
}

@Composable
private fun NetHeader(ssid: String?, ip: String?, phase: String, devices: List<Device>) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Wifi, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(
                ssid ?: "Red Wi-Fi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (ip != null) {
            Text(
                "Tu IP: $ip",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        if (phase.isNotEmpty()) {
            Text(
                phase,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (devices.isNotEmpty()) {
            val high = devices.count { it.confidence() == Confidence.HIGH }
            val med = devices.count { it.confidence() == Confidence.MEDIUM }
            val low = devices.size - high - med
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                ConfidenceDot(Confidence.HIGH); Text(" $high  ", style = MaterialTheme.typography.labelSmall)
                ConfidenceDot(Confidence.MEDIUM); Text(" $med  ", style = MaterialTheme.typography.labelSmall)
                ConfidenceDot(Confidence.LOW); Text(" $low identificados", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun NewDevicesBanner(devices: List<Device>, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFB74D).copy(alpha = 0.18f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.NewReleases, null, tint = Color(0xFFFF9800))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (devices.size == 1) "1 dispositivo nuevo en tu red"
                    else "${devices.size} dispositivos nuevos en tu red",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    devices.take(3).joinToString(" · ") { it.bestLabel() },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onDismiss) { Text("Visto") }
        }
    }
}

@Composable
private fun VersionRow(
    version: String,
    status: ScanViewModel.CheckStatus,
    onCheck: () -> Unit
) {
    val checking = status == ScanViewModel.CheckStatus.CHECKING
    val statusText = when (status) {
        ScanViewModel.CheckStatus.CHECKING -> "Buscando…"
        ScanViewModel.CheckStatus.UP_TO_DATE -> "Estás al día ✓"
        ScanViewModel.CheckStatus.ERROR -> "No se pudo comprobar"
        else -> null
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Versión $version",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        statusText?.let {
            Text(
                "  ·  $it",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCheck, enabled = !checking) {
            Text("Buscar actualizaciones")
        }
    }
}

@Composable
private fun UpdateBanner(
    versionName: String,
    phase: String,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "Actualización disponible · v$versionName",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (phase.isNotEmpty()) {
                    Text(
                        phase,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            if (phase.isEmpty()) {
                TextButton(onClick = onDismiss) { Text("Después") }
                Button(onClick = onUpdate) { Text("Actualizar") }
            } else {
                Button(onClick = onUpdate) { Text("Reintentar") }
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.TravelExplore, null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Toca «Escanear» para descubrir\nlos dispositivos de tu red",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun DeviceCard(d: Device, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Icon(
                    iconFor(d), null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Box(Modifier.align(Alignment.BottomEnd)) { ConfidenceDot(d.confidence()) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    d.bestLabel(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val sub = d.subLabel()
                if (sub.isNotEmpty()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    d.ip,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace
                )
                d.latencyMs?.let {
                    Text(
                        "$it ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                if (d.isNew) Badge("NUEVO", Color(0xFFFF9800))
                if (d.isGateway) Badge("router")
                if (d.isThisDevice) Badge("este equipo")
            }
        }
    }
}

@Composable
internal fun Badge(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Surface(
        color = color.copy(alpha = 0.18f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.padding(top = 2.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

@Composable
internal fun ConfidenceDot(c: Confidence) {
    Box(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(confidenceColor(c))
    )
}

internal fun confidenceColor(c: Confidence): Color = when (c) {
    Confidence.HIGH -> Color(0xFF66BB6A)
    Confidence.MEDIUM -> Color(0xFFFFCA28)
    Confidence.LOW -> Color(0xFF9E9E9E)
}

internal fun iconFor(d: Device): ImageVector = iconForType(d.deviceType, d.isGateway)

internal fun iconForType(type: String?, isGateway: Boolean = false): ImageVector {
    val t = (type ?: "").lowercase()
    return when {
        isGateway || t.contains("router") -> Icons.Default.Router
        t.contains("tv") || t.contains("media") || t.contains("chromecast") -> Icons.Default.Tv
        t.contains("impresora") -> Icons.Default.Print
        t.contains("cámara") -> Icons.Default.Videocam
        t.contains("iphone") || t.contains("apple") -> Icons.Default.PhoneIphone
        t.contains("teléfono") || t.contains("android") || t.contains("phone") -> Icons.Default.Smartphone
        t.contains("pc") || t.contains("windows") || t.contains("linux") || t.contains("nas") -> Icons.Default.Computer
        t.contains("altavoz") || t.contains("sonos") || t.contains("home") -> Icons.Default.Speaker
        t.contains("iot") || t.contains("domótica") -> Icons.Default.SettingsRemote
        else -> Icons.Default.DeviceUnknown
    }
}
