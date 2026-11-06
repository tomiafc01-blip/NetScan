package com.netscan.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.netscan.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Auto-actualización desde los Releases de GitHub.
 * El workflow publica cada compilación como Release con tag
 * "v1.0.<run_number>" y el APK adjunto. Aquí se compara ese número
 * con el versionCode instalado y, si hay uno mayor, se descarga e instala.
 */
object UpdateManager {

    private const val OWNER = "tomiafc01-blip"
    private const val REPO = "NetScan"
    private const val API = "https://api.github.com/repos/$OWNER/$REPO/releases/latest"

    data class Release(
        val buildNumber: Int,
        val versionName: String,
        val apkUrl: String?,
        val notes: String?
    )

    /** Consulta el último Release. Devuelve null si no hay o falla la red. */
    suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "NetScan-Updater")
            }
            if (conn.responseCode != 200) { conn.disconnect(); return@withContext null }
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val obj = JSONObject(json)
            val tag = obj.optString("tag_name")              // "v1.0.42"
            val build = tag.substringAfterLast('.').toIntOrNull() ?: return@withContext null
            val name = tag.removePrefix("v").ifEmpty { tag }
            val notes = obj.optString("body").ifBlank { null }

            // Buscar el asset .apk
            var apkUrl: String? = null
            val assets = obj.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.getJSONObject(i)
                    if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                        apkUrl = a.optString("browser_download_url")
                        break
                    }
                }
            }
            Release(build, name, apkUrl, notes)
        } catch (_: Exception) {
            null
        }
    }

    /** ¿El Release es más nuevo que lo instalado? */
    fun isNewer(release: Release): Boolean = release.buildNumber > BuildConfig.VERSION_CODE

    val currentVersionName: String get() = BuildConfig.VERSION_NAME

    /**
     * Descarga el APK a la carpeta privada de la app.
     * [onProgress] recibe 0..100 (o -1 si el tamaño es desconocido).
     */
    suspend fun download(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.getExternalFilesDir(null), "apk").apply { mkdirs() }
            val file = File(dir, "NetScan-update.apk")
            if (file.exists()) file.delete()

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "NetScan-Updater")
            }
            val total = conn.contentLength
            conn.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buf = ByteArray(16 * 1024)
                    var read: Int
                    var done = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        done += read
                        if (total > 0) onProgress(((done * 100) / total).toInt())
                        else onProgress(-1)
                    }
                }
            }
            conn.disconnect()
            file
        } catch (_: Exception) {
            null
        }
    }

    /** ¿La app tiene permiso para instalar APKs? (Android 8+). */
    fun canInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
    }

    /** Abre los ajustes para que el usuario permita instalar desde NetScan. */
    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try { context.startActivity(intent) } catch (_: Exception) {}
        }
    }

    /** Lanza el instalador del sistema con el APK descargado. */
    fun install(context: Context, apk: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", apk
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
