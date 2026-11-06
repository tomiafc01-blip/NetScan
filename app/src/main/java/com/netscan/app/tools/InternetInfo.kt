package com.netscan.app.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Datos de tu conexión a internet vistos desde fuera. */
data class InternetData(
    val publicIp: String?,
    val isp: String?,
    val city: String?,
    val region: String?,
    val country: String?,
    val error: String? = null
) {
    fun location(): String? =
        listOfNotNull(city, region, country).distinct().joinToString(", ").ifEmpty { null }
}

/** Consulta tu IP pública, tu proveedor (ISP) y tu ubicación aproximada. */
object InternetInfo {

    suspend fun fetch(): InternetData = withContext(Dispatchers.IO) {
        // Probamos dos servicios por si uno falla o limita peticiones.
        fromIpapi() ?: fromIfconfig() ?: InternetData(null, null, null, null, null, "Sin conexión a internet")
    }

    private fun fromIpapi(): InternetData? = try {
        val json = get("https://ipapi.co/json/") ?: return null
        val o = JSONObject(json)
        if (o.optBoolean("error", false)) null
        else InternetData(
            publicIp = o.optString("ip").ifEmpty { null },
            isp = (o.optString("org").ifEmpty { null }) ?: o.optString("asn").ifEmpty { null },
            city = o.optString("city").ifEmpty { null },
            region = o.optString("region").ifEmpty { null },
            country = o.optString("country_name").ifEmpty { null }
        )
    } catch (_: Exception) { null }

    private fun fromIfconfig(): InternetData? = try {
        val json = get("https://ifconfig.co/json") ?: return null
        val o = JSONObject(json)
        InternetData(
            publicIp = o.optString("ip").ifEmpty { null },
            isp = o.optString("asn_org").ifEmpty { null },
            city = o.optString("city").ifEmpty { null },
            region = o.optString("region_name").ifEmpty { null },
            country = o.optString("country").ifEmpty { null }
        )
    } catch (_: Exception) { null }

    private fun get(url: String): String? {
        return try {
            val c = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000; readTimeout = 5000
                setRequestProperty("User-Agent", "NetScan")
                setRequestProperty("Accept", "application/json")
            }
            if (c.responseCode !in 200..299) { c.disconnect(); return null }
            val txt = c.inputStream.bufferedReader().use { it.readText() }
            c.disconnect()
            txt
        } catch (_: Exception) { null }
    }
}
