package com.netscan.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.netscan.app.MainActivity
import com.netscan.app.R
import com.netscan.app.scan.Device

/** Avisos del sistema cuando aparece un dispositivo nuevo en la red. */
object Notifier {

    private const val CHANNEL = "new_devices"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Dispositivos nuevos", NotificationManager.IMPORTANCE_DEFAULT)
                        .apply { description = "Aviso cuando un aparato desconocido se conecta a tu Wi-Fi" }
                )
            }
        }
    }

    fun notifyNewDevices(context: Context, network: String, devices: List<Device>) {
        if (devices.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        ensureChannel(context)

        val title = if (devices.size == 1) "Nuevo dispositivo en $network"
        else "${devices.size} dispositivos nuevos en $network"
        val lines = devices.take(5).map { "${it.bestLabel()} · ${it.ip}" }
        val text = lines.joinToString("\n")

        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(network.hashCode(), n)
        } catch (_: SecurityException) {}
    }
}
