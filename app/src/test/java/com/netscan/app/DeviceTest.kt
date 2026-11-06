package com.netscan.app

import com.netscan.app.scan.Confidence
import com.netscan.app.scan.Device
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceTest {

    @Test fun mac_aleatoria() {
        // Segundo bit menos significativo del primer byte activo = local/aleatoria.
        assertTrue(Device.isRandomMac("AA:BB:CC:DD:EE:FF"))
        assertFalse(Device.isRandomMac("00:11:22:33:44:55"))
        assertFalse(Device.isRandomMac("3C:5A:B4:00:00:01"))
    }

    @Test fun etiqueta_usa_nombre_personalizado() {
        val d = Device(ip = "192.168.0.10", model = "Router X")
        assertEquals("Router X", d.bestLabel())
        d.customName = "Mi router"
        assertEquals("Mi router", d.bestLabel())
    }

    @Test fun confianza_alta_con_modelo_publicado() {
        val d = Device(ip = "192.168.0.11", model = "Smart TV")
        d.sources.add("SSDP")
        assertEquals(Confidence.HIGH, d.confidence())
    }

    @Test fun confianza_baja_sin_datos() {
        val d = Device(ip = "192.168.0.12")
        assertEquals(Confidence.LOW, d.confidence())
    }

    @Test fun clave_por_mac_real() {
        val d = Device(ip = "192.168.0.13", mac = "00:11:22:33:44:55")
        assertEquals("mac:00:11:22:33:44:55", d.key())
        val r = Device(ip = "192.168.0.14", mac = "AA:BB:CC:DD:EE:FF")
        assertEquals("ip:192.168.0.14", r.key()) // mac aleatoria -> no sirve de clave
    }
}
