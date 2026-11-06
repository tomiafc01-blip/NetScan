package com.netscan.app.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Resultado de la prueba de velocidad a internet. */
data class SpeedResult(
    val downMbps: Double?,
    val upMbps: Double?,
    val pingMs: Double?,
    val error: String? = null
)

/**
 * Prueba de velocidad contra los puntos públicos de medición de Cloudflare.
 * Mide latencia, descarga y subida. Usa unos 15 MB de datos.
 */
object SpeedTest {

    private const val DOWN = "https://speed.cloudflare.com/__down?bytes="
    private const val UP = "https://speed.cloudflare.com/__up"

    enum class Phase { PING, DOWNLOAD, UPLOAD, DONE }

    suspend fun run(
        onPhase: (Phase, Double?) -> Unit = { _, _ -> }
    ): SpeedResult = withContext(Dispatchers.IO) {
        try {
            onPhase(Phase.PING, null)
            val ping = measurePing()

            onPhase(Phase.DOWNLOAD, null)
            val down = measureDownload(10_000_000) { onPhase(Phase.DOWNLOAD, it) }

            onPhase(Phase.UPLOAD, null)
            val up = measureUpload(5_000_000) { onPhase(Phase.UPLOAD, it) }

            onPhase(Phase.DONE, null)
            SpeedResult(down, up, ping)
        } catch (e: Exception) {
            SpeedResult(null, null, null, error = e.message ?: "No se pudo conectar a internet")
        }
    }

    private fun measurePing(): Double? {
        val times = mutableListOf<Double>()
        repeat(5) {
            try {
                val t0 = System.nanoTime()
                val c = (URL(DOWN + "0").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000; readTimeout = 4000; requestMethod = "GET"
                }
                c.inputStream.use { it.readBytes() }
                c.disconnect()
                times.add((System.nanoTime() - t0) / 1_000_000.0)
            } catch (_: Exception) {}
        }
        return if (times.isEmpty()) null else times.sorted()[times.size / 2]
    }

    private fun measureDownload(bytes: Int, onMbps: (Double) -> Unit): Double? {
        val t0 = System.nanoTime()
        var read = 0L
        val c = (URL(DOWN + bytes).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000; readTimeout = 15000
        }
        c.inputStream.use { ins ->
            val buf = ByteArray(32 * 1024)
            while (true) {
                val n = ins.read(buf); if (n < 0) break
                read += n
                val secs = (System.nanoTime() - t0) / 1e9
                if (secs > 0) onMbps(read * 8 / secs / 1e6)
                if (secs > 15) break
            }
        }
        c.disconnect()
        val secs = (System.nanoTime() - t0) / 1e9
        return if (secs <= 0) null else read * 8 / secs / 1e6
    }

    private fun measureUpload(bytes: Int, onMbps: (Double) -> Unit): Double? {
        val t0 = System.nanoTime()
        val c = (URL(UP).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000; readTimeout = 15000
            doOutput = true; requestMethod = "POST"
            setFixedLengthStreamingMode(bytes)
            setRequestProperty("Content-Type", "application/octet-stream")
        }
        val chunk = ByteArray(32 * 1024)
        var sent = 0L
        val os: OutputStream = c.outputStream
        os.use {
            while (sent < bytes) {
                val n = minOf(chunk.size.toLong(), bytes - sent).toInt()
                it.write(chunk, 0, n); sent += n
                val secs = (System.nanoTime() - t0) / 1e9
                if (secs > 0) onMbps(sent * 8 / secs / 1e6)
                if (secs > 15) break
            }
        }
        c.responseCode
        c.disconnect()
        val secs = (System.nanoTime() - t0) / 1e9
        return if (secs <= 0) null else sent * 8 / secs / 1e6
    }
}
