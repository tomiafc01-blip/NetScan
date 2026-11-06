package com.netscan.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netscan.app.data.DeviceStore
import com.netscan.app.data.KnownDevice
import com.netscan.app.data.ScanRecord
import com.netscan.app.data.WatchWorker
import com.netscan.app.scan.Device
import com.netscan.app.scan.NetworkInfo
import com.netscan.app.scan.ScanEngine
import com.netscan.app.scan.ScanState
import com.netscan.app.tools.NetTools
import com.netscan.app.tools.PingResult
import com.netscan.app.update.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = ScanEngine(app.applicationContext)
    private val store = DeviceStore(app.applicationContext)

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state

    /** Red del último escaneo (SSID o puerta de enlace). */
    private val _network = MutableStateFlow<String?>(null)
    val network: StateFlow<String?> = _network

    /** Dispositivos nuevos del último escaneo (para el aviso en pantalla). */
    private val _newDevices = MutableStateFlow<List<Device>>(emptyList())
    val newDevices: StateFlow<List<Device>> = _newDevices

    // --- Historial ---
    private val _known = MutableStateFlow<List<KnownDevice>>(emptyList())
    val known: StateFlow<List<KnownDevice>> = _known

    private val _records = MutableStateFlow<List<ScanRecord>>(emptyList())
    val records: StateFlow<List<ScanRecord>> = _records

    private val _watch = MutableStateFlow(store.watchEnabled)
    val watch: StateFlow<Boolean> = _watch

    // --- Herramientas por dispositivo ---
    private val _ping = MutableStateFlow<Pair<String, PingResult?>?>(null) // ip -> resultado (null = midiendo)
    val ping: StateFlow<Pair<String, PingResult?>?> = _ping

    private val _toast = MutableStateFlow<String?>(null)
    val toast: StateFlow<String?> = _toast

    // --- Auto-actualización ---
    enum class CheckStatus { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, ERROR }

    private val _update = MutableStateFlow<UpdateManager.Release?>(null)
    val update: StateFlow<UpdateManager.Release?> = _update

    private val _updatePhase = MutableStateFlow("")
    val updatePhase: StateFlow<String> = _updatePhase

    private val _checkStatus = MutableStateFlow(CheckStatus.IDLE)
    val checkStatus: StateFlow<CheckStatus> = _checkStatus

    val currentVersion: String get() = UpdateManager.currentVersionName

    init {
        checkForUpdate()
        // Así el historial ya se ve antes del primer escaneo.
        NetworkInfo.current(app.applicationContext)?.let { _network.value = store.networkId(it) }
        refreshHistory()
    }

    fun startScan() {
        if (_state.value.running) return
        _state.value = ScanState(running = true, phase = "Iniciando…")
        _newDevices.value = emptyList()
        viewModelScope.launch {
            engine.run { s ->
                val net = s.net
                if (net != null) {
                    val id = store.networkId(net)
                    _network.value = id
                    if (s.running) {
                        store.applyNames(id, s.devices)
                    } else {
                        // Escaneo terminado: cruzamos con el historial y guardamos.
                        _newDevices.value = store.applyAndSave(id, s.devices)
                        refreshHistory()
                    }
                }
                _state.value = s
            }
        }
    }

    fun refreshHistory() {
        _network.value?.let { _known.value = store.known(it) }
        _records.value = store.records()
    }

    fun rename(device: Device, name: String?) {
        val id = _network.value ?: return
        store.rename(id, device, name)
        bump()
        refreshHistory()
    }

    fun forget(key: String) {
        val id = _network.value ?: return
        store.forget(id, key)
        refreshHistory()
    }

    fun dismissNew() {
        _state.value.devices.forEach { it.isNew = false }
        _newDevices.value = emptyList()
        bump()
    }

    fun setWatch(context: Context, enabled: Boolean) {
        store.watchEnabled = enabled
        _watch.value = enabled
        if (enabled) WatchWorker.schedule(context) else WatchWorker.cancel(context)
    }

    fun runPing(ip: String) {
        _ping.value = ip to null
        viewModelScope.launch {
            val r = NetTools.ping(ip)
            _ping.value = ip to r
        }
    }

    fun clearPing() { _ping.value = null }

    fun wake(device: Device) {
        val mac = device.mac
        if (mac == null) {
            _toast.value = "Sin MAC: no se puede enviar Wake-on-LAN"
            return
        }
        viewModelScope.launch {
            val ok = NetTools.wakeOnLan(mac, device.ip)
            _toast.value = if (ok) "Paquete mágico enviado a $mac" else "No se pudo enviar el paquete"
        }
    }

    fun export(context: Context) {
        val devices = _state.value.devices
        if (devices.isEmpty()) {
            _toast.value = "Primero haz un escaneo"
            return
        }
        try {
            NetTools.exportCsv(context, _network.value ?: "Red", devices)
        } catch (e: Exception) {
            _toast.value = "No se pudo exportar: ${e.message}"
        }
    }

    fun toastShown() { _toast.value = null }

    private fun bump() {
        _state.value = _state.value.copy(rev = _state.value.rev + 1)
    }

    /** Comprueba si hay actualización. [manual] muestra feedback aunque ya esté al día. */
    fun checkForUpdate(manual: Boolean = false) {
        viewModelScope.launch {
            _checkStatus.value = CheckStatus.CHECKING
            val latest = UpdateManager.fetchLatest()
            when {
                latest == null -> _checkStatus.value = CheckStatus.ERROR
                UpdateManager.isNewer(latest) -> {
                    _update.value = latest
                    _checkStatus.value = CheckStatus.AVAILABLE
                }
                else -> _checkStatus.value = CheckStatus.UP_TO_DATE
            }
        }
    }

    /** Descarga e instala la actualización disponible. */
    fun runUpdate(context: Context) {
        val release = _update.value ?: return
        val url = release.apkUrl
        if (url == null) {
            _updatePhase.value = "No se encontró el APK en el Release"
            return
        }
        if (!UpdateManager.canInstall(context)) {
            _updatePhase.value = "Permite instalar apps de NetScan…"
            UpdateManager.openInstallPermission(context)
            return
        }
        viewModelScope.launch {
            _updatePhase.value = "Descargando…"
            val file = UpdateManager.download(context, url) { p ->
                _updatePhase.value = if (p >= 0) "Descargando… $p%" else "Descargando…"
            }
            if (file == null) {
                _updatePhase.value = "Falló la descarga"
                return@launch
            }
            _updatePhase.value = "Abriendo instalador…"
            UpdateManager.install(context, file)
        }
    }

    fun dismissUpdate() {
        _update.value = null
        _updatePhase.value = ""
        _checkStatus.value = CheckStatus.IDLE
    }
}
