package com.netscan.app.scan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Collections
import kotlin.coroutines.resume

/**
 * Capa 2: mDNS / Bonjour / Zeroconf vía NsdManager.
 * Descubre servicios anunciados en la red y extrae IP, nombre y, si
 * el servicio lo publica en su TXT, el modelo del dispositivo.
 *
 * Importante: NsdManager solo resuelve UN servicio a la vez en muchas
 * versiones de Android (si no, falla con FAILURE_ALREADY_ACTIVE), así que
 * los servicios encontrados se ponen en una cola y se resuelven en orden.
 */
class MdnsScanner(private val context: Context) {

    private val nsd =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    // Tipos de servicio más informativos para identificar aparatos.
    private val serviceTypes = listOf(
        "_googlecast._tcp.",   // Chromecast / Google TV (TXT trae 'md' = modelo)
        "_airplay._tcp.",      // Apple TV / AirPlay (TXT trae 'model')
        "_raop._tcp.",         // AirPlay audio
        "_ipp._tcp.",          // Impresoras (TXT trae 'ty' = modelo)
        "_ipps._tcp.",
        "_printer._tcp.",
        "_pdl-datastream._tcp.", // Impresoras RAW
        "_scanner._tcp.",      // Escáneres / multifuncionales
        "_http._tcp.",         // NAS, routers, cámaras, muchos IoT
        "_workstation._tcp.",  // PCs Linux/avahi
        "_device-info._tcp.",  // Apple: TXT 'model' = p.ej. iPhone14,5 / Macmini9,1
        "_companion-link._tcp.", // Apple (Handoff)
        "_hap._tcp.",          // HomeKit (TXT 'md' = modelo)
        "_homekit._tcp.",
        "_spotify-connect._tcp.",
        "_sonos._tcp.",
        "_miio._udp.",         // Xiaomi / Mi Home (IoT, cámaras, enchufes)
        "_amzn-wplay._tcp.",   // Amazon Fire TV
        "_googlezone._tcp.",   // Google Home / Nest
        "_androidtvremote2._tcp.", // Android TV / Google TV
        "_smb._tcp.",          // NAS / compartidos
        "_afpovertcp._tcp.",   // Apple File Protocol / NAS
        "_ssh._tcp.",          // Raspberry Pi / Linux
        "_esphomelib._tcp.",   // ESPHome (domótica DIY)
        "_rtsp._tcp.",         // Cámaras IP
        "_dlna._tcp.",         // Media / Smart TV
        "_mediarenderer._tcp."
    )

    /** Resultado por IP: nombre, modelo, tipo y servicios anunciados. */
    data class Hit(
        val ip: String,
        val name: String?,
        val model: String?,
        val type: String?,
        val services: Set<String>
    )

