package com.jpspm.travelplanner.platform

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.view.View
import android.widget.RemoteViews
import com.jpspm.travelplanner.data.background.HomeSummaryLoader
import com.jpspm.travelplanner.shared.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * Widget "Hoje" na tela inicial (RF-45), com `RemoteViews` clássico, sem biblioteca. Mostra só texto:
 * a viagem, o próximo item com horário e quantos ingressos há hoje. Tocar abre o app.
 */
class TodayWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val summary = runCatching { GlobalContext.get().get<HomeSummaryLoader>().load() }.getOrNull()
                val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                    PendingIntent.getActivity(context, 1, it, PendingIntent.FLAG_IMMUTABLE)
                }
                ids.forEach { id ->
                    val views = RemoteViews(context.packageName, R.layout.widget_today).apply {
                        setTextViewText(R.id.widget_trip, summary?.tripName ?: context.getString(R.string.widget_label))
                        setText(R.id.widget_subtitle, summary?.subtitle)
                        setText(R.id.widget_next_title, summary?.nextTitle)
                        setText(R.id.widget_next_time, summary?.nextTime)
                        setText(R.id.widget_tickets, summary?.tickets)
                        open?.let { setOnClickPendingIntent(R.id.widget_root, it) }
                    }
                    manager.updateAppWidget(id, views)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun RemoteViews.setText(id: Int, text: String?) {
        setTextViewText(id, text.orEmpty())
        setViewVisibility(id, if (text.isNullOrBlank()) View.GONE else View.VISIBLE)
    }
}
