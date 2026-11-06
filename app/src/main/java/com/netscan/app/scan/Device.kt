package com.netscan.app.scan

/** Qué tan seguros estamos de la identificación del dispositivo. */
enum class Confidence(val label: String) {
    HIGH("Alta"),     // El propio aparato publicó su modelo (SSDP / mDNS)
    MEDIUM("Media"),  // Nombre de host o fabricante real (NetBIOS, mDNS, OUI)
    LOW("Baja")       // Solo una suposición por puertos o banners
}

/** Un dispositivo descubierto en la red local. */
data class Device(
    val ip: String,
    var mac: String? = null,
    var vendor: String? = null,      // Fabricante por OUI del MAC o por UPnP
    var hostname: String? = null,    // Nombre de host (mDNS / NetBIOS / DNS inverso)
    var model: String? = null,       // Modelo exacto si algún servicio lo publica
    var deviceType: String? = null,  // TV, Impresora, Router, Teléfono, PC...
    var openPorts: MutableList<Int> = mutableListOf(),
    var services: MutableSet<String> = mutableSetOf(), // Servicios mDNS anunciados
    var sources: MutableSet<String> = mutableSetOf(), // Qué capas aportaron datos
    var isGateway: Boolean = false,
    var isThisDevice: Boolean = false,
    var latencyMs: Int? = null,      // Tiempo de respuesta en el barrido
    var webUrl: String? = null,      // Interfaz web detectada (si hay)
    // --- Datos del historial (DeviceStore) ---
    var customName: String? = null,
    var firstSeen: Long? = null,
    var isNew: Boolean = false,
    var trusted: Boolean = false,
    var risks: List<Risk> = emptyList()
) {
    /**
     * Clave estable para el historial: el MAC si lo tenemos y no es aleatorio;
     * si no, el modelo/nombre + IP, que suele mantenerse con DHCP.
     */
    fun key(): String {
        val m = mac
        if (m != null && !isRandomMac(m)) return "mac:$m"
        return "ip:$ip"
    }

    /** Nivel de confianza según qué capas aportaron datos. */
    fun confidence(): Confidence = when {
        model != null && ("SSDP" in sources || "mDNS" in sources) -> Confidence.HIGH
        isGateway || isThisDevice -> Confidence.HIGH
        hostname != null && ("NetBIOS" in sources || "mDNS" in sources) -> Confidence.MEDIUM
        "OUI" in sources && vendor != MAC_RANDOM -> Confidence.MEDIUM
        "SSDP" in sources -> Confidence.MEDIUM
        else -> Confidence.LOW
    }

    /** Etiqueta automática (sin el nombre que puso el usuario). */
    fun autoLabel(): String {
        if (isThisDevice) return model ?: "Este teléfono"
        model?.let { return it }
        hostname?.takeIf { !looksLikeHash(it) }?.let { return it }
        vendor?.takeIf { it != MAC_RANDOM }?.let { return it }
        deviceType?.let { return it }
        return "Dispositivo desconocido"
    }

    /** Mejor etiqueta disponible para mostrar al usuario. */
    fun bestLabel(): String = customName?.takeIf { it.isNotBlank() } ?: autoLabel()

    /** Subtítulo con la mejor pista secundaria (sin repetir la etiqueta principal). */
    fun subLabel(): String {
        val main = bestLabel()
        val parts = mutableListOf<String>()
        if (customName != null && autoLabel() != main) parts.add(autoLabel())
        deviceType?.let { if (it != main && it !in parts) parts.add(it) }
        vendor?.let {
            if (it != main && it != MAC_RANDOM && model != null && it !in parts) parts.add(it)
        }
        if (vendor == MAC_RANDOM && deviceType == null) parts.add("MAC privada")
        return parts.joinToString(" · ")
    }

    /** Un nombre que es puro hexadecimal (ID interno, no un nombre legible). */
    private fun looksLikeHash(s: String): Boolean {
        val t = s.trim()
        if (t.length < 12) return false
        return t.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }
    }

    companion object {
        const val MAC_RANDOM = "MAC aleatoria (privado)"

        /** MAC administrada localmente (bit 0x02 del primer byte) = aleatoria. */
        fun isRandomMac(mac: String): Boolean {
            val hex = mac.replace(":", "").replace("-", "")
            val first = hex.take(2).toIntOrNull(16) ?: return false
            return (first and 0x02) != 0
        }
    }
}
