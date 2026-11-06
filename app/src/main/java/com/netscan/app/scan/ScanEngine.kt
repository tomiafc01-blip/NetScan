package com.netscan.app.scan

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.net.InetAddress

/** Estado del escaneo que se refleja en la UI. */
data class ScanState(
    val running: Boolean = false,
    val phase: String = "",
    val net: LocalNet? = null,
    val devices: List<Device> = emptyList()
)

/**
 * Une las 5 capas: barrido de hosts + (mDNS, SSDP a nivel de red) +
 * (NetBIOS, banners HTTP, DNS inverso por host) + OUI. Fusiona en Device.
 */
class ScanEngine(private val context: Context) {

    /** Ejecuta un escaneo completo, emitiendo estados intermedios. */
    suspend fun run(emit: (ScanState) -> Unit) = coroutineScope {
        val net = NetworkInfo.current(context)
        if (net == null) {
            emit(ScanState(running = false, phase = "Sin red Wi-Fi activa"))
            return@coroutineScope
        }

        val devices = LinkedHashMap<String, Device>()
        fun snapshot(phase: String, running: Boolean = true) =
            emit(ScanState(running, phase, net, devices.values.sortedBy { ipKey(it.ip) }))

        snapshot("Buscando dispositivos activos…")

        // Lanzamos mDNS y SSDP en paralelo con el barrido (son de red, no por host).
        val mdnsJob = async(Dispatchers.IO) {
            try { MdnsScanner(context).discover() } catch (_: Exception) { emptyMap() }
        }
        val ssdpJob = async(Dispatchers.IO) {
            try { SsdpScanner(context).discover() } catch (_: Exception) { emptyMap() }
        }

        // Barrido de hosts (emite cada IP viva en cuanto aparece).
        HostScanner.sweep(net) { d ->
            if (!devices.containsKey(d.ip)) {
                devices[d.ip] = d
                snapshot("Encontrados ${devices.size}…")
            }
        }

        snapshot("Identificando (mDNS / SSDP)…")
        val mdns = mdnsJob.await()
        val ssdp = ssdpJob.await()

        for ((ip, hit) in mdns) {
            val d = devices.getOrPut(ip) { Device(ip) }
            hit.name?.let { if (d.hostname == null) d.hostname = it }
            hit.model?.let { d.model = it }
            hit.type?.let { if (d.deviceType == null) d.deviceType = it }
            d.sources.add("mDNS")
        }
        for ((ip, hit) in ssdp) {
            val d = devices.getOrPut(ip) { Device(ip) }
            hit.model?.let { d.model = it }
            hit.manufacturer?.let { if (d.vendor == null) d.vendor = it }
            hit.friendlyName?.let { if (d.hostname == null) d.hostname = it }
            hit.deviceType?.let { if (d.deviceType == null) d.deviceType = it }
            d.sources.add("SSDP")
        }
        snapshot("Consultando nombres y servicios…")

        // Por host: NetBIOS + banners + DNS inverso, en paralelo.
        val list = devices.values.toList()
        list.map { d ->
            async(Dispatchers.IO) {
                // DNS inverso
                if (d.hostname == null) {
                    try {
                        val h = InetAddress.getByName(d.ip).canonicalHostName
                        if (h != d.ip) d.hostname = h.substringBefore('.')
                    } catch (_: Exception) {}
                }
                // NetBIOS
                if (d.hostname == null) {
                    NetbiosScanner.query(d.ip)?.let {
                        d.hostname = it
                        d.sources.add("NetBIOS")
                        if (d.deviceType == null) d.deviceType = "PC (Windows)"
                    }
                }
                // Banners HTTP + puertos
                val banner = BannerScanner.probe(d.ip)
                d.openPorts = banner.openPorts.toMutableList()
                if (banner.openPorts.isNotEmpty()) d.sources.add("Puertos")
                if (d.model == null) {
                    banner.title?.let { d.model = cleanTitle(it) }
                }
                if (banner.server != null) {
                    d.sources.add("HTTP")
                    if (d.deviceType == null) d.deviceType = typeFromServer(banner.server!!)
                }
            }
        }.awaitAll()

        // OUI al final (solo útil si conseguimos MAC).
        for (d in devices.values) {
            if (d.vendor == null) {
                OuiLookup.vendorFor(context, d.mac)?.let {
                    d.vendor = it
                    d.sources.add("OUI")
                }
            }
            inferType(d)
        }

        snapshot("Listo · ${devices.size} dispositivos", running = false)
    }

    private fun ipKey(ip: String): Long {
        val p = ip.split(".")
        if (p.size != 4) return Long.MAX_VALUE
        return p.fold(0L) { acc, s -> acc * 256 + (s.toLongOrNull() ?: 0) }
    }

    private fun cleanTitle(t: String): String =
        t.replace(Regex("\\s+"), " ").take(60)

    private fun typeFromServer(server: String): String? {
        val s = server.lowercase()
        return when {
            s.contains("routeros") || s.contains("openwrt") || s.contains("lighttpd") -> "Router"
            s.contains("hikvision") || s.contains("dahua") || s.contains("rtsp") -> "Cámara IP"
            s.contains("cups") -> "Impresora"
            s.contains("synology") || s.contains("qnap") -> "NAS"
            else -> null
        }
    }

    private fun inferType(d: Device) {
        if (d.deviceType != null) return
        d.deviceType = when {
            d.isGateway -> "Router / puerta de enlace"
            9100 in d.openPorts || 515 in d.openPorts || 631 in d.openPorts -> "Impresora"
            554 in d.openPorts -> "Cámara IP"
            62078 in d.openPorts -> "Dispositivo Apple"
            445 in d.openPorts || 139 in d.openPorts -> "PC / NAS (SMB)"
            22 in d.openPorts -> "Equipo Linux / servidor"
            else -> null
        }
    }
}
