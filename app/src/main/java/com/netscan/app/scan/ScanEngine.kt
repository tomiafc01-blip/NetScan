package com.netscan.app.scan

import android.content.Context
import android.os.Build
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
    val devices: List<Device> = emptyList(),
    val rev: Int = 0 // Sube cuando cambian datos de un Device ya mostrado (p. ej. al renombrarlo)
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
        // El barrido avisa desde varios hilos a la vez: protegemos el mapa.
        fun snapshot(phase: String, running: Boolean = true) {
            val list = synchronized(devices) { devices.values.sortedBy { ipKey(it.ip) } }
            emit(ScanState(running, phase, net, list))
        }

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
            val added = synchronized(devices) {
                if (devices.containsKey(d.ip)) false else { devices[d.ip] = d; true }
            }
            if (added) snapshot("Encontrados ${devices.size}…")
        }

        snapshot("Identificando (mDNS / SSDP)…")
        val mdns = mdnsJob.await()
        val ssdp = ssdpJob.await()

        for ((ip, hit) in mdns) {
            if (!net.contains(ip)) continue
            val d = devices.getOrPut(ip) { Device(ip) }
            hit.name?.let { if (d.hostname == null) d.hostname = it }
            hit.model?.let { d.model = it }
            hit.type?.let { if (d.deviceType == null) d.deviceType = it }
            d.services.addAll(hit.services)
            d.sources.add("mDNS")
        }
        for ((ip, hit) in ssdp) {
            if (!net.contains(ip)) continue
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
                // NetBIOS: nombre del equipo y, de regalo, su MAC real.
                if (d.hostname == null || d.mac == null) {
                    NetbiosScanner.query(d.ip)?.let { nb ->
                        nb.name?.let {
                            if (d.hostname == null) d.hostname = it
                            if (d.deviceType == null) d.deviceType = "PC (Windows)"
                        }
                        nb.mac?.let { if (d.mac == null) d.mac = it }
                        d.sources.add("NetBIOS")
                    }
                }
                // Banners HTTP + puertos
                val banner = BannerScanner.probe(d.ip)
                d.openPorts = banner.openPorts.toMutableList()
                d.webUrl = banner.webUrl
                if (banner.openPorts.isNotEmpty()) d.sources.add("Puertos")
                if (d.model == null) {
                    banner.title?.let { cleanTitle(it)?.let { t -> d.model = t } }
                }
                if (banner.server != null) {
                    d.sources.add("HTTP")
                    if (d.deviceType == null) d.deviceType = typeFromServer(banner.server!!)
                }
            }
        }.awaitAll()

        // Este teléfono: sabemos exactamente qué es.
        devices[net.localIp]?.let { me ->
            me.isThisDevice = true
            me.model = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
            me.deviceType = "Teléfono Android"
            me.sources.add("Sistema")
        }

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

    /** Escaneo completo sin estados intermedios (para la vigilancia en segundo plano). */
    suspend fun runOnce(): ScanState {
        var last = ScanState()
        this.run { last = it }
        return last
    }

    private fun ipKey(ip: String): Long {
        val p = ip.split(".")
        if (p.size != 4) return Long.MAX_VALUE
        return p.fold(0L) { acc, s -> acc * 256 + (s.toLongOrNull() ?: 0) }
    }

    /** Títulos web genéricos que no dicen nada del aparato. */
    private val junkTitles = setOf(
        "login", "log in", "index", "home", "welcome", "redirect", "redirecting",
        "loading", "404", "403", "401", "error", "untitled", "document", "web", "sign in"
    )

    private fun cleanTitle(t: String): String? {
        val c = t.replace(Regex("\\s+"), " ").trim().take(60)
        if (c.length < 3 || c.lowercase().trim('.', '…') in junkTitles) return null
        if (c.lowercase().startsWith("404") || c.lowercase().contains("not found")) return null
        return c
    }

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
        val p = d.openPorts
        d.deviceType = when {
            d.isGateway -> "Router / puerta de enlace"
            8009 in p -> "Chromecast / Google TV"
            1400 in p -> "Sonos"
            7000 in p -> "Apple / AirPlay"
            62078 in p -> "iPhone / iPad"
            32469 in p -> "Servidor Plex"
            5000 in p || 5001 in p -> "NAS (Synology)"
            9100 in p || 515 in p || 631 in p -> "Impresora"
            554 in p || 8554 in p -> "Cámara IP"
            3389 in p || 2869 in p -> "PC (Windows)"
            445 in p || 139 in p -> "PC / NAS (SMB)"
            548 in p -> "Equipo Apple / NAS"
            5555 in p -> "Android / Fire TV (ADB)"
            1883 in p || 8883 in p -> "Dispositivo IoT / domótica"
            23 in p -> "Dispositivo IoT"
            22 in p -> "Equipo Linux / servidor"
            53 in p && 80 in p -> "Router / servidor DNS"
            else -> null
        }
    }
}
