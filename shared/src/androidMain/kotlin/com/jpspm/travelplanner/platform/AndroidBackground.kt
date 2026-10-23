package com.jpspm.travelplanner.platform

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.app.Notification
import com.jpspm.travelplanner.shared.R

private const val CHANNEL_ID = "reminders"
private const val PREFS = "reminders"
private const val KEY_CODES = "scheduled_codes"
private const val EXTRA_ID = "id"
private const val EXTRA_TITLE = "title"
private const val EXTRA_TEXT = "text"

/**
 * Lembretes com alarmes inexatos do Android (RF-43): não exigem a permissão de alarme exato e
 * sobrevivem ao modo de economia de bateria (`setAndAllowWhileIdle`), podendo atrasar alguns minutos.
 */
class AndroidReminderScheduler(private val context: Context) : ReminderScheduler {

    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_HIGH)
            .apply { description = context.getString(R.string.reminder_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun replaceAll(notifications: List<ScheduledNotification>) {
        // Cancela tudo o que foi agendado antes, para não duplicar nem deixar lembrete de item apagado.
        prefs.getStringSet(KEY_CODES, emptySet()).orEmpty().forEach { code ->
            alarms.cancel(pendingIntent(code.toInt(), null))
        }
        val codes = notifications.map { n ->
            val code = n.key.hashCode()
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, n.at.toEpochMilliseconds(), pendingIntent(code, n))
            code.toString()
        }
        prefs.edit().putStringSet(KEY_CODES, codes.toSet()).apply()
    }

    private fun pendingIntent(code: Int, n: ScheduledNotification?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = "com.jpspm.travelplanner.REMINDER"
            if (n != null) {
                putExtra(EXTRA_ID, code)
                putExtra(EXTRA_TITLE, n.title)
                putExtra(EXTRA_TEXT, n.text)
            }
        }
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

/** Mostra a notificação na hora do lembrete. Tocar nela abre o app. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
            PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
        }
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(intent.getStringExtra(EXTRA_TITLE))
            .setContentText(intent.getStringExtra(EXTRA_TEXT))
            .setStyle(Notification.BigTextStyle().bigText(intent.getStringExtra(EXTRA_TEXT)))
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(intent.getIntExtra(EXTRA_ID, 0), notification)
    }
}

/**
 * Depois de reiniciar o celular ou atualizar o app, os alarmes somem. Receber o aviso já sobe o
 * processo do app, e a `Application` reagenda tudo pelo [com.jpspm.travelplanner.data.background.BackgroundSync].
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}

/** Pede ao Android para redesenhar o widget "Hoje" (RF-45). */
class AndroidHomeWidgetUpdater(private val context: Context) : HomeWidgetUpdater {
    override fun update() {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, TodayWidgetProvider::class.java))
        if (ids.isEmpty()) return
        context.sendBroadcast(
            Intent(context, TodayWidgetProvider::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
        )
    }
}
