package com.netscan.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.netscan.app.MainActivity
import com.netscan.app.R
import com.netscan.app.data.DeviceStore

/** Widget de inicio: muestra cuántos aparatos había en el último escaneo. */
class NetScanWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) render(context, mgr, id)
    }

    companion object {
        /** Refresca todos los widgets (llámalo tras un escaneo). */
        fun refresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, NetScanWidget::class.java))
            for (id in ids) render(context, mgr, id)
        }

        private fun render(context: Context, mgr: AppWidgetManager, id: Int) {
            val last = DeviceStore(context).records().firstOrNull()
            val views = RemoteViews(context.packageName, R.layout.widget_netscan)
            if (last == null) {
                views.setTextViewText(R.id.widget_count, "—")
                views.setTextViewText(R.id.widget_label, "Toca para escanear")
            } else {
                views.setTextViewText(R.id.widget_count, last.total.toString())
                val rel = android.text.format.DateUtils.getRelativeTimeSpanString(
                    last.time, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS
                )
                views.setTextViewText(R.id.widget_label, "${last.network} · $rel")
            }
            val pi = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)
            mgr.updateAppWidget(id, views)
        }
    }
}
