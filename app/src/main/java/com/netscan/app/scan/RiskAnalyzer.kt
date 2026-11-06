package com.netscan.app.scan

enum class RiskLevel(val label: String) { HIGH("Alto"), MEDIUM("Medio"), INFO("Aviso") }

/** Un hallazgo de seguridad sobre un dispositivo, en lenguaje claro. */
data class Risk(val level: RiskLevel, val title: String, val detail: String)

/**
 * Revisa cada dispositivo y señala configuraciones de riesgo frecuentes
 * en una red doméstica. No ataca nada: solo mira puertos abiertos y
 * pistas del escaneo, y explica qué conviene hacer.
 */
object RiskAnalyzer {

    fun analyze(d: Device): List<Risk> {
        if (d.isThisDevice) return emptyList()
        val p = d.openPorts
        val risks = mutableListOf<Risk>()

        if (23 in p || 2323 in p) risks.add(
            Risk(RiskLevel.HIGH, "Telnet abierto",
                "El puerto Telnet manda todo sin cifrar, contraseñas incluidas. Desactívalo o usa SSH.")
        )
        if (5555 in p) risks.add(
            Risk(RiskLevel.HIGH, "Depuración ADB por red",
                "Con ADB abierto, cualquiera en tu Wi-Fi podría instalar apps o controlar el aparato. Desactiva la «depuración por red».")
        )
        if (21 in p) risks.add(
            Risk(RiskLevel.MEDIUM, "FTP abierto",
                "El FTP no cifra el tráfico. Si no lo usas, apágalo; si lo necesitas, cambia a SFTP.")
        )
        if (3389 in p) risks.add(
            Risk(RiskLevel.MEDIUM, "Escritorio remoto (RDP)",
                "El escritorio remoto de Windows está expuesto. Usa una contraseña fuerte y, si puedes, no lo dejes abierto en la red.")
        )
        if (554 in p || 8554 in p) risks.add(
            Risk(RiskLevel.MEDIUM, "Cámara accesible",
                "Esta cámara responde en la red. Cámbiale la contraseña de fábrica y mantén su firmware al día.")
        )
        if (445 in p || 139 in p) risks.add(
            Risk(RiskLevel.INFO, "Compartición de archivos (SMB)",
                "Hay carpetas compartidas de Windows/NAS activas. Revisa que pidan usuario y contraseña.")
        )
        if (1883 in p || 8883 in p) risks.add(
            Risk(RiskLevel.INFO, "Dispositivo IoT con MQTT",
                "Aparato de domótica con su servicio de mensajes abierto. Mantenlo actualizado.")
        )

        if (d.isGateway) {
            val hasHttps = 443 in p
            val hasHttp = 80 in p
            if (hasHttp && !hasHttps) risks.add(
                Risk(RiskLevel.MEDIUM, "Panel del router sin cifrar",
                    "La administración del router usa HTTP (sin candado). Evita entrar desde redes que no controles.")
            )
            risks.add(
                Risk(RiskLevel.INFO, "Revisa la contraseña del router",
                    "Si nunca cambiaste la contraseña de fábrica del router, hazlo desde su panel de administración. Es la puerta de toda tu red.")
            )
        }

        return risks
    }

    /** El nivel más grave de la lista (para el punto de color). */
    fun worst(risks: List<Risk>): RiskLevel? = when {
        risks.any { it.level == RiskLevel.HIGH } -> RiskLevel.HIGH
        risks.any { it.level == RiskLevel.MEDIUM } -> RiskLevel.MEDIUM
        risks.any { it.level == RiskLevel.INFO } -> RiskLevel.INFO
        else -> null
    }
}
