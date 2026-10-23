package com.jpspm.travelplanner.platform

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.jpspm.travelplanner.core.newId
import com.jpspm.travelplanner.data.background.IncomingShare
import org.koin.core.context.GlobalContext
import java.io.File

/**
 * Recebe PDF e imagens enviados por outro app pelo "Compartilhar" (RF-44). Copia na hora para o
 * cache do app, porque a permissão de leitura do outro app vale só por pouco tempo.
 */
fun handleShareIntent(activity: Activity, intent: Intent?) {
    if (intent == null) return
    val uris: List<Uri> = when (intent.action) {
        Intent.ACTION_SEND -> listOfNotNull(intent.extraStream())
        Intent.ACTION_SEND_MULTIPLE -> intent.extraStreams()
        else -> return
    }
    val dir = File(activity.cacheDir, "shared").apply { mkdirs() }
    val files = uris.mapNotNull { uri ->
        runCatching {
            val resolver = activity.contentResolver
            val mime = resolver.getType(uri).orEmpty()
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            } ?: "arquivo"
            val isPdf = mime == "application/pdf" || name.endsWith(".pdf", ignoreCase = true)
            val ext = when {
                isPdf -> "pdf"
                name.contains('.') -> name.substringAfterLast('.').lowercase()
                else -> "jpg"
            }
            val target = File(dir, "${newId()}.$ext")
            resolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: return@runCatching null
            SharedFile(target.absolutePath, name, isPdf)
        }.getOrNull()
    }
    GlobalContext.get().get<IncomingShare>().receive(files)
    // Evita receber de novo os mesmos arquivos se a Activity for recriada.
    intent.action = null
}

@Suppress("DEPRECATION")
private fun Intent.extraStream(): Uri? =
    if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else getParcelableExtra(Intent.EXTRA_STREAM)

@Suppress("DEPRECATION")
private fun Intent.extraStreams(): List<Uri> =
    (if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
    else getParcelableArrayListExtra(Intent.EXTRA_STREAM)).orEmpty()

@Composable
actual fun rememberNotificationPermission(): NotificationPermission {
    val context = LocalContext.current
    fun granted() = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var isGranted by remember { mutableStateOf(granted()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted = it }
    return remember(isGranted) {
        NotificationPermission(
            granted = isGranted,
            request = { if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS) },
        )
    }
}
