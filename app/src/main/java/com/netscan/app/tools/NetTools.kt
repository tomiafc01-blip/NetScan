package com.netscan.app.tools

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.netscan.app.scan.Device
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Resultado de una prueba de latencia. */
data class PingResult(
    val sent: Int,
    val received: Int,
    val minMs: Double?,
    val avgMs: Double?,
    val maxMs: Double?
) {
    val lossPercent: Int get() = if (sent == 0) 100 else ((sent - received) * 100) / sent
}

/** Herramientas por dispositivo: ping, Wake-on-LAN y exportar. */
object NetTools {

    /**
     * Prueba de latencia. Usa el comando `ping` del sistema (no requiere root);
     * si no está disponible, mide con InetAddress.isReachable.
     */
    suspend fun ping(ip: String, count: Int = 10): PingResult = withContext(Dispatchers.IO) {
        systemPing(ip, count) ?: fallbackPing(ip, count)
    }

    private fun systemPing(ip: String, count: Int): PingResult? {
        return try {
            val proc = ProcessBuilder("ping", "-c", "$count", "-i", "0.2", "-W", "1", ip)
                .redirectErrorStream(true).start()
            val times = mutableListOf<Double>()
            proc.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    val m = Regex("time[=<]([0-9.]+)").find(line) ?: continue
                    m.groupValues[1].toDoubleOrNull()?.let { times.add(it) }
                }
            }
            proc.waitFor()
            // Código 2 = error del comando (p. ej. no existe o sin permiso).
            if (proc.exitValue() == 2 && times.isEmpty()) return null
            build(count, times)
        } catch (_: Exception) {
            null
        }
    }

    private fun fallbackPing(ip: String, count: Int): PingResult {
        val times = mutableListOf<Double>()
        val addr = try { InetAddress.getByName(ip) } catch (_: Exception) { null }
        repeat(count) {
            val t0 = System.nanoTime()
            val ok = try { addr?.isReachable(1000) == true } catch (_: Exception) { false }
            if (ok) times.add((System.nanoTime() - t0) / 1_000_000.0)
        }
        return build(count, times)
    }

    private fun build(sent: Int, times: List<Double>) = PingResult(
        sent = sent,
        received = times.size,
        minMs = times.minOrNull(),
        avgMs = if (times.isEmpty()) null else times.average(),
        maxMs = times.maxOrNull()
    )

    /**
     * Wake-on-LAN: envía el "paquete mágico" (6×FF + 16×MAC) por broadcast
     * a los puertos 9 y 7. El equipo debe tener WOL activado en BIOS/adaptador.
     */
    suspend fun wakeOnLan(mac: String, deviceIp: String): Boolean = withContext(Dispatchers.IO) {
        val hex = mac.replace(":", "").replace("-", "")
        if (hex.length != 12) return@withContext false
        val macBytes = ByteArray(6) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        val payload = ByteArray(6 + 16 * 6)
        for (i in 0 until 6) payload[i] = 0xFF.toByte()
        for (i in 0 until 16) System.arraycopy(macBytes, 0, payload, 6 + i * 6, 6)

        val subnetBroadcast = deviceIp.substringBeforeLast('.') + ".255"
        var sentAny = false
        try {
            DatagramSocket().use { s ->
                s.broadcast = true
                for (target in listOf(subnetBroadcast, "255.255.255.255")) {
                    val addr = InetAddress.getByName(target)
                    for (port in listOf(9, 7)) {
                        try {
                            s.send(DatagramPacket(payload, payload.size, addr, port))
                            sentAny = true
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}
        sentAny
    }

    /** Exporta el escaneo como CSV y abre el menú "Compartir". */
    fun exportCsv(context: Context, network: String, devices: List<Device>) {
        val date = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US).format(Date())
        val file = File(context.cacheDir, "netscan_$date.csv")
        val sb = StringBuilder()
        sb.append("Nombre,IP,MAC,Fabricante,Modelo,Tipo,Confianza,Latencia ms,Puertos,Servicios,Detectado por,Nuevo\n")
        for (d in devices) {
            sb.append(listOf(
                d.bestLabel(), d.ip, d.mac ?: "", d.vendor ?: "", d.model ?: "",
                d.deviceType ?: "", d.confidence().label, d.latencyMs?.toString() ?: "",
                d.openPorts.joinToString(" "), d.services.joinToString(" "),
                d.sources.joinToString(" "), if (d.isNew) "sí" else ""
            ).joinToString(",") { csv(it) })
            sb.append('\n')
        }
        // BOM para que Excel respete los acentos.
        file.writeText("﻿" + sb.toString(), Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Escaneo NetScan · $network")
            putExtra(Intent.EXTRA_TEXT, "${devices.size} dispositivos en $network")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "Exportar escaneo").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun csv(v: String): String =
        if (v.any { it == ',' || it == '"' || it == '\n' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
}
