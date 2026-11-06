package com.netscan.app.scan

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL

/**
 * Capa 3: SSDP (descubrimiento UPnP). Muchos TVs, consolas, routers y
 * reproductores publican un XML con modelName y manufacturer exactos.
 */
class SsdpScanner(private val context: Context) {

    data class Hit(
        val ip: String,
        val friendlyName: String?,
        val manufacturer: String?,
        val model: String?,
        val deviceType: String?
    )

    private val mSearch = buildString {
        append("M-SEARCH * HTTP/1.1\r\n")
        append("HOST: 239.255.255.250:1900\r\n")
        append("MAN: \"ssdp:discovery\"\r\n")
        append("MX: 2\r\n")
        append("ST: ssdp:all\r\n")
        append("\r\n")
    }.toByteArray()

    suspend fun discover(durationMs: Long = 5000): Map<String, Hit> =
        withContext(Dispatchers.IO) {
            val locations = HashSet<String>()
            val ipByLocation = HashMap<String, String>()

            val wifi = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val lock = wifi.createMulticastLock("netscan-ssdp").apply {
                setReferenceCounted(true)
                try { acquire() } catch (_: Exception) {}
            }

            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply {
                    reuseAddress = true
                    soTimeout = 1200
                    broadcast = true
                }
                val group = InetAddress.getByName("239.255.255.250")
                val packet = DatagramPacket(mSearch, mSearch.size, InetSocketAddress(group, 1900))
                socket.send(packet)
                socket.send(packet) // segundo envío por si se pierde

                val buf = ByteArray(2048)
                val deadline = System.currentTimeMillis() + durationMs
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val resp = DatagramPacket(buf, buf.size)
                        socket.receive(resp)
                        val text = String(resp.data, 0, resp.length)
                        val loc = headerValue(text, "LOCATION")
                        if (loc != null && locations.add(loc)) {
                            ipByLocation[loc] = resp.address.hostAddress ?: ""
                        }
                    } catch (_: Exception) {
                        // timeout de recepción: seguimos hasta el deadline
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { socket?.close() } catch (_: Exception) {}
                try { lock.release() } catch (_: Exception) {}
            }

            val out = HashMap<String, Hit>()
            for (loc in locations) {
                val hit = fetchDescription(loc, ipByLocation[loc] ?: "")
                if (hit != null) out[hit.ip] = hit
            }
            out
        }

    private fun fetchDescription(location: String, fallbackIp: String): Hit? {
        return try {
            val url = URL(location)
            val ip = url.host ?: fallbackIp
            val xml = url.openConnection().apply {
                connectTimeout = 2000
                readTimeout = 2000
            }.getInputStream().bufferedReader().use { it.readText() }

            val friendly = tag(xml, "friendlyName")
            val manufacturer = tag(xml, "manufacturer")
            val model = tag(xml, "modelName")
            val devType = tag(xml, "deviceType")
            if (friendly == null && manufacturer == null && model == null) null
            else Hit(ip, friendly, manufacturer, model, simplifyType(devType))
        } catch (_: Exception) {
            null
        }
    }

    private fun headerValue(response: String, header: String): String? {
        for (line in response.split("\r\n")) {
            val idx = line.indexOf(':')
            if (idx > 0 && line.substring(0, idx).trim().equals(header, ignoreCase = true)) {
                return line.substring(idx + 1).trim()
            }
        }
        return null
    }

    private fun tag(xml: String, name: String): String? {
        val m = Regex("<$name>(.*?)</$name>", RegexOption.DOT_MATCHES_ALL).find(xml)
        return m?.groupValues?.get(1)?.trim()?.ifEmpty { null }
    }

    private fun simplifyType(deviceType: String?): String? {
        val t = deviceType ?: return null
        return when {
            t.contains("MediaRenderer") || t.contains("tvdevice") -> "Smart TV / media"
            t.contains("InternetGatewayDevice") -> "Router"
            t.contains("Printer") -> "Impresora"
            t.contains("MediaServer") -> "Servidor multimedia / NAS"
            else -> null
        }
    }
}