    suspend fun discover(durationMs: Long = 7000): Map<String, Hit> {
        val results = Collections.synchronizedMap(HashMap<String, Hit>())
        val listeners = mutableListOf<NsdManager.DiscoveryListener>()
        val queue = Channel<NsdServiceInfo>(Channel.UNLIMITED)
        val seen = Collections.synchronizedSet(HashSet<String>())

        // Sin este lock muchos teléfonos descartan los paquetes multicast.
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("netscan-mdns").apply {
            setReferenceCounted(true)
            try { acquire() } catch (_: Exception) {}
        }

        val scope = CoroutineScope(Dispatchers.IO)
        // Consumidor: resuelve los servicios de uno en uno.
        val resolver = scope.launch {
            for (info in queue) {
                val resolved = resolveOne(info) ?: continue
                merge(resolved, results)
            }
        }

        try {
            for (type in serviceTypes) {
                val listener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) {}
                    override fun onDiscoveryStopped(serviceType: String) {}
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                    override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                    override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                        val id = "${serviceInfo.serviceName}|${serviceInfo.serviceType}"
                        if (seen.add(id)) queue.trySend(serviceInfo)
                    }
                }
                try {
                    nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                    listeners.add(listener)
                } catch (_: Exception) {}
            }

            delay(durationMs)
        } finally {
            for (l in listeners) {
                try { nsd.stopServiceDiscovery(l) } catch (_: Exception) {}
            }
            queue.close()
            // Dejamos que termine la cola, con tope para no colgar el escaneo.
            withTimeoutOrNull(6000) { resolver.join() }
            resolver.cancel()
            try { lock.release() } catch (_: Exception) {}
        }
        return HashMap(results)
    }

    /** Resuelve un servicio y espera el resultado (con reintento si está ocupado). */
    @Suppress("DEPRECATION")
    private suspend fun resolveOne(info: NsdServiceInfo): NsdServiceInfo? {
        repeat(2) { attempt ->
            var busy = false
            val r = withTimeoutOrNull(2500) {
                suspendCancellableCoroutine<NsdServiceInfo?> { cont ->
                    val listener = object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            busy = errorCode == NsdManager.FAILURE_ALREADY_ACTIVE
                            if (cont.isActive) cont.resume(null)
                        }
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            if (cont.isActive) cont.resume(serviceInfo)
                        }
                    }
                    try {
                        nsd.resolveService(info, listener)
                    } catch (_: Exception) {
                        if (cont.isActive) cont.resume(null)
                    }
                }
            }
            if (r != null) return r
            if (!busy || attempt == 1) return null
            delay(300)
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun merge(info: NsdServiceInfo, out: MutableMap<String, Hit>) {
        val host = info.host ?: return
        val ip = host.hostAddress ?: return
        if (ip.contains(':')) return // ignoramos IPv6
        val friendly = extractFriendly(info)
        val model = extractModel(info)
        val type = typeLabel(info.serviceType)
        val svc = serviceLabel(info.serviceType)
        synchronized(out) {
            val existing = out[ip]
            out[ip] = Hit(
                ip = ip,
                name = existing?.name ?: friendly ?: info.serviceName,
                // El modelo de _device-info / googlecast es más fiable: no lo pisamos.
                model = existing?.model ?: model,
                type = existing?.type ?: type,
                services = (existing?.services ?: emptySet()) + listOfNotNull(svc)
            )
        }
    }

    /** Busca el modelo en los atributos TXT y traduce códigos de Apple. */
    private fun extractModel(info: NsdServiceInfo): String? {
        val attrs = try { info.attributes } catch (_: Exception) { null } ?: return null
        for (key in listOf("md", "model", "ty", "am", "usb_MDL", "product")) {
            val bytes = attrs[key] ?: continue
            val value = bytes.toString(Charsets.UTF_8).trim().trim('(', ')')
            if (value.isNotEmpty()) return AppleModels.readable(value)
        }
        return null
    }

    /** Nombre amigable publicado en el TXT (fn/n), más legible que un hash. */
    private fun extractFriendly(info: NsdServiceInfo): String? {
        val attrs = try { info.attributes } catch (_: Exception) { null } ?: return null
        for (key in listOf("fn", "n", "name")) {
            val bytes = attrs[key] ?: continue
            val value = bytes.toString(Charsets.UTF_8).trim()
            if (value.isNotEmpty()) return value
        }
        return null
    }

    private fun typeLabel(serviceType: String?): String? {
        val t = serviceType ?: return null
        return when {
            t.contains("googlecast") || t.contains("androidtvremote") -> "Chromecast / Google TV"
            t.contains("amzn-wplay") -> "Amazon Fire TV"
            t.contains("airplay") || t.contains("raop") -> "Apple / AirPlay"
            t.contains("ipp") || t.contains("printer") || t.contains("pdl-datastream") -> "Impresora"
            t.contains("scanner") -> "Impresora / escáner"
            t.contains("sonos") || t.contains("spotify") -> "Altavoz"
            t.contains("googlezone") -> "Google Home / Nest"
            t.contains("hap") || t.contains("homekit") || t.contains("esphome") || t.contains("miio") ->
                "Dispositivo IoT / domótica"
            t.contains("rtsp") -> "Cámara IP"
            t.contains("workstation") -> "PC"
            else -> null
        }
    }

    /** Nombre corto del servicio para mostrarlo en el detalle. */
    private fun serviceLabel(serviceType: String?): String? {
        val t = serviceType?.trim('.') ?: return null
        val core = t.substringBefore("._").trimStart('_').ifEmpty { return null }
        return when (core) {
            "googlecast" -> "Google Cast"
            "airplay" -> "AirPlay"
            "raop" -> "AirPlay audio"
            "ipp", "ipps" -> "Impresión IPP"
            "printer", "pdl-datastream" -> "Impresión"
            "scanner" -> "Escáner"
            "http" -> "Web"
            "smb" -> "Archivos SMB"
            "afpovertcp" -> "Archivos AFP"
            "ssh" -> "SSH"
            "hap", "homekit" -> "HomeKit"
            "spotify-connect" -> "Spotify Connect"
            "device-info" -> "Info de Apple"
            "companion-link" -> "Apple Handoff"
            "googlezone" -> "Google Home"
            "androidtvremote2" -> "Control Android TV"
            "amzn-wplay" -> "Fire TV"
            else -> core
        }
    }
}

