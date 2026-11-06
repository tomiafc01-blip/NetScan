package com.netscan.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.netscan.app.ScanViewModel
import com.netscan.app.scan.Device

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(vm: ScanViewModel) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NetScan", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
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
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            NetHeader(state.net?.ssid, state.net?.localIp, state.phase)

            if (state.devices.isEmpty() && !state.running) {
                EmptyHint()
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 90.dp)
                ) {
                    items(state.devices, key = { it.ip }) { d ->
                        DeviceCard(d)
                    }
                }
            }
        }
    }
}

@Composable
private fun NetHeader(ssid: String?, ip: String?, phase: String) {
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
private fun DeviceCard(d: Device) {
    var expanded by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    iconFor(d), null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        d.bestLabel(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    val sub = d.subLabel()
                    if (sub.isNotEmpty()) {
                        Text(
                            sub,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        d.ip,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                    if (d.isGateway) Badge("router")
                    if (d.isThisDevice) Badge("este equipo")
                }
            }

            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 10.dp)) {
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    Spacer(Modifier.height(8.dp))
                    DetailRow("IP", d.ip)
                    d.mac?.let { DetailRow("MAC", it) }
                    d.vendor?.let { DetailRow("Fabricante", it) }
                    d.hostname?.let { DetailRow("Nombre", it) }
                    d.model?.let { DetailRow("Modelo", it) }
                    d.deviceType?.let { DetailRow("Tipo", it) }
                    if (d.openPorts.isNotEmpty()) {
                        DetailRow("Puertos", d.openPorts.joinToString(", "))
                    }
                    if (d.sources.isNotEmpty()) {
                        DetailRow("Detectado por", d.sources.joinToString(", "))
                    }
                }
            }
        }
    }
}

@Composable
private fun Badge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.padding(top = 2.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (label == "MAC" || label == "IP" || label == "Puertos")
                FontFamily.Monospace else FontFamily.Default
        )
    }
}

private fun iconFor(d: Device): ImageVector {
    val t = (d.deviceType ?: "").lowercase()
    return when {
        d.isGateway || t.contains("router") -> Icons.Default.Router
        t.contains("tv") || t.contains("media") || t.contains("chromecast") -> Icons.Default.Tv
        t.contains("impresora") -> Icons.Default.Print
        t.contains("cámara") -> Icons.Default.Videocam
        t.contains("apple") -> Icons.Default.PhoneIphone
        t.contains("pc") || t.contains("windows") || t.contains("linux") || t.contains("nas") -> Icons.Default.Computer
        t.contains("altavoz") -> Icons.Default.Speaker
        t.contains("teléfono") || t.contains("phone") -> Icons.Default.Smartphone
        else -> Icons.Default.DeviceUnknown
    }
}
