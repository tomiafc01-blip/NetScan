package com.netscan.app.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.sqrt

/** Resultado de la prueba de velocidad a internet. */
data class SpeedResult(
    val downMbps: Double?,
    val upMbps: Double?,
    val idlePingMs: Double?,
    val loadedPingMs: Double?,   // latencia mientras se descarga (bufferbloat)
    val jitterMs: Double?,
    val bufferbloatGrade: String?, // A (excelente) … F (malo)
    val error: String? = null
)

/**
 * Prueba de velocidad contra los puntos públicos de Cloudflare.
 * Usa varios flujos en paralelo (un solo flujo subestima en fibra),
 * mide jitter y la latencia bajo carga para calificar el bufferbloat.
 * Consume ~40 MB de datos.
 */
object SpeedTest {

    private const val DOWN = "https://speed.cloudflare.com/__down?bytes="
    private const val UP = "https://speed.cloudflare.com/__up"
    private const val STREAMS = 4

    enum class Phase { PING, DOWNLOAD, UPLOAD, DONE }

    suspend fun run(onPhase: (Phase, Double?) -> Unit = { _, _ -> }): SpeedResult = withContext(Dispatchers.IO) {
        try {
            onPhase(Phase.PING, null)
            val pings = measurePings(8)
            val idle = median(pings)
            val jitter = stddev(pings)

            onPhase(Phase.DOWNLOAD, null)
            val loaded = ArrayList<Double>()
            val down = measureDownloadParallel(
                onMbps = { onPhase(Phase.DOWNLOAD, it) },
                onLoadedPing = { loaded.add(it) }
            )

            onPhase(Phase.UPLOAD, null)
            val up = measureUpload(10_000_000) { onPhase(Phase.UPLOAD, it) }

            onPhase(Phase.DONE, null)
            val loadedMed = median(loaded)
            SpeedResult(down, up, idle, loadedMed, jitter, grade(idle, loadedMed))
        } catch (e: Exception) {
            SpeedResult(null, null, null, null, null, null, e.message ?: "No se pudo conectar")
        }
    }

    private fun measurePings(n: Int): List<Double> {
        val times = ArrayList<Double>()
        repeat(n) {
            try {
                val t0 = System.nanoTime()
                val c = (URL(DOWN + "0").openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000; readTimeout = 4000
                }
                c.inputStream.use { it.readBytes() }
                c.disconnect()
                times.add((System.nanoTime() - t0) / 1_000_000.0)
            } catch (_: Exception) {}
        }
        return times
    }

    private suspend fun measureDownloadParallel(
        onMbps: (Double) -> Unit,
        onLoadedPing: (Double) -> Unit
    ): Double? = coroutineScope {
        val total = AtomicLong(0)
        val t0 = System.nanoTime()
        val durationNs = 10_000_000_000L

        // Reporta velocidad agregada en vivo.
        val reporter = launch(Dispatchers.IO) {
            while (isActive && System.nanoTime() - t0 < durationNs) {
                delay(300)
                val secs = (System.nanoTime() - t0) / 1e9
                if (secs > 0) onMbps(total.get() * 8 / secs / 1e6)
            }
        }
        // Mide latencia bajo carga en paralelo.
        val latencyProbe = launch(Dispatchers.IO) {
            while (isActive && System.nanoTime() - t0 < durationNs) {
                try {
                    val p0 = System.nanoTime()
                    val c = (URL(DOWN + "0").openConnection() as HttpURLConnection).apply {
                        connectTimeout = 3000; readTimeout = 3000
                    }
                    c.inputStream.use { it.readBytes() }
                    c.disconnect()
                    onLoadedPing((System.nanoTime() - p0) / 1_000_000.0)
                } catch (_: Exception) {}
                delay(250)
            }
        }

        val jobs = (0 until STREAMS).map {
            async(Dispatchers.IO) {
                while (System.nanoTime() - t0 < durationNs) {
                    try {
                        val c = (URL(DOWN + 25_000_000).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 5000; readTimeout = 10000
                        }
                        c.inputStream.use { ins ->
                            val buf = ByteArray(64 * 1024)
                            while (System.nanoTime() - t0 < durationNs) {
                                val n = ins.read(buf); if (n < 0) break
                                total.addAndGet(n.toLong())
                            }
                        }
                        c.disconnect()
                    } catch (_: Exception) { break }
                }
            }
        }
        jobs.awaitAll()
        reporter.cancel(); latencyProbe.cancel()
        val secs = (System.nanoTime() - t0) / 1e9
        if (secs <= 0 || total.get() == 0L) null else total.get() * 8 / secs / 1e6
    }

    private suspend fun measureUpload(bytes: Int, onMbps: (Double) -> Unit): Double? =
        withContext(Dispatchers.IO) {
            try {
                val t0 = System.nanoTime()
                val c = (URL(UP).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000; readTimeout = 15000
                    doOutput = true; requestMethod = "POST"
                    setFixedLengthStreamingMode(bytes)
                    setRequestProperty("Content-Type", "application/octet-stream")
                }
                val chunk = ByteArray(64 * 1024)
                var sent = 0L
                c.outputStream.use {
                    while (sent < bytes) {
                        val n = minOf(chunk.size.toLong(), bytes - sent).toInt()
                        it.write(chunk, 0, n); sent += n
                        val secs = (System.nanoTime() - t0) / 1e9
                        if (secs > 0) onMbps(sent * 8 / secs / 1e6)
                        if (secs > 15) break
                    }
                }
                c.responseCode; c.disconnect()
                val secs = (System.nanoTime() - t0) / 1e9
                if (secs <= 0) null else sent * 8 / secs / 1e6
            } catch (_: Exception) { null }
        }

    private fun grade(idle: Double?, loaded: Double?): String? {
        if (idle == null || loaded == null) return null
        return when ((loaded - idle).coerceAtLeast(0.0)) {
            in 0.0..30.0 -> "A"
            in 30.0..60.0 -> "B"
            in 60.0..100.0 -> "C"
            in 100.0..200.0 -> "D"
            else -> "F"
        }
    }

    private fun median(v: List<Double>): Double? =
        if (v.isEmpty()) null else v.sorted()[v.size / 2]

    private fun stddev(v: List<Double>): Double? {
        if (v.size < 2) return null
        val m = v.average()
        return sqrt(v.sumOf { (it - m) * (it - m) } / v.size)
    }
}
