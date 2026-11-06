package com.netscan.app.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.netscan.app.scan.RiskAnalyzer
import com.netscan.app.scan.RiskLevel
import com.netscan.app.ui.components.GradientFab
import com.netscan.app.ui.components.HeroHeader
import com.netscan.app.ui.components.pressable

private enum class Filter(val label: String) {
    TODOS("Todos"), RIESGOS("Con riesgo"), NO_CONFIABLES("No confiables"), NUEVOS("Nuevos")
}

private enum class Sort(val label: String) { IP("IP"), NOMBRE("Nombre"), CONFIANZA("Confianza") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(vm: ScanViewModel) {
    val state by vm.state.collectAsState()
    val toast by vm.toast.collectAsState()
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var selectedIp by remember { mutableStateOf<String?>(null) }
    var showDiag by remember { mutableStateOf(false) }

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
                        if (tab == 0) {
                            IconButton(onClick = { vm.export(ctx) }) {
                                Icon(Icons.Default.Share, contentDescription = "Exportar escaneo")
                            }
                        }
                        IconButton(onClick = { showDiag = true }) {
                            Icon(Icons.Default.BugReport, contentDescription = "Diagnóstico")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.surface) {
                    Tab(selected = tab == 0, onClick = { tab = 0 },
                        text = { Text("Red") }, icon = { Icon(Icons.Default.Radar, null) })
                    Tab(selected = tab == 1, onClick = { tab = 1 },
                        text = { Text("Wi-Fi") }, icon = { Icon(Icons.Default.NetworkWifi, null) })
                    Tab(selected = tab == 2, onClick = { tab = 2; vm.refreshHistory() },
                        text = { Text("Historial") }, icon = { Icon(Icons.Default.History, null) })
                }
            }
        },
        floatingActionButton = {
            if (tab == 0) GradientFab(scanning = state.running) { vm.startScan() }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            when (tab) {
                0 -> NetworkTab(vm, state.rev, onSelect = { selectedIp = it.ip })
                1 -> WifiTab(vm)
                else -> HistoryTab(vm)
            }
        }
    }

    if (showDiag) DiagSheet(onDismiss = { showDiag = false })

    val selected = selectedIp?.let { ip -> state.devices.firstOrNull { it.ip == ip } }
    if (selected != null) {
        DeviceSheet(
            device = selected,
            rev = state.rev,
            vm = vm,
            onDismiss = { selectedIp = null; vm.clearPing(); vm.clearPortScan() }
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

    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.TODOS) }
    var sort by rememberSaveable { mutableStateOf(Sort.IP) }
    var subnetDialog by remember { mutableStateOf(false) }

    val shown = remember(state.devices, state.rev, query, filter, sort) {
        state.devices.asSequence()
            .filter { d ->
                when (filter) {
                    Filter.TODOS -> true
                    Filter.RIESGOS -> d.risks.isNotEmpty()
                    Filter.NO_CONFIABLES -> !d.trusted && !d.isThisDevice
                    Filter.NUEVOS -> d.isNew
                }
            }
            .filter { d ->
                query.isBlank() || listOfNotNull(
                    d.bestLabel(), d.ip, d.mac, d.vendor, d.hostname, d.model, d.deviceType
                ).any { it.contains(query, ignoreCase = true) }
            }
            .sortedWith(when (sort) {
                Sort.IP -> compareBy { ipKey(it.ip) }
                Sort.NOMBRE -> compareBy { it.bestLabel().lowercase() }
                Sort.CONFIANZA -> compareByDescending { it.confidence().ordinalHigh() }
            })
            .toList()
    }

    Column(Modifier.fillMaxSize()) {
        HeroHeader(
            ssid = state.net?.ssid,
            ip = state.net?.localIp,
            phase = state.phase,
            deviceCount = state.devices.size,
            riskCount = state.devices.count { it.risks.isNotEmpty() },
            untrusted = state.devices.count { !it.trusted && !it.isThisDevice },
            onScanSubnet = { subnetDialog = true },
            composition = { CompositionBar(state.devices, Modifier.fillMaxWidth()) }
        )
        Spacer(Modifier.height(10.dp))

        VersionRow(vm.currentVersion, checkStatus) { vm.checkForUpdate(manual = true) }

        update?.let { rel ->
            UpdateBanner(rel.versionName, updatePhase, { vm.runUpdate(ctx) }, { vm.dismissUpdate() })
        }
        if (newDevices.isNotEmpty()) {
            NewDevicesBanner(newDevices) { vm.dismissNew() }
        }

        if (state.running) {
            Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                ScanRadar(state.devices.size, running = true, modifier = Modifier.size(150.dp))
            }
        }

        if (state.devices.isNotEmpty() || state.running) {
            FilterBar(query, { query = it }, filter, { filter = it }, sort, { sort = it })
        }

        if (state.devices.isEmpty() && !state.running) {
            EmptyHint()
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(shown, key = { it.ip }) { d ->
                    key(rev) { DeviceCard(d, Modifier.animateItem(), onClick = { onSelect(d) }) }
                }
                if (shown.isEmpty()) {
                    item {
                        Text("Nada coincide con el filtro.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
            }
        }
    }

    if (subnetDialog) {
        SubnetDialog(
            current = state.net?.localIp,
            onScan = { subnetDialog = false; vm.scanSubnet(it) },
            onDismiss = { subnetDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBar(
    query: String, onQuery: (String) -> Unit,
    filter: Filter, onFilter: (Filter) -> Unit,
    sort: Sort, onSort: (Sort) -> Unit
) {
    Column(Modifier.padding(bottom = 6.dp)) {
        OutlinedTextField(
            value = query, onValueChange = onQuery,
            placeholder = { Text("Buscar nombre, IP, MAC…") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Default.Clear, null) }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Filter.values().forEach { f ->
                FilterChip(selected = filter == f, onClick = { onFilter(f) }, label = { Text(f.label) })
            }
            VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
            Icon(Icons.Default.Sort, null, modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Sort.values().forEach { srt ->
                FilterChip(selected = sort == srt, onClick = { onSort(srt) }, label = { Text(srt.label) })
            }
        }
    }
}


@Composable
private fun SubnetDialog(current: String?, onScan: (String) -> Unit, onDismiss: () -> Unit) {
    val guess = current?.substringBeforeLast('.')?.let {
        // Sugerimos la subred vecina (.0 -> .1).
        val parts = it.split('.')
        if (parts.size == 3) "${parts[0]}.${parts[1]}.${(parts[2].toIntOrNull() ?: 0) + 1}" else "192.168.1"
    } ?: "192.168.1"
    var text by remember { mutableStateOf(guess) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Escanear otra subred") },
        text = {
            Column {
                Text("Útil para la red de invitados o un repetidor. Escribe los 3 primeros números.",
                    style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = text, onValueChange = { text = it.take(15) },
                    singleLine = true, placeholder = { Text("192.168.1") },
                    label = { Text("Subred") })
                Text("Sin mDNS/SSDP: esta vista identifica por puertos, NetBIOS y fabricante.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 6.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onScan(text) }) { Text("Escanear") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun NewDevicesBanner(devices: List<Device>, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFB74D).copy(alpha = 0.18f),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.NewReleases, null, tint = Color(0xFFFF9800))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (devices.size == 1) "1 dispositivo nuevo en tu red"
                    else "${devices.size} dispositivos nuevos en tu red",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
                )
                Text(devices.take(3).joinToString(" · ") { it.bestLabel() },
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onDismiss) { Text("Visto") }
        }
    }
}

@Composable
private fun VersionRow(version: String, status: ScanViewModel.CheckStatus, onCheck: () -> Unit) {
    val checking = status == ScanViewModel.CheckStatus.CHECKING
    val statusText = when (status) {
        ScanViewModel.CheckStatus.CHECKING -> "Buscando…"
        ScanViewModel.CheckStatus.UP_TO_DATE -> "Estás al día ✓"
        ScanViewModel.CheckStatus.ERROR -> "No se pudo comprobar"
        else -> null
    }
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Versión $version", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        statusText?.let {
            Text("  ·  $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onCheck, enabled = !checking) { Text("Buscar actualizaciones") }
    }
}

@Composable
private fun UpdateBanner(versionName: String, phase: String, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Actualización disponible · v$versionName",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                if (phase.isNotEmpty()) {
                    Text(phase, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
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
            Icon(Icons.Default.TravelExplore, null, modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
            Spacer(Modifier.height(8.dp))
            Text("Toca «Escanear» para descubrir\nlos dispositivos de tu red",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

@Composable
private fun DeviceCard(d: Device, modifier: Modifier = Modifier, onClick: () -> Unit) {
    // Franja de acento: rojo/ámbar si hay riesgo, si no el color de confianza.
    val accent = if (d.risks.isNotEmpty()) riskColor(RiskAnalyzer.worst(d.risks)) else confidenceColor(d.confidence())
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth().pressable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Icon(iconFor(d), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Box(Modifier.align(Alignment.BottomEnd)) { ConfidenceDot(d.confidence()) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.bestLabel(), style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (d.trusted) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Verified, "de confianza", tint = Color(0xFF66BB6A),
                            modifier = Modifier.size(15.dp))
                    }
                }
                val sub = d.subLabel()
                if (sub.isNotEmpty()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (d.risks.isNotEmpty()) {
                    val worst = RiskAnalyzer.worst(d.risks)
                    Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = riskColor(worst), modifier = Modifier.size(13.dp))
                        Text(" ${d.risks.size} ${if (d.risks.size == 1) "aviso" else "avisos"} de seguridad",
                            style = MaterialTheme.typography.labelSmall, color = riskColor(worst))
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(d.ip, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                d.latencyMs?.let {
                    Text("$it ms", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                if (d.isNew) Badge("NUEVO", Color(0xFFFF9800))
                if (d.isGateway) Badge("router")
                if (d.isThisDevice) Badge("este equipo")
            }
            }
        }
    }
}

@Composable
internal fun Badge(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Surface(color = color.copy(alpha = 0.18f), shape = RoundedCornerShape(6.dp), modifier = Modifier.padding(top = 2.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
    }
}

@Composable
internal fun ConfidenceDot(c: Confidence) {
    Box(Modifier.size(10.dp).clip(CircleShape).background(confidenceColor(c)))
}

internal fun confidenceColor(c: Confidence): Color = when (c) {
    Confidence.HIGH -> Color(0xFF66BB6A)
    Confidence.MEDIUM -> Color(0xFFFFCA28)
    Confidence.LOW -> Color(0xFF9E9E9E)
}

internal fun riskColor(level: RiskLevel?): Color = when (level) {
    RiskLevel.HIGH -> Color(0xFFE57373)
    RiskLevel.MEDIUM -> Color(0xFFFFB74D)
    else -> Color(0xFF90A4AE)
}

private fun Confidence.ordinalHigh(): Int = when (this) {
    Confidence.HIGH -> 2; Confidence.MEDIUM -> 1; Confidence.LOW -> 0
}

internal fun ipKey(ip: String): Long {
    val p = ip.split("."); if (p.size != 4) return Long.MAX_VALUE
    return p.fold(0L) { acc, s -> acc * 256 + (s.toLongOrNull() ?: 0) }
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
