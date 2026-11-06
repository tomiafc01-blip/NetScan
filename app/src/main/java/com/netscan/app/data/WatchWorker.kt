package com.netscan.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.netscan.app.scan.NetworkInfo
import com.netscan.app.scan.ScanEngine
import java.util.concurrent.TimeUnit

/**
 * Vigilancia en segundo plano: cada 30 min (cuando hay Wi-Fi) escanea la red
 * y avisa con una notificación si se conectó un aparato que nunca se había visto.
 */
class WatchWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val net = NetworkInfo.current(applicationContext) ?: return Result.success()
        val store = DeviceStore(applicationContext)
        val state = try {
            ScanEngine(applicationContext).runOnce()
        } catch (_: Exception) {
            return Result.retry()
        }
        if (state.devices.isEmpty()) return Result.success()
        val network = store.networkId(net)
        val newOnes = store.applyAndSave(network, state.devices)
        Notifier.notifyNewDevices(applicationContext, network, newOnes)
        return Result.success()
    }

    companion object {
        private const val NAME = "netscan-watch"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<WatchWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.UNMETERED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