/** Traduce identificadores de Apple ("iPhone14,5") a nombres comerciales. */
object AppleModels {
    private val known = mapOf(
        "iPhone17,1" to "iPhone 16 Pro", "iPhone17,2" to "iPhone 16 Pro Max",
        "iPhone17,3" to "iPhone 16", "iPhone17,4" to "iPhone 16 Plus", "iPhone17,5" to "iPhone 16e",
        "iPhone16,1" to "iPhone 15 Pro", "iPhone16,2" to "iPhone 15 Pro Max",
        "iPhone15,4" to "iPhone 15", "iPhone15,5" to "iPhone 15 Plus",
        "iPhone15,2" to "iPhone 14 Pro", "iPhone15,3" to "iPhone 14 Pro Max",
        "iPhone14,7" to "iPhone 14", "iPhone14,8" to "iPhone 14 Plus",
        "iPhone14,2" to "iPhone 13 Pro", "iPhone14,3" to "iPhone 13 Pro Max",
        "iPhone14,4" to "iPhone 13 mini", "iPhone14,5" to "iPhone 13", "iPhone14,6" to "iPhone SE (3.ª gen)",
        "iPhone13,1" to "iPhone 12 mini", "iPhone13,2" to "iPhone 12",
        "iPhone13,3" to "iPhone 12 Pro", "iPhone13,4" to "iPhone 12 Pro Max",
        "iPhone12,1" to "iPhone 11", "iPhone12,3" to "iPhone 11 Pro", "iPhone12,5" to "iPhone 11 Pro Max",
        "iPhone12,8" to "iPhone SE (2.ª gen)",
        "AppleTV14,1" to "Apple TV 4K (3.ª gen)", "AppleTV11,1" to "Apple TV 4K (2.ª gen)",
        "AppleTV6,2" to "Apple TV 4K", "AppleTV5,3" to "Apple TV HD",
        "AudioAccessory1,1" to "HomePod", "AudioAccessory5,1" to "HomePod mini",
        "AudioAccessory6,1" to "HomePod (2.ª gen)",
        "Mac14,2" to "MacBook Air M2", "Mac14,15" to "MacBook Air 15\" M2",
        "Mac15,12" to "MacBook Air M3", "Mac14,3" to "Mac mini M2", "Mac16,10" to "Mac mini M4",
        "Macmini9,1" to "Mac mini M1", "MacBookAir10,1" to "MacBook Air M1",
        "MacBookPro17,1" to "MacBook Pro 13\" M1", "MacBookPro18,1" to "MacBook Pro 16\" M1 Pro",
        "MacBookPro18,3" to "MacBook Pro 14\" M1 Pro", "iMac21,1" to "iMac 24\" M1"
    )

    fun readable(code: String): String {
        val c = code.trim()
        known[c]?.let { return it }
        return when {
            c.startsWith("iPhone") && c.contains(',') -> "iPhone ($c)"
            c.startsWith("iPad") && c.contains(',') -> "iPad ($c)"
            c.startsWith("Watch") && c.contains(',') -> "Apple Watch ($c)"
            c.startsWith("AudioAccessory") -> "HomePod"
            c.startsWith("AppleTV") -> "Apple TV"
            (c.startsWith("Mac") || c.startsWith("iMac")) && c.contains(',') -> "Mac ($c)"
            else -> c
        }
    }
}
