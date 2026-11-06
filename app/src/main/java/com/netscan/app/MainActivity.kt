package com.netscan.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netscan.app.data.DeviceStore
import com.netscan.app.data.Notifier
import com.netscan.app.data.WatchWorker
import com.netscan.app.ui.ScanScreen
import com.netscan.app.ui.theme.NetScanTheme

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ubicación: necesaria para leer el SSID. Dispositivos cercanos: Android 13+.
        val wanted = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) wanted.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) permissionLauncher.launch(missing.toTypedArray())

        Notifier.ensureChannel(this)
        // Si la vigilancia estaba activa, nos aseguramos de que siga programada.
        if (DeviceStore(this).watchEnabled) WatchWorker.schedule(this)

        setContent {
            NetScanTheme {
                val vm: ScanViewModel = viewModel()
                ScanScreen(vm)
            }
        }
    }
}
