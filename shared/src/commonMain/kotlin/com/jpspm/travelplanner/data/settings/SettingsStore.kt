package com.jpspm.travelplanner.data.settings

import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.filesDir
import io.github.vinceglb.filekit.readString
import io.github.vinceglb.filekit.writeString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Preferências do app guardadas num JSON pequeno no armazenamento privado. */
@Serializable
data class AppSettings(
    /** Último backup exportado (epoch ms), para o lembrete de backup (RNF-04). */
    val lastBackupAt: Long? = null,
    /** Lembrete adiado pelo usuário até este instante (epoch ms). */
    val backupReminderSnoozedUntil: Long? = null,
)

class SettingsStore(private val root: () -> PlatformFile = { FileKit.filesDir }) {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val state = MutableStateFlow<AppSettings?>(null)
    private val lock = Mutex()

    private val file get() = root() / "settings.json"

    val settings: Flow<AppSettings> = state.onStart { ensureLoaded() }.filterNotNull()

    suspend fun update(transform: (AppSettings) -> AppSettings) = lock.withLock {
        val updated = transform(state.value ?: load())
        withContext(Dispatchers.IO) { file.writeString(json.encodeToString(updated)) }
        state.value = updated
    }

    private suspend fun ensureLoaded() {
        if (state.value == null) lock.withLock { if (state.value == null) state.value = load() }
    }

    private suspend fun load(): AppSettings = withContext(Dispatchers.IO) {
        runCatching { if (file.exists()) json.decodeFromString<AppSettings>(file.readString()) else null }
            .getOrNull() ?: AppSettings()
    }
}
