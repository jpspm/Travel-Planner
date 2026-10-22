package com.jpspm.travelplanner.ui.trip

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jpspm.travelplanner.data.export.HtmlExporter
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.action_ok
import com.jpspm.travelplanner.resources.html_export_failed
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.compose.rememberShareFileLauncher
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

/** Gera o HTML do roteiro e abre o menu de compartilhar do sistema (RF-30). */
class HtmlExportViewModel(private val exporter: HtmlExporter) : ViewModel() {
    var exporting by mutableStateOf(false)
        private set
    var ready by mutableStateOf<PlatformFile?>(null)
        private set
    var failed by mutableStateOf(false)
        private set

    fun export(tripId: String) {
        if (exporting) return
        exporting = true
        viewModelScope.launch {
            try {
                ready = exporter.export(tripId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
            } finally {
                exporting = false
            }
        }
    }

    fun shared() {
        ready = null
    }

    fun dismissError() {
        failed = false
    }
}

/** Compartilha o arquivo assim que ele fica pronto e mostra o erro, se houver. */
@Composable
fun HtmlExportEffects(viewModel: HtmlExportViewModel) {
    val share = rememberShareFileLauncher()
    val ready = viewModel.ready
    LaunchedEffect(ready) {
        if (ready != null) {
            share.launch(ready)
            viewModel.shared()
        }
    }
    if (viewModel.failed) {
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            text = { Text(stringResource(Res.string.html_export_failed)) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text(stringResource(Res.string.action_ok)) } },
        )
    }
}
