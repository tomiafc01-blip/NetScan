package com.netscan.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.netscan.app.scan.ScanEngine
import com.netscan.app.scan.ScanState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ScanViewModel(app: Application) : AndroidViewModel(app) {

    private val engine = ScanEngine(app.applicationContext)

    private val _state = MutableStateFlow(ScanState())
    val state: StateFlow<ScanState> = _state

    fun startScan() {
        if (_state.value.running) return
        _state.value = ScanState(running = true, phase = "Iniciando…")
        viewModelScope.launch {
            engine.run { s -> _state.value = s }
        }
    }
}
