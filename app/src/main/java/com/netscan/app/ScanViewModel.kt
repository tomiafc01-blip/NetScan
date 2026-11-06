package com.netscan.app

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netscan.app.scan.ScanEngine
import com.netscan.app.scan.ScanState
import com.netscan.app.update.UpdateManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = ScanEngine(app.applicationContext)

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state

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
    }

    fun startScan() {
        if (_state.value.running) return
        _state.value = ScanState(running = true, phase = "Iniciando…")
        viewModelScope.launch {
            engine.run { s -> _state.value = s }
        }
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
