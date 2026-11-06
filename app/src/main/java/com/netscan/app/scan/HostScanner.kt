package com.netscan.app.scan

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

/** Descubre qué IPs del subred están vivas y, cuando se puede, su MAC. */
object HostScanner {

    // Puertos de sondeo TCP: si responde ICMP falla, probamos conexión.
    private val PROBE_PORTS = listOf(80, 443, 22, 139, 445, 8080, 62078)

    /**
     * Barre el subred. [onHostFound] se llama por cada IP viva (puede
     * llamarse antes de tener el MAC resuelto).
     */
    suspend fun sweep(
        net: LocalNet,
        onHostFound: (Device) -> Unit
    ): List<Device> = coroutineScope {
        val hosts = net.subnetHosts()

        val found = hosts.map { ip ->
            async(Dispatchers.IO) {
                if (isAlive(ip)) {
                    val d = Device(ip = ip)
                    d.isGateway = (ip == net.gatewayIp)
                    d.isThisDevice = (ip == net.localIp)
                    onHostFound(d)
                    d
                } else null
            }
        }.awaitAll().filterNotNull()

        // Tras el barrido, la caché ARP del kernel suele estar poblada.
        // Probamos dos vías: /proc/net/arp y el comando `ip neigh` (a veces
        // una funciona donde la otra está bloqueada).
        val arp = readArpTable()
        val neigh = if (arp.isEmpty()) readIpNeigh() else emptyMap()
        for (d in found) {
            (arp[d.ip] ?: neigh[d.ip])?.let { mac ->
                d.mac = mac
                d.sources.add("ARP")
            }
        }
        found
    }

    /** Lee la tabla de vecinos con `ip neigh`. Mapa IP -> MAC. */
    private fun readIpNeigh(): Map<String, String> {
        val result = HashMap<String, String>()
        try {
            val proc = ProcessBuilder("ip", "neigh").redirectErrorStream(true).start()
            proc.inputStream.bufferedReader().useLines { lines ->
                for (line in lines) {
                    // Formato: "192.168.0.5 dev wlan0 lladdr aa:bb:cc:dd:ee:ff REACHABLE"
                    val cols = line.trim().split(Regex("\\s+"))
                    val ip = cols.firstOrNull() ?: continue
                    val llIdx = cols.indexOf("lladdr")
                    if (llIdx != -1 && llIdx + 1 < cols.size) {
                        val mac = cols[llIdx + 1]
                        if (mac.contains(":") && mac != "00:00:00:00:00:00") {
                            result[ip] = mac.uppercase()
                        }
                    }
                }
            }
            proc.waitFor()
        } catch (_: Exception) {}
        return result
    }

    /** ¿La IP responde a ICMP o a algún puerto TCP común? */
    private suspend fun isAlive(ip: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val addr = InetAddress.getByName(ip)
            if (addr.isReachable(600)) return@withContext true
        } catch (_: Exception) {}

        // Respaldo: intento de conexión TCP rápida.
        for (port in PROBE_PORTS) {
            try {
                Socket().use { s ->
                    s.connect(InetSocketAddress(ip, port), 250)
                    return@withContext true
                }
            } catch (_: Exception) {}
        }
        false
    }

    /**
     * Lee /proc/net/arp. Devuelve mapa IP -> MAC.
     * En Android 10+ suele estar vacío o bloqueado: en ese caso
     * se devuelve un mapa vacío y las otras capas hacen el trabajo.
     */
    private fun readArpTable(): Map<String, String> {
        val result = HashMap<String, String>()
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                reader.readLine() // cabecera
                var line = reader.readLine()
                while (line != null) {
                    val cols = line.split(Regex("\\s+")).filter { it.isNotBlank() }
                    if (cols.size >= 4) {
                        val ip = cols[0]
                        val mac = cols[3]
                        if (mac != "00:00:00:00:00:00" && mac.contains(":")) {
                            result[ip] = mac.uppercase()
                        }
                    }
                    line = reader.readLine()
                }
            }
        } catch (_: Exception) {
            // Sin acceso: devolvemos lo que haya (posiblemente nada).
        }
        return result
    }
}
