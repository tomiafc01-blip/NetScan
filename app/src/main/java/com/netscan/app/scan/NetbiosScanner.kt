package com.netscan.app.scan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * Capa 4: consulta NetBIOS de estado de nombre (NBSTAT) a UDP 137.
 * Devuelve el nombre de equipo de las máquinas Windows (y algunos NAS/SMB).
 */
object NetbiosScanner {

    // Paquete NBSTAT para el nombre comodín "*".
    private val QUERY: ByteArray = buildQuery()

    /** Nombre del equipo y, si viene, su MAC real (el "Unit ID" de NBSTAT). */
    data class Hit(val name: String?, val mac: String?)

    suspend fun query(ip: String, timeoutMs: Int = 700): Hit? =
        withContext(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket().apply { soTimeout = timeoutMs }
                val addr = InetAddress.getByName(ip)
                socket.send(DatagramPacket(QUERY, QUERY.size, addr, 137))

                val buf = ByteArray(1024)
                val resp = DatagramPacket(buf, buf.size)
                socket.receive(resp)
                parse(resp.data, resp.length)
            } catch (_: Exception) {
                null
            } finally {
                try { socket?.close() } catch (_: Exception) {}
            }
        }

    private fun buildQuery(): ByteArray {
        val header = byteArrayOf(
            0x00, 0x00,             // Transaction ID
            0x00, 0x00,             // Flags
            0x00, 0x01,             // Questions = 1
            0x00, 0x00,             // Answer RRs
            0x00, 0x00,             // Authority RRs
            0x00, 0x00              // Additional RRs
        )
        // Nombre codificado: "*" + 15 nulos, codificación de primer nivel.
        val name = ByteArray(16)
        name[0] = 0x2A // '*'
        val encoded = ByteArray(32)
        for (i in 0 until 16) {
            val b = name[i].toInt() and 0xFF
            encoded[i * 2] = ((b shr 4) + 0x41).toByte()
            encoded[i * 2 + 1] = ((b and 0x0F) + 0x41).toByte()
        }
        val question = ByteArray(1 + 32 + 1 + 2 + 2)
        var p = 0
        question[p++] = 0x20 // longitud del nombre codificado (32)
        for (e in encoded) question[p++] = e
        question[p++] = 0x00 // terminador
        question[p++] = 0x00; question[p++] = 0x21 // tipo NBSTAT
        question[p++] = 0x00; question[p++] = 0x01 // clase IN
        return header + question
    }

    private fun parse(data: ByteArray, len: Int): Hit? {
        try {
            // Cabecera (12) + nombre codificado (34) + tipo/clase/ttl/rdlen (10)
            // = 56: ahí está el byte con el número de nombres.
            var offset = 56
            if (offset >= len) return null
            val numNames = data[offset].toInt() and 0xFF
            offset += 1
            var workstation: String? = null
            var fallback: String? = null
            for (i in 0 until numNames) {
                if (offset + 18 > len) break
                val nameBytes = data.copyOfRange(offset, offset + 15)
                val suffix = data[offset + 15].toInt() and 0xFF
                val flags = ((data[offset + 16].toInt() and 0xFF) shl 8) or
                        (data[offset + 17].toInt() and 0xFF)
                offset += 18
                val isGroup = (flags and 0x8000) != 0
                val name = String(nameBytes, Charsets.US_ASCII).trim().trim('\u0000').trim()
                if (name.isEmpty() || isGroup) continue
                // Suffix 0x00 = estación de trabajo; 0x20 = servidor de archivos.
                if (suffix == 0x00 && workstation == null) workstation = name
                if (suffix == 0x20 && fallback == null) fallback = name
            }
            // Tras la lista de nombres vienen 6 bytes con el MAC del adaptador.
            var mac: String? = null
            if (offset + 6 <= len) {
                val raw = data.copyOfRange(offset, offset + 6)
                if (raw.any { it.toInt() != 0 }) {
                    mac = raw.joinToString(":") { "%02X".format(it.toInt() and 0xFF) }
                }
            }
            val name = workstation ?: fallback
            if (name == null && mac == null) return null
            return Hit(name, mac)
        } catch (_: Exception) {}
        return null
    }
}
