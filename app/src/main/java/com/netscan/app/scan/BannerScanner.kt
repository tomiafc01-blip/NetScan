package com.netscan.app.scan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Capa 5: puertos abiertos + banners HTTP.
 * Lee la cabecera Server y el <title> de la interfaz web, que suelen
 * revelar el modelo de routers, cámaras, NAS e IoT.
 */
object BannerScanner {

    private val WEB_PORTS = listOf(80, 8080, 443)
    // Puertos característicos: cada uno delata un tipo de aparato aunque no haya MAC.
    private val COMMON_PORTS = listOf(
        22,     // SSH (Linux / servidor / Raspberry Pi)
        23,     // Telnet (IoT viejo)
        53,     // DNS (router)
        80, 443, 8080, 8443, // web
        139, 445,            // SMB (PC Windows / NAS)
        515, 631, 9100,      // impresoras (LPD / IPP / RAW)
        548,    // AFP (Apple / NAS)
        554, 8554, // RTSP (cámara IP)
        1400,   // Sonos
        1883, 8883, // MQTT (IoT / domótica)
        2869,   // UPnP Windows
        3389,   // RDP (Windows)
        5000, 5001, // Synology / UPnP
        5555,   // ADB (Android / Fire TV)
        7000,   // AirPlay
        8009,   // Chromecast / Google Cast
        9000,   // varios NAS / media
        32469,  // Plex DLNA
        62078   // lockdownd (iPhone / iPad)
    )

    data class Hit(val openPorts: List<Int>, val server: String?, val title: String?)

    suspend fun probe(ip: String): Hit = coroutineScope {
        // Revisamos todos los puertos en paralelo para no alargar el escaneo.
        val open = COMMON_PORTS.map { port ->
            async(Dispatchers.IO) { if (isOpen(ip, port)) port else null }
        }.awaitAll().filterNotNull().sorted()

        var server: String? = null
        var title: String? = null
        for (port in WEB_PORTS) {
            if (port !in open) continue
            val banner = readHttp(ip, port) ?: continue
            server = server ?: banner.first
            title = title ?: banner.second
            if (server != null && title != null) break
        }
        Hit(open, server, title)
    }


    private fun isOpen(ip: String, port: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(ip, port), 300)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    /** Devuelve (Server, title). */
    private fun readHttp(ip: String, port: Int): Pair<String?, String?>? {
        val scheme = if (port == 443 || port == 8443) "https" else "http"
        return try {
            val url = URL("$scheme://$ip:$port/")
            val conn = url.openConnection() as HttpURLConnection
            if (conn is HttpsURLConnection) {
                conn.sslSocketFactory = trustAllFactory()
                conn.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, _ -> true }
            }
            conn.connectTimeout = 2000
            conn.readTimeout = 2000
            conn.instanceFollowRedirects = false
            conn.requestMethod = "GET"
            conn.connect()

            val server = conn.getHeaderField("Server")
            val body = try {
                conn.inputStream.bufferedReader().use { r ->
                    val sb = StringBuilder()
                    var line = r.readLine()
                    var count = 0
                    while (line != null && count < 60) { // primeras líneas bastan
                        sb.append(line)
                        if (sb.contains("</title>", ignoreCase = true)) break
                        line = r.readLine(); count++
                    }
                    sb.toString()
                }
            } catch (_: Exception) { "" }
            conn.disconnect()

            val title = Regex("<title>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                .find(body)?.groupValues?.get(1)?.trim()?.ifEmpty { null }
            Pair(server, title)
        } catch (_: Exception) {
            null
        }
    }

    private fun trustAllFactory() = try {
        val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers() = arrayOf<java.security.cert.X509Certificate>()
        })
        SSLContext.getInstance("TLS").apply { init(null, trustAll, java.security.SecureRandom()) }.socketFactory
    } catch (e: Exception) {
        HttpsURLConnection.getDefaultSSLSocketFactory()
    }
}
