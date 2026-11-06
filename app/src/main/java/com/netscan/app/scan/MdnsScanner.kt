package com.netscan.app.scan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections

/**
 * Capa 2: mDNS / Bonjour / Zeroconf vía NsdManager.
 * Descubre servicios anunciados en la red y extrae IP, nombre y, si
 * el servicio lo publica en su TXT, el modelo del dispositivo.
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
        "_http._tcp.",         // NAS, routers, cámaras, muchos IoT
        "_workstation._tcp.",  // PCs Linux/avahi
        "_spotify-connect._tcp.",
        "_sonos._tcp."
    )

    private val resolveMutex = Mutex()

    /** Resultado por IP: nombre y, cuando aparece, modelo. */
    data class Hit(val ip: String, val name: String?, val model: String?, val type: String?)

    suspend fun discover(durationMs: Long = 6000): Map<String, Hit> {
        val results = Collections.synchronizedMap(HashMap<String, Hit>())
        val listeners = mutableListOf<NsdManager.DiscoveryListener>()

        for (type in serviceTypes) {
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    resolve(serviceInfo, results)
                }
            }
            try {
                nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
                listeners.add(listener)
            } catch (_: Exception) {}
        }

        delay(durationMs)

        for (l in listeners) {
            try { nsd.stopServiceDiscovery(l) } catch (_: Exception) {}
        }
        return HashMap(results)
    }

    private fun resolve(info: NsdServiceInfo, out: MutableMap<String, Hit>) {
        val listener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val host = serviceInfo.host ?: return
                val ip = host.hostAddress ?: return
                val name = serviceInfo.serviceName
                val model = extractModel(serviceInfo)
                val type = typeLabel(serviceInfo.serviceType)
                val existing = out[ip]
                out[ip] = Hit(
                    ip = ip,
                    name = name ?: existing?.name,
                    model = model ?: existing?.model,
                    type = type ?: existing?.type
                )
            }
        }
        try { nsd.resolveService(info, listener) } catch (_: Exception) {}
    }

    /** Busca el modelo en los atributos TXT del servicio. */
    private fun extractModel(info: NsdServiceInfo): String? {
        val attrs = try { info.attributes } catch (_: Exception) { null } ?: return null
        for (key in listOf("md", "model", "ty", "am")) {
            val bytes = attrs[key] ?: continue
            val value = bytes?.toString(Charsets.UTF_8)?.trim()
            if (!value.isNullOrEmpty()) return value
        }
        return null
    }

    private fun typeLabel(serviceType: String?): String? {
        val t = serviceType ?: return null
        return when {
            t.contains("googlecast") -> "Chromecast / Google TV"
            t.contains("airplay") || t.contains("raop") -> "Apple / AirPlay"
            t.contains("ipp") || t.contains("printer") -> "Impresora"
            t.contains("sonos") || t.contains("spotify") -> "Altavoz"
            t.contains("workstation") -> "PC"
            else -> null
        }
    }
}
