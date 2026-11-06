package com.netscan.app.scan

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.wifi.WifiManager
import java.net.Inet4Address

/** Datos de la red Wi-Fi a la que está conectado el teléfono. */
data class LocalNet(
    val localIp: String,
    val prefixLength: Int,
    val gatewayIp: String?,
    val ssid: String?
) {
    /** ¿La IP pertenece a nuestra subred /24? (filtra respuestas de otras redes) */
    fun contains(ip: String): Boolean =
        ip.substringBeforeLast('.') == localIp.substringBeforeLast('.')

    /** Lista de IPs del mismo subred (solo /24 o más estrecho, para no barrer de más). */
    fun subnetHosts(): List<String> {
        val parts = localIp.split(".")
        if (parts.size != 4) return emptyList()
        val base = "${parts[0]}.${parts[1]}.${parts[2]}."
        // Nos limitamos a un /24 práctico: .1 a .254
        return (1..254).map { base + it }
    }
}

object NetworkInfo {

    fun current(context: Context): LocalNet? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return null
        val linkProps = cm.getLinkProperties(network) ?: return null

        var ip: String? = null
        var prefix = 24
        for (la: LinkAddress in linkProps.linkAddresses) {
            val addr = la.address
            if (addr is Inet4Address && !addr.isLoopbackAddress) {
                ip = addr.hostAddress
                prefix = la.prefixLength
                break
            }
        }
        if (ip == null) return null

        val gateway = linkProps.routes
            .firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }
            ?.gateway?.hostAddress

        val ssid = readSsid(context)
        return LocalNet(ip, prefix, gateway, ssid)
    }

    @Suppress("DEPRECATION")
    private fun readSsid(context: Context): String? {
        return try {
            val wifi = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val raw = wifi.connectionInfo?.ssid ?: return null
            if (raw == "<unknown ssid>") null else raw.trim('"')
        } catch (e: Exception) {
            null
        }
    }
}
