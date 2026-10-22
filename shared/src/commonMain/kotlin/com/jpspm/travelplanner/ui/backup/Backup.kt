package com.jpspm.travelplanner.ui.backup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpspm.travelplanner.core.time.TimeFormat
import com.jpspm.travelplanner.domain.repository.BackupException
import com.jpspm.travelplanner.domain.repository.BackupRepository
import com.jpspm.travelplanner.domain.repository.BackupSummary
import com.jpspm.travelplanner.domain.repository.ImportMode
import com.jpspm.travelplanner.resources.*
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitDialogSettings
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFileSaverLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberShareFileLauncher
import io.github.vinceglb.filekit.name
import io.github.vinceglb.filekit.size
import io.github.vinceglb.filekit.write
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

sealed interface BackupState {
    data object Idle : BackupState
    data class Working(val importing: Boolean) : BackupState

    /** `.zip` gerado, esperando o usuário escolher salvar ou compartilhar. */
    data class Exported(val file: PlatformFile, val sizeBytes: Long) : BackupState

    /** Backup lido e válido, esperando o usuário escolher como importar. */
    data class ReadyToImport(val file: PlatformFile, val summary: BackupSummary) : BackupState
    data class Imported(val tripCount: Int) : BackupState
    data class Failed(val message: String) : BackupState
}

class BackupViewModel(private val backup: BackupRepository) : ViewModel() {

    var state by mutableStateOf<BackupState>(BackupState.Idle)
        private set

    /** Lembrete de backup (RNF-04). */
    val reminder: StateFlow<Boolean> = backup.observeReminder()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** [tripIds] `null` = todas as viagens (RF-33). */
    fun export(tripIds: List<String>? = null) = run(importing = false) {
        val file = backup.export(tripIds)
        state = BackupState.Exported(file, file.size())
    }

    fun inspect(file: PlatformFile) = run(importing = true) {
        state = BackupState.ReadyToImport(file, backup.inspect(file))
    }

    fun import(mode: ImportMode) {
        val ready = state as? BackupState.ReadyToImport ?: return
        run(importing = true) { state = BackupState.Imported(backup.import(ready.file, mode)) }
    }

    /** Grava o `.zip` no local escolhido pelo usuário (seletor de arquivos do sistema). */
    fun saveTo(target: PlatformFile) {
        val exported = state as? BackupState.Exported ?: return
        run(importing = false) {
            target write exported.file
            backup.markExported()
            state = BackupState.Idle
        }
    }

    /** O usuário salvou ou compartilhou o `.zip`. */
    fun exported() {
        viewModelScope.launch { backup.markExported() }
        state = BackupState.Idle
    }

    fun snoozeReminder() = viewModelScope.launch { backup.snoozeReminder() }

    fun dismiss() {
        state = BackupState.Idle
    }

    private fun run(importing: Boolean, block: suspend () -> Unit) {
        state = BackupState.Working(importing)
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupException) {
                state = BackupState.Failed(e.message.orEmpty())
            } catch (e: Exception) {
                state = BackupState.Failed(e.message ?: e.toString())
            }
        }
    }
}

/** Abre o seletor de arquivo para importar um backup. */
@Composable
fun rememberBackupImportLauncher(viewModel: BackupViewModel) =
    rememberFilePickerLauncher(type = FileKitType.File("zip")) { file -> file?.let(viewModel::inspect) }

/** Diálogos do fluxo de backup: progresso, salvar/compartilhar, confirmar importação e resultado. */
@Composable
fun BackupDialogs(viewModel: BackupViewModel) {
    val saver = rememberFileSaverLauncher(FileKitDialogSettings.createDefault()) { target ->
        if (target != null) viewModel.saveTo(target)
    }
    val share = rememberShareFileLauncher()

    when (val s = viewModel.state) {
        BackupState.Idle -> Unit
        is BackupState.Working -> AlertDialog(
            onDismissRequest = {},
            confirmButton = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                    Text(
                        stringResource(if (s.importing) Res.string.backup_importing else Res.string.backup_exporting),
                        modifier = Modifier.padding(start = 16.dp),
                    )
                }
            },
        )
        is BackupState.Exported -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text(stringResource(Res.string.backup_ready_title)) },
            text = { Text(stringResource(Res.string.backup_ready_message, formatSize(s.sizeBytes))) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = { saver.launch(s.file.name.removeSuffix(".zip"), "zip") }) {
                        Text(stringResource(Res.string.backup_save))
                    }
                    OutlinedButton(onClick = { share.launch(s.file); viewModel.exported() }) {
                        Text(stringResource(Res.string.backup_share))
                    }
                    TextButton(onClick = viewModel::dismiss) { Text(stringResource(Res.string.action_cancel)) }
                }
            },
        )
        is BackupState.ReadyToImport -> ImportDialog(s.summary, onImport = viewModel::import, onDismiss = viewModel::dismiss)
        is BackupState.Imported -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text(stringResource(Res.string.backup_imported_title)) },
            text = { Text(pluralStringResource(Res.plurals.backup_imported_message, s.tripCount, s.tripCount)) },
            confirmButton = { TextButton(onClick = viewModel::dismiss) { Text(stringResource(Res.string.action_ok)) } },
        )
        is BackupState.Failed -> AlertDialog(
            onDismissRequest = viewModel::dismiss,
            title = { Text(stringResource(Res.string.backup_failed_title)) },
            text = { Text(s.message) },
            confirmButton = { TextButton(onClick = viewModel::dismiss) { Text(stringResource(Res.string.action_ok)) } },
        )
    }
}

@Composable
private fun ImportDialog(summary: BackupSummary, onImport: (ImportMode) -> Unit, onDismiss: () -> Unit) {
    var confirmReplace by remember { mutableStateOf(false) }
    val date = summary.exportedAt.toLocalDateTime(TimeZone.currentSystemDefault())
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (confirmReplace) Res.string.backup_replace_confirm_title else Res.string.backup_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (confirmReplace) {
                    Text(stringResource(Res.string.backup_replace_confirm_message))
                } else {
                    Text(stringResource(Res.string.backup_import_from, TimeFormat.date(date.date), TimeFormat.time(date.time)))
                    Text(
                        summary.tripNames.joinToString("\n") { "• $it" },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        stringResource(Res.string.backup_import_modes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (confirmReplace) {
                    Button(onClick = { onImport(ImportMode.REPLACE) }) { Text(stringResource(Res.string.backup_replace)) }
                } else {
                    Button(onClick = { onImport(ImportMode.MERGE) }) { Text(stringResource(Res.string.backup_merge)) }
                    OutlinedButton(onClick = { confirmReplace = true }) { Text(stringResource(Res.string.backup_replace)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
            }
        },
    )
}

/** Lembrete de backup no topo da lista de viagens (RNF-04). */
@Composable
fun BackupReminderCard(onBackup: () -> Unit, onLater: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(Res.string.backup_reminder_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(Res.string.backup_reminder_message),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(Modifier.padding(top = 8.dp)) {
                Button(onClick = onBackup) { Text(stringResource(Res.string.backup_reminder_action)) }
                TextButton(onClick = onLater) { Text(stringResource(Res.string.backup_reminder_later)) }
            }
        }
    }
}

/** `12345678` → `11,8 MB` */
fun formatSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    fun Double.oneDecimal() = ((this * 10).toLong() / 10.0).toString().replace('.', ',')
    return when {
        mb >= 1 -> "${mb.oneDecimal()} MB"
        kb >= 1 -> "${kb.toLong()} KB"
        else -> "$bytes B"
    }
}
