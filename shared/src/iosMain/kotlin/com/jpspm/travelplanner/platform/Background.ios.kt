package com.jpspm.travelplanner.platform

import androidx.compose.runtime.Composable

// Lembretes, widget e compartilhamento de entrada são só do Android nesta etapa (RNF-28).
@Composable
actual fun rememberNotificationPermission(): NotificationPermission = NotificationPermission(granted = true, request = {})
