package com.netscan.app.scan

/** Un dispositivo descubierto en la red local. */
data class Device(
    val ip: String,
    var mac: String? = null,
    var vendor: String? = null,      // Fabricante por OUI del MAC
    var hostname: String? = null,    // Nombre de host (mDNS / NetBIOS / DNS inverso)
    var model: String? = null,       // Modelo exacto si algún servicio lo publica
    var deviceType: String? = null,  // TV, Impresora, Router, Teléfono, PC...
    var openPorts: MutableList<Int> = mutableListOf(),
    var sources: MutableSet<String> = mutableSetOf(), // Qué capas aportaron datos
    var isGateway: Boolean = false,
    var isThisDevice: Boolean = false
) {
    /** Mejor etiqueta disponible para mostrar al usuario. */
    fun bestLabel(): String {
        model?.let { return it }
        hostname?.takeIf { !looksLikeHash(it) }?.let { return it }
        vendor?.takeIf { it != "MAC aleatoria (privado)" }?.let { return it }
        deviceType?.let { return it }
        return "Dispositivo desconocido"
    }

    /** Subtítulo con la mejor pista secundaria (sin repetir la etiqueta principal). */
    fun subLabel(): String {
        val main = bestLabel()
        val parts = mutableListOf<String>()
        deviceType?.let { if (it != main) parts.add(it) }
        vendor?.let { if (it != main && it != "MAC aleatoria (privado)" && model != null) parts.add(it) }
        if (vendor == "MAC aleatoria (privado)" && deviceType == null) parts.add("MAC privada")
        return parts.joinToString(" · ")
    }

    /** Un nombre que es puro hexadecimal (ID interno, no un nombre legible). */
    private fun looksLikeHash(s: String): Boolean {
        val t = s.trim()
        if (t.length < 12) return false
        return t.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
    }
}
