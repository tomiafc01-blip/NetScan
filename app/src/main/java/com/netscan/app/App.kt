package com.netscan.app

import android.app.Application
import com.netscan.app.data.Diag

/** Punto de arranque de la app: instala el diagnóstico lo antes posible. */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Diag.install(this)
    }
}
