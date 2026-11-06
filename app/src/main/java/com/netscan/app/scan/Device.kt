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
        hostname?.let { return it }
        vendor?.let { return it }
        return "Dispositivo desconocido"
    }

    /** Subtítulo con la mejor pista secundaria. */
    fun subLabel(): String {
        val parts = mutableListOf<String>()
        deviceType?.let { parts.add(it) }
        if (model != null) {
            vendor?.let { parts.add(it) }
        }
        return parts.joinToString(" · ")
    }
}
