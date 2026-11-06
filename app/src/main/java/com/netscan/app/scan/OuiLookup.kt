package com.netscan.app.scan

import android.content.Context

/** Capa 1: fabricante a partir de los primeros 3 bytes del MAC (OUI). */
object OuiLookup {

    private var table: Map<String, String>? = null

    private fun ensureLoaded(context: Context) {
        if (table != null) return
        val map = HashMap<String, String>()
        try {
            context.assets.open("oui.txt").bufferedReader().useLines { lines ->
                for (raw in lines) {
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) continue
                    val eq = line.indexOf('=')
                    if (eq <= 0) continue
                    val prefix = line.substring(0, eq).uppercase()
                    val name = line.substring(eq + 1).trim()
                    if (prefix.length == 6) map[prefix] = name
                }
            }
        } catch (_: Exception) {}
        table = map
    }

    /** Devuelve el fabricante o null si el OUI no está en la base. */
    fun vendorFor(context: Context, mac: String?): String? {
        if (mac == null) return null
        ensureLoaded(context)
        val hex = mac.replace(":", "").replace("-", "").uppercase()
        if (hex.length < 6) return null
        // Un MAC con el 2.º bit menos significativo del 1.er byte activo
        // es aleatorio/local: el OUI no identifica al fabricante real.
        val firstByte = hex.substring(0, 2).toIntOrNull(16) ?: return null
        if ((firstByte and 0x02) != 0) return Device.MAC_RANDOM
        return table?.get(hex.substring(0, 6))
    }
}
