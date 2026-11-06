package com.netscan.app

import com.netscan.app.scan.Device
import com.netscan.app.scan.RiskAnalyzer
import com.netscan.app.scan.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RiskAnalyzerTest {

    @Test fun telnet_es_riesgo_alto() {
        val d = Device(ip = "192.168.0.20", openPorts = mutableListOf(23, 80))
        val risks = RiskAnalyzer.analyze(d)
        assertTrue(risks.any { it.level == RiskLevel.HIGH && it.title.contains("Telnet") })
        assertEquals(RiskLevel.HIGH, RiskAnalyzer.worst(risks))
    }

    @Test fun este_equipo_no_tiene_riesgos() {
        val d = Device(ip = "192.168.0.21", openPorts = mutableListOf(23), isThisDevice = true)
        assertTrue(RiskAnalyzer.analyze(d).isEmpty())
    }

    @Test fun router_http_sin_https_avisa() {
        val d = Device(ip = "192.168.0.1", openPorts = mutableListOf(80), isGateway = true)
        val risks = RiskAnalyzer.analyze(d)
        assertTrue(risks.any { it.title.contains("sin cifrar") })
        assertTrue(risks.any { it.title.contains("contraseña", ignoreCase = true) })
    }

    @Test fun sin_puertos_sensibles_sin_riesgos() {
        val d = Device(ip = "192.168.0.22", openPorts = mutableListOf(443))
        assertTrue(RiskAnalyzer.analyze(d).isEmpty())
    }
}
