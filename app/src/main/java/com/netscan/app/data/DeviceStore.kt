package com.netscan.app.data

import android.content.Context
import com.netscan.app.scan.Device
import com.netscan.app.scan.LocalNet
import org.json.JSONArray
import org.json.JSONObject

/** Un dispositivo que ya vimos alguna vez en una red. */
data class KnownDevice(
    val key: String,
    val label: String,         // Última etiqueta automática
    val customName: String?,   // Nombre que puso el usuario
    val ip: String,
    val mac: String?,
    val type: String?,
    val firstSeen: Long,
    val lastSeen: Long,
    val timesSeen: Int,
    val trusted: Boolean
) {
    fun displayName(): String = customName?.takeIf { it.isNotBlank() } ?: label
}

/** Un escaneo terminado, para el historial. */
data class ScanRecord(
    val time: Long,
    val network: String,
    val total: Int,
    val newCount: Int
)

/**
 * Historial persistente (SharedPreferences + JSON): nombres personalizados,
 * primera/última vez que se vio cada aparato y registro de escaneos.
 * Los datos se guardan por red (SSID), para no mezclar la casa con el trabajo.
 */
class DeviceStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("netscan_store", Context.MODE_PRIVATE)

    // ---------- Ajustes ----------

    var watchEnabled: Boolean
        get() = prefs.getBoolean("watch_enabled", false)
        set(v) { prefs.edit().putBoolean("watch_enabled", v).apply() }

    // ---------- Identificador de red ----------

    fun networkId(net: LocalNet): String =
        net.ssid ?: net.gatewayIp?.let { "Red $it" } ?: "Red local"

    // ---------- Dispositivos conocidos ----------

    @Synchronized
    fun known(network: String): List<KnownDevice> {
        val obj = loadNetwork(network)
        val out = mutableListOf<KnownDevice>()
        for (k in obj.keys()) {
            val o = obj.optJSONObject(k) ?: continue
            out.add(fromJson(k, o))
        }
        return out.sortedByDescending { it.lastSeen }
    }

    /**
     * Cruza el escaneo con el historial: aplica nombres personalizados,
     * marca los dispositivos nuevos y guarda la visita.
     * Devuelve los que nunca se habían visto en esta red.
     * En el PRIMER escaneo de una red no se marca nada como nuevo (es la base).
     */
    @Synchronized
    fun applyAndSave(network: String, devices: List<Device>): List<Device> {
        val obj = loadNetwork(network)
        val firstTime = obj.length() == 0
        val now = System.currentTimeMillis()
        val newOnes = mutableListOf<Device>()

        for (d in devices) {
            val key = findKey(obj, d)
            val prev = obj.optJSONObject(key)
            if (prev == null) {
                d.isNew = !firstTime && !d.isThisDevice
                d.firstSeen = now
                if (d.isNew) newOnes.add(d)
            } else {
                d.isNew = false
                d.firstSeen = prev.optLong("first", now)
                d.customName = prev.optString("custom").ifEmpty { null }
                d.trusted = prev.optBoolean("trust", false)
            }
            val o = prev ?: JSONObject()
            o.put("label", d.autoLabel())
            o.put("ip", d.ip)
            d.mac?.let { o.put("mac", it) }
            d.deviceType?.let { o.put("type", it) }
            o.put("first", d.firstSeen ?: now)
            o.put("last", now)
            o.put("count", (prev?.optInt("count", 0) ?: 0) + 1)
            // Registro de actividad: últimas 30 veces que se vio.
            val seen = prev?.optJSONArray("seen") ?: JSONArray()
            seen.put(now)
            val trimmed = JSONArray()
            val start = (seen.length() - 30).coerceAtLeast(0)
            for (i in start until seen.length()) trimmed.put(seen.get(i))
            o.put("seen", trimmed)
            // Si antes lo teníamos por IP y ahora sabemos el MAC, migramos la clave.
            if (key != d.key() && d.key().startsWith("mac:")) {
                obj.remove(key)
                obj.put(d.key(), o)
            } else {
                obj.put(key, o)
            }
        }
        saveNetwork(network, obj)
        addRecord(ScanRecord(now, network, devices.size, newOnes.size))
        return newOnes
    }

    /** Solo aplica nombres e historial sin guardar una visita (para refrescar la UI). */
    @Synchronized
    fun applyNames(network: String, devices: List<Device>) {
        val obj = loadNetwork(network)
        for (d in devices) {
            val prev = obj.optJSONObject(findKey(obj, d)) ?: continue
            d.customName = prev.optString("custom").ifEmpty { null }
            d.firstSeen = prev.optLong("first")
            d.trusted = prev.optBoolean("trust", false)
        }
    }

    /** Marcas de tiempo (ms) de las últimas veces que se vio el dispositivo. */
    @Synchronized
    fun seenTimes(network: String, device: Device): List<Long> {
        val obj = loadNetwork(network)
        val o = obj.optJSONObject(findKey(obj, device)) ?: return emptyList()
        val arr = o.optJSONArray("seen") ?: return emptyList()
        return (0 until arr.length()).map { arr.optLong(it) }
    }

    @Synchronized
    fun rename(network: String, device: Device, name: String?) {
        val obj = loadNetwork(network)
        val key = findKey(obj, device)
        val o = obj.optJSONObject(key) ?: JSONObject().apply {
            put("label", device.autoLabel())
            put("ip", device.ip)
            put("first", System.currentTimeMillis())
            put("last", System.currentTimeMillis())
            put("count", 1)
        }
        if (name.isNullOrBlank()) o.remove("custom") else o.put("custom", name.trim())
        obj.put(key, o)
        saveNetwork(network, obj)
        device.customName = name?.trim()?.ifEmpty { null }
    }

    @Synchronized
    fun setTrusted(network: String, device: Device, trusted: Boolean) {
        val obj = loadNetwork(network)
        val key = findKey(obj, device)
        val o = obj.optJSONObject(key) ?: JSONObject().apply {
            put("label", device.autoLabel())
            put("ip", device.ip)
            put("first", System.currentTimeMillis())
            put("last", System.currentTimeMillis())
            put("count", 1)
        }
        o.put("trust", trusted)
        obj.put(key, o)
        saveNetwork(network, obj)
        device.trusted = trusted
    }

    @Synchronized
    fun forget(network: String, key: String) {
        val obj = loadNetwork(network)
        obj.remove(key)
        saveNetwork(network, obj)
    }

    // ---------- Registro de escaneos ----------

    @Synchronized
    fun records(): List<ScanRecord> {
        val arr = JSONArray(prefs.getString("records", "[]"))
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            ScanRecord(o.optLong("t"), o.optString("n"), o.optInt("c"), o.optInt("new"))
        }.sortedByDescending { it.time }
    }

    private fun addRecord(r: ScanRecord) {
        val arr = JSONArray(prefs.getString("records", "[]"))
        arr.put(JSONObject().put("t", r.time).put("n", r.network).put("c", r.total).put("new", r.newCount))
        // Guardamos solo los últimos 50 escaneos.
        val trimmed = JSONArray()
        val start = (arr.length() - 50).coerceAtLeast(0)
        for (i in start until arr.length()) trimmed.put(arr.get(i))
        prefs.edit().putString("records", trimmed.toString()).apply()
    }

    // ---------- Internos ----------

    /**
     * Busca la entrada de este dispositivo: primero por MAC; si no hay MAC,
     * por IP (y, si hay MAC nueva, una entrada vieja con la misma IP sin MAC).
     */
    private fun findKey(obj: JSONObject, d: Device): String {
        val k = d.key()
        if (obj.has(k)) return k
        val ipKey = "ip:${d.ip}"
        if (obj.has(ipKey)) {
            val o = obj.optJSONObject(ipKey)
            if (o != null && !o.has("mac")) return ipKey
        }
        return k
    }

    private fun loadNetwork(network: String): JSONObject = try {
        JSONObject(prefs.getString("net:$network", "{}") ?: "{}")
    } catch (_: Exception) { JSONObject() }

    private fun saveNetwork(network: String, obj: JSONObject) {
        prefs.edit().putString("net:$network", obj.toString()).apply()
    }

    private fun fromJson(key: String, o: JSONObject) = KnownDevice(
        key = key,
        label = o.optString("label", "Dispositivo"),
        customName = o.optString("custom").ifEmpty { null },
        ip = o.optString("ip"),
        mac = o.optString("mac").ifEmpty { null },
        type = o.optString("type").ifEmpty { null },
        firstSeen = o.optLong("first"),
        lastSeen = o.optLong("last"),
        timesSeen = o.optInt("count", 1),
        trusted = o.optBoolean("trust", false)
    )
}
