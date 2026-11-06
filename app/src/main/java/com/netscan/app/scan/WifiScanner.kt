package com.netscan.app.scan

import android.content.Context
import android.net.wifi.WifiManager

/** Una red Wi-Fi vecina detectada en el aire. */
data class WifiAp(
    val ssid: String,
    val bssid: String,
    val rssi: Int,       // dBm (más cerca de 0 = más fuerte)
    val channel: Int,
    val band: String,    // "2.4 GHz", "5 GHz", "6 GHz"
    val isCurrent: Boolean
) {
    /** Calidad 0..100 a partir del RSSI. */
    val quality: Int get() = when {
        rssi >= -50 -> 100
        rssi <= -100 -> 0
        else -> 2 * (rssi + 100)
    }
}

/** Lee las redes Wi-Fi cercanas y la señal de la red actual. */
object WifiScanner {

    @Suppress("DEPRECATION")
    fun nearby(context: Context): List<WifiAp> {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val currentBssid = try { wifi.connectionInfo?.bssid } catch (_: Exception) { null }
        return try {
            wifi.scanResults.map { r ->
                val freq = r.frequency
                WifiAp(
                    ssid = r.SSID?.ifEmpty { "(red oculta)" } ?: "(red oculta)",
                    bssid = r.BSSID ?: "",
                    rssi = r.level,
                    channel = channelFor(freq),
                    band = bandFor(freq),
                    isCurrent = r.BSSID != null && r.BSSID == currentBssid
                )
            }.sortedByDescending { it.rssi }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** RSSI (dBm) de la red a la que estás conectado, o null. */
    @Suppress("DEPRECATION")
    fun currentRssi(context: Context): Int? = try {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifi.connectionInfo?.rssi?.takeIf { it != -127 && it != 0 }
    } catch (_: Exception) { null }

    private fun bandFor(freq: Int): String = when {
        freq in 2400..2500 -> "2.4 GHz"
        freq in 5000..5900 -> "5 GHz"
        freq in 5925..7125 -> "6 GHz"
        else -> "?"
    }

    private fun channelFor(freq: Int): Int = when {
        freq == 2484 -> 14
        freq in 2412..2472 -> (freq - 2412) / 5 + 1
        freq in 5000..5900 -> (freq - 5000) / 5
        freq in 5925..7125 -> (freq - 5950) / 5 + 1
        else -> 0
    }
}
