package com.netscan.app.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.InetSocketAddress
import java.net.Socket

/** Resultado de un escaneo de puertos bajo demanda. */
data class PortScanResult(val ip: String, val openPorts: List<Int>, val scanned: Int)

/**
 * Escaneo de puertos TCP bajo demanda (más completo que el del escaneo normal).
 * Limita cuántas conexiones hay a la vez para no saturar el teléfono ni la red.
 */
object PortScanner {

    /** Nombres amigables de los puertos más conocidos. */
    val names: Map<Int, String> = mapOf(
        20 to "FTP datos", 21 to "FTP", 22 to "SSH", 23 to "Telnet", 25 to "SMTP",
        53 to "DNS", 80 to "HTTP", 110 to "POP3", 139 to "SMB", 143 to "IMAP",
        443 to "HTTPS", 445 to "SMB", 515 to "Impresión LPD", 548 to "AFP",
        554 to "RTSP (cámara)", 631 to "Impresión IPP", 993 to "IMAP TLS",
        1400 to "Sonos", 1883 to "MQTT", 2323 to "Telnet", 3306 to "MySQL",
        3389 to "Escritorio remoto", 5000 to "UPnP / Synology", 5353 to "mDNS",
        5555 to "ADB", 7000 to "AirPlay", 8008 to "Cast", 8009 to "Cast",
        8080 to "HTTP alt", 8443 to "HTTPS alt", 8883 to "MQTT TLS", 9100 to "Impresión RAW",
        32469 to "Plex", 62078 to "iPhone"
    )

    /**
     * Escanea [from]..[to] en [ip]. [onProgress] recibe (revisados, total).
     */
    suspend fun scan(
        ip: String,
        from: Int = 1,
        to: Int = 1024,
        timeoutMs: Int = 300,
        onProgress: (Int, Int) -> Unit = { _, _ -> }
    ): PortScanResult = coroutineScope {
        val ports = (from..to).toList()
        val total = ports.size
        val gate = Semaphore(128) // como máximo 128 sockets a la vez
        var done = 0
        val lock = Any()

        val open = ports.map { port ->
            async(Dispatchers.IO) {
                val isOpen = gate.withPermit { isOpen(ip, port, timeoutMs) }
                synchronized(lock) {
                    done++
                    if (done % 32 == 0 || done == total) onProgress(done, total)
                }
                if (isOpen) port else null
            }
        }.awaitAll().filterNotNull().sorted()

        PortScanResult(ip, open, total)
    }

    private fun isOpen(ip: String, port: Int, timeoutMs: Int): Boolean = try {
        Socket().use { it.connect(InetSocketAddress(ip, port), timeoutMs); true }
    } catch (_: Exception) { false }
}
