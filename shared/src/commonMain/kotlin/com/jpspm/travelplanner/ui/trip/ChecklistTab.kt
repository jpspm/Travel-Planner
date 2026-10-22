package com.jpspm.travelplanner.ui.trip

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jpspm.travelplanner.domain.repository.ChecklistItem
import com.jpspm.travelplanner.domain.repository.ChecklistRepository
import com.jpspm.travelplanner.resources.*
import com.jpspm.travelplanner.ui.components.EmptyState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class ChecklistViewModel(private val tripId: String, private val checklist: ChecklistRepository) : ViewModel() {

    val items: StateFlow<List<ChecklistItem>?> = checklist.observe(tripId).map { it as List<ChecklistItem>? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun add(texts: List<String>) = viewModelScope.launch { checklist.add(tripId, texts) }
    fun setDone(id: String, done: Boolean) = viewModelScope.launch { checklist.setDone(id, done) }
    fun rename(id: String, text: String) = viewModelScope.launch { checklist.rename(id, text) }
    fun delete(id: String) = viewModelScope.launch { checklist.delete(id) }
}

/** Aba Checklist pré-viagem (RF-26). */
@Composable
fun ChecklistTab(tripId: String, modifier: Modifier, viewModel: ChecklistViewModel = koinViewModel { parametersOf(tripId) }) {
    val items by viewModel.items.collectAsStateWithLifecycle()
    var newText by remember { mutableStateOf("") }
    var renaming by remember { mutableStateOf<ChecklistItem?>(null) }
    val suggestions = listOf(
        stringResource(Res.string.checklist_suggestion_insurance),
        stringResource(Res.string.checklist_suggestion_documents),
        stringResource(Res.string.checklist_suggestion_bank),
        stringResource(Res.string.checklist_suggestion_sim),
        stringResource(Res.string.checklist_suggestion_adapter),
        stringResource(Res.string.checklist_suggestion_checkin),
        stringResource(Res.string.checklist_suggestion_offline),
        stringResource(Res.string.checklist_suggestion_backup),
    )
    val list = items ?: return

    Column(modifier.imePadding()) {
        if (list.isEmpty()) {
            Column(Modifier.weight(1f)) {
                EmptyState(
                    "✅",
                    stringResource(Res.string.checklist_empty_title),
                    stringResource(Res.string.checklist_empty_message),
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = { viewModel.add(suggestions) },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp),
                ) { Text(stringResource(Res.string.checklist_add_suggestions)) }
            }
        } else {
            val done = list.count { it.done }
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(Res.string.checklist_progress, done, list.size), style = MaterialTheme.typography.labelLarge)
                LinearProgressIndicator(progress = { done.toFloat() / list.size }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
                items(list, key = { it.id }) { item ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 4.dp)) {
                        Checkbox(checked = item.done, onCheckedChange = { viewModel.setDone(item.id, it) })
                        Text(
                            item.text,
                            style = MaterialTheme.typography.bodyLarge,
                            textDecoration = if (item.done) TextDecoration.LineThrough else null,
                            color = if (item.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f).clickable { renaming = item }.padding(vertical = 12.dp),
                        )
                        IconButton(onClick = { viewModel.delete(item.id) }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_delete))
                        }
                    }
                }
            }
        }
        // Campo para novo item, sempre visível embaixo.
        fun submit() {
            if (newText.isNotBlank()) viewModel.add(listOf(newText))
            newText = ""
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = newText,
                onValueChange = { newText = it },
                placeholder = { Text(stringResource(Res.string.checklist_new_item)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { submit() }, enabled = newText.isNotBlank()) {
                Icon(Icons.Default.Add, contentDescription = stringResource(Res.string.checklist_add))
            }
        }
    }

    renaming?.let { item ->
        var text by remember(item.id) { mutableStateOf(item.text) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text(stringResource(Res.string.checklist_rename)) },
            text = { OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { viewModel.rename(item.id, text); renaming = null }) { Text(stringResource(Res.string.action_save)) } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}
