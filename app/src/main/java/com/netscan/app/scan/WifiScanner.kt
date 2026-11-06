package com.netscan.app.scan

import android.content.Context
import android.net.wifi.WifiManager

/** Una red Wi-Fi vecina detectada en el aire. */
data class WifiAp(
    val ssid: String,
    val bssid: String,
    val rssi: Int,       // dBm (más cerca de 0 = más fuerte); 0 = sin dato
    val channel: Int,
    val band: String,    // "2.4 GHz", "5 GHz", "6 GHz"
    val widthMhz: Int,   // 20, 40, 80, 160… (0 = desconocido)
    val security: String, // "Abierta", "WEP", "WPA2", "WPA3"…
    val isCurrent: Boolean
) {
    /** ¿La lectura de señal es válida? (0 o positivo = no fiable) */
    val hasSignal: Boolean get() = rssi < 0

    /** Calidad 0..100 a partir del RSSI; -1 si no hay dato. */
    val quality: Int get() = when {
        !hasSignal -> -1
        rssi >= -50 -> 100
        rssi <= -100 -> 0
        else -> 2 * (rssi + 100)
    }

    /** Redes sin cifrado real: conviene avisar. */
    val isInsecure: Boolean get() = security == "Abierta" || security == "WEP"
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
                    widthMhz = widthOf(r),
                    security = securityOf(r.capabilities),
                    isCurrent = r.BSSID != null && r.BSSID == currentBssid
                )
            }.sortedWith(
                // Primero la conectada, luego por señal válida (fuerte→débil),
                // y al final las que no dan lectura de señal.
                compareByDescending<WifiAp> { it.isCurrent }
                    .thenByDescending { it.hasSignal }
                    .thenByDescending { it.rssi }
            )
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

    /** Traduce el campo `capabilities` de Android a un tipo de seguridad corto. */
    private fun securityOf(caps: String?): String {
        val c = caps ?: return "?"
        return when {
            c.contains("WPA3") || c.contains("SAE") -> "WPA3"
            c.contains("WPA2") || c.contains("RSN") -> "WPA2"
            c.contains("WPA") -> "WPA"
            c.contains("WEP") -> "WEP"
            c.contains("ESS") -> "Abierta"
            else -> "?"
        }
    }

    @Suppress("DEPRECATION")
    private fun widthOf(r: android.net.wifi.ScanResult): Int = when (r.channelWidth) {
        android.net.wifi.ScanResult.CHANNEL_WIDTH_20MHZ -> 20
        android.net.wifi.ScanResult.CHANNEL_WIDTH_40MHZ -> 40
        android.net.wifi.ScanResult.CHANNEL_WIDTH_80MHZ -> 80
        android.net.wifi.ScanResult.CHANNEL_WIDTH_160MHZ -> 160
        android.net.wifi.ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
        else -> 0
    }

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
