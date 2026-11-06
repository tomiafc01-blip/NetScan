package com.netscan.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netscan.app.data.DeviceStore
import com.netscan.app.data.Notifier
import com.netscan.app.data.WatchWorker
import com.netscan.app.ui.ScanScreen

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
            val dark = isSystemInDarkTheme()
            val ctx = this
            // Material You: en Android 12+ usamos el color del sistema; si no, el tema propio.
            val colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            } else if (dark) darkColorScheme(
                primary = Color(0xFF4DD0E1),
                background = Color(0xFF0D1B2A),
                surface = Color(0xFF13293D)
            ) else lightColorScheme(
                primary = Color(0xFF00838F)
            )
            MaterialTheme(colorScheme = colors) {
                val vm: ScanViewModel = viewModel()
                ScanScreen(vm)
            }
        }
    }
}
