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

    private val WEB_PORTS = listOf(80, 8080, 443, 8443)
    // Puertos característicos: cada uno delata un tipo de aparato aunque no haya MAC.
    private val COMMON_PORTS = listOf(
        21,     // FTP (sin cifrar)
        22,     // SSH (Linux / servidor / Raspberry Pi)
        23,     // Telnet (IoT viejo)
        2323,   // Telnet alternativo (IoT)
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

    data class Hit(
        val openPorts: List<Int>,
        val server: String?,
        val title: String?,
        val webUrl: String?, // Primera interfaz web que respondió
        val certCn: String? = null // CN del certificado HTTPS (pista de modelo)
    )

    suspend fun probe(ip: String): Hit = coroutineScope {
        // Revisamos todos los puertos en paralelo para no alargar el escaneo.
        val open = COMMON_PORTS.map { port ->
            async(Dispatchers.IO) { if (isOpen(ip, port)) port else null }
        }.awaitAll().filterNotNull().sorted()

        var server: String? = null
        var title: String? = null
        var webUrl: String? = null
        var certCn: String? = null
        for (port in WEB_PORTS) {
            if (port !in open) continue
            val banner = readHttp(ip, port) ?: continue
            if (webUrl == null) {
                val scheme = if (port == 443 || port == 8443) "https" else "http"
                webUrl = if (port == 80 || port == 443) "$scheme://$ip/" else "$scheme://$ip:$port/"
            }
            server = server ?: banner.server
            title = title ?: banner.title
            certCn = certCn ?: banner.certCn
            if (server != null && title != null && certCn != null) break
        }
        Hit(open, server, title, webUrl, certCn)
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

    private data class HttpInfo(val server: String?, val title: String?, val certCn: String?)

    /** Lee Server, <title> y, en HTTPS, el nombre (CN) del certificado. */
    private fun readHttp(ip: String, port: Int): HttpInfo? {
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

            val certCn = if (conn is HttpsURLConnection) readCertCn(conn) else null
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
            HttpInfo(server, title, certCn)
        } catch (_: Exception) {
            null
        }
    }

    /** Extrae el CN del certificado del servidor; a veces trae la marca/modelo. */
    private fun readCertCn(conn: HttpsURLConnection): String? = try {
        val cert = conn.serverCertificates.firstOrNull() as? java.security.cert.X509Certificate
        val dn = cert?.subjectX500Principal?.name ?: return null
        val cn = Regex("CN=([^,]+)").find(dn)?.groupValues?.get(1)?.trim()
        // Descartamos CN genéricos (IP, localhost, comodines) que no identifican nada.
        cn?.takeIf {
            it.length in 2..40 && !it.equals("localhost", true) &&
            !it.startsWith("*") && !Regex("^[0-9.]+$").matches(it)
        }
    } catch (_: Exception) { null }

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
