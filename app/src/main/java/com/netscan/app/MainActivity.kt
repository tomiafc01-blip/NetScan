package com.netscan.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.netscan.app.ui.ScanScreen

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        setContent {
            val dark = isSystemInDarkTheme()
            val colors = if (dark) darkColorScheme(
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
