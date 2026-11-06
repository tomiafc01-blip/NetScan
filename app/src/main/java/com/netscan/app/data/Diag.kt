package com.netscan.app.data

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Registro de diagnóstico: guarda errores (incluidos los cierres inesperados)
 * en un archivo local para que el usuario pueda compartir un reporte.
 * Todo queda en el teléfono hasta que la persona decide compartirlo.
 */
object Diag {

    private const val MAX = 200
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    private var appCtx: Context? = null
    private var file: File? = null
    private val entries = ArrayDeque<String>()

    /** Instala el capturador de cierres inesperados. Llamar al arrancar la app. */
    fun install(context: Context) {
        val app = context.applicationContext
        appCtx = app
        file = File(app.filesDir, "diag.log")
        load()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try { record("CIERRE", "La app se cerró en el hilo \"${thread.name}\"", error) } catch (_: Throwable) {}
            previous?.uncaughtException(thread, error)
        }
        log("INFO", "App iniciada")
    }

    /** Registra un mensaje y, si lo hay, el detalle de un error. */
    fun log(tag: String, message: String, error: Throwable? = null) {
        try { record(tag, message, error) } catch (_: Throwable) {}
    }

    @Synchronized
    private fun record(tag: String, message: String, error: Throwable?) {
        val sb = StringBuilder()
        sb.append(fmt.format(Date())).append("  [").append(tag).append("] ").append(message)
        error?.let { sb.append('\n').append(stackOf(it)) }
        entries.addLast(sb.toString())
        while (entries.size > MAX) entries.removeFirst()
        persist()
    }

    @Synchronized
    fun entries(): List<String> = entries.toList()

    @Synchronized
    fun clear() {
        entries.clear()
        persist()
    }

    /** Reporte completo con datos del equipo, listo para compartir. */
    fun report(): String {
        val sb = StringBuilder()
        sb.append(header()).append("\n\n")
        val list = entries()
        if (list.isEmpty()) sb.append("(sin errores registrados)")
        else list.asReversed().forEach { sb.append(it).append("\n\n") } // más recientes primero
        return sb.toString().trim()
    }

    fun header(): String {
        val ctx = appCtx
        var version = "?"
        var pkg = "com.netscan.app"
        if (ctx != null) {
            try {
                val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
                version = "${pi.versionName} (${pi.longVersionCodeCompat()})"
                pkg = ctx.packageName
            } catch (_: Exception) {}
        }
        return buildString {
            append("=== Reporte NetScan ===\n")
            append("App: $pkg  v$version\n")
            append("Equipo: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("Idioma: ${Locale.getDefault()}\n")
            append("Fecha del reporte: ${fmt.format(Date())}\n")
            append("Errores registrados: ${entries().size}")
        }
    }

    private fun stackOf(error: Throwable): String {
        val sw = StringWriter()
        error.printStackTrace(PrintWriter(sw))
        // Limitamos el tamaño para no guardar trazas gigantes.
        return sw.toString().take(4000)
    }

    @Synchronized
    private fun persist() {
        try { file?.writeText(entries.joinToString("\n\u0001\n")) } catch (_: Exception) {}
    }

    private fun load() {
        try {
            val f = file ?: return
            if (!f.exists()) return
            val parts = f.readText().split("\n\u0001\n").filter { it.isNotBlank() }
            entries.clear()
            entries.addAll(parts.takeLast(MAX))
        } catch (_: Exception) {}
    }

    private fun android.content.pm.PackageInfo.longVersionCodeCompat(): Long =
        if (Build.VERSION.SDK_INT >= 28) longVersionCode else @Suppress("DEPRECATION") versionCode.toLong()
}
