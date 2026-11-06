package com.netscan.app.scan

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URL

/** Un puerto reenviado desde internet hacia un equipo de tu red. */
data class PortMapping(
    val externalPort: Int,
    val protocol: String,
    val internalClient: String,
    val internalPort: Int,
    val description: String,
    val enabled: Boolean
)

/** Resultado de consultar el router por UPnP (IGD). */
data class IgdResult(
    val available: Boolean,
    val externalIp: String?,
    val mappings: List<PortMapping>
)

/**
 * Habla con el router por UPnP/IGD para preguntar qué puertos tiene abiertos
 * hacia internet (reenvío de puertos). Es la causa más común de cámaras y
 * NAS expuestos sin querer. Solo consulta; no cambia nada.
 */
object IgdClient {

    suspend fun query(context: Context): IgdResult = withContext(Dispatchers.IO) {
        val location = discoverLocation(context) ?: return@withContext IgdResult(false, null, emptyList())
        val (controlUrl, serviceType) = findControl(location) ?: return@withContext IgdResult(false, null, emptyList())
        val extIp = soapExternalIp(controlUrl, serviceType)
        val maps = mutableListOf<PortMapping>()
        for (i in 0 until 60) {
            val m = soapMapping(controlUrl, serviceType, i) ?: break
            maps.add(m)
        }
        IgdResult(true, extIp, maps)
    }

    /** M-SEARCH para encontrar el InternetGatewayDevice y su LOCATION. */
    private fun discoverLocation(context: Context): String? {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("netscan-igd").apply {
            setReferenceCounted(true); try { acquire() } catch (_: Exception) {}
        }
        val search = (
            "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 2\r\n" +
            "ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n"
        ).toByteArray()
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket().apply { soTimeout = 1200; reuseAddress = true }
            val group = InetAddress.getByName("239.255.255.250")
            val pkt = DatagramPacket(search, search.size, InetSocketAddress(group, 1900))
            socket.send(pkt); socket.send(pkt)
            val buf = ByteArray(2048)
            val deadline = System.currentTimeMillis() + 4000
            while (System.currentTimeMillis() < deadline) {
                try {
                    val resp = DatagramPacket(buf, buf.size)
                    socket.receive(resp)
                    val text = String(resp.data, 0, resp.length)
                    headerValue(text, "LOCATION")?.let { return it }
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {
        } finally {
            try { socket?.close() } catch (_: Exception) {}
            try { lock.release() } catch (_: Exception) {}
        }
        return null
    }

    /** Lee la descripción del router y saca controlURL + serviceType de la conexión WAN. */
    private fun findControl(location: String): Pair<String, String>? {
        return try {
            val xml = fetch(location) ?: return null
            val base = tag(xml, "URLBase")?.trimEnd('/') ?: run {
                val u = URL(location); "${u.protocol}://${u.host}:${if (u.port == -1) 80 else u.port}"
            }
            // Buscamos el bloque <service> cuyo serviceType sea WANIPConnection o WANPPPConnection.
            val services = Regex("<service>(.*?)</service>", RegexOption.DOT_MATCHES_ALL).findAll(xml)
            for (m in services) {
                val block = m.groupValues[1]
                val type = tag(block, "serviceType") ?: continue
                if (type.contains("WANIPConnection") || type.contains("WANPPPConnection")) {
                    val ctrl = tag(block, "controlURL") ?: continue
                    val full = if (ctrl.startsWith("http")) ctrl else URL(URL(base + "/"), ctrl).toString()
                    return Pair(full, type)
                }
            }
            null
        } catch (_: Exception) { null }
    }

    private fun soapExternalIp(controlUrl: String, serviceType: String): String? {
        val body = soapEnvelope(serviceType, "GetExternalIPAddress", "")
        val resp = soap(controlUrl, serviceType, "GetExternalIPAddress", body) ?: return null
        return tag(resp, "NewExternalIPAddress")
    }

    private fun soapMapping(controlUrl: String, serviceType: String, index: Int): PortMapping? {
        val inner = "<NewPortMappingIndex>$index</NewPortMappingIndex>"
        val body = soapEnvelope(serviceType, "GetGenericPortMappingEntry", inner)
        val resp = soap(controlUrl, serviceType, "GetGenericPortMappingEntry", body) ?: return null
        val ext = tag(resp, "NewExternalPort")?.toIntOrNull() ?: return null
        return PortMapping(
            externalPort = ext,
            protocol = tag(resp, "NewProtocol") ?: "?",
            internalClient = tag(resp, "NewInternalClient") ?: "?",
            internalPort = tag(resp, "NewInternalPort")?.toIntOrNull() ?: 0,
            description = tag(resp, "NewPortMappingDescription") ?: "",
            enabled = tag(resp, "NewEnabled") != "0"
        )
    }

    private fun soapEnvelope(serviceType: String, action: String, inner: String): String =
        """<?xml version="1.0"?>""" +
        """<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""" +
        """<s:Body><u:$action xmlns:u="$serviceType">$inner</u:$action></s:Body></s:Envelope>"""

    private fun soap(controlUrl: String, serviceType: String, action: String, body: String): String? {
        return try {
            val c = (URL(controlUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 2500; readTimeout = 2500
                requestMethod = "POST"
                doOutput = true
                setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                setRequestProperty("SOAPAction", "\"$serviceType#$action\"")
            }
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }
            c.disconnect()
            if (code in 200..299) text else null
        } catch (_: Exception) { null }
    }

    private fun fetch(url: String): String? = try {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 2500; readTimeout = 2500
        }
        val t = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect(); t
    } catch (_: Exception) { null }

    private fun headerValue(response: String, header: String): String? {
        for (line in response.split("\r\n")) {
            val idx = line.indexOf(':')
            if (idx > 0 && line.substring(0, idx).trim().equals(header, true))
                return line.substring(idx + 1).trim()
        }
        return null
    }

    private fun tag(xml: String, name: String): String? =
        Regex("<(?:[\\w-]+:)?$name>(.*?)</(?:[\\w-]+:)?$name>", RegexOption.DOT_MATCHES_ALL)
            .find(xml)?.groupValues?.get(1)?.trim()?.ifEmpty { null }
}
