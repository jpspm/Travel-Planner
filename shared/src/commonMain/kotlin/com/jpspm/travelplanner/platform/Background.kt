package com.jpspm.travelplanner.platform

import kotlin.time.Instant

/** Notificação local pronta para agendar, com os textos já em pt-BR. */
data class ScheduledNotification(val key: String, val at: Instant, val title: String, val text: String)

/** Agenda os lembretes no sistema (RF-43). Sempre substitui todos os agendados antes. */
interface ReminderScheduler {
    fun replaceAll(notifications: List<ScheduledNotification>)
}

/** Pede ao sistema para redesenhar o widget "Hoje" (RF-45). */
interface HomeWidgetUpdater {
    fun update()
}

/** Plataformas sem lembretes nem widget nesta etapa (iOS, RNF-28). */
object NoBackground : ReminderScheduler, HomeWidgetUpdater {
    override fun replaceAll(notifications: List<ScheduledNotification>) = Unit
    override fun update() = Unit
}

/** Arquivo recebido de outro app pelo "Compartilhar" (RF-44), já copiado para o armazenamento do app. */
data class SharedFile(val path: String, val name: String, val isPdf: Boolean)

/** Permissão de notificações (Android 13+). Nas outras plataformas, sempre concedida. */
class NotificationPermission(val granted: Boolean, val request: () -> Unit)

@androidx.compose.runtime.Composable
expect fun rememberNotificationPermission(): NotificationPermission
