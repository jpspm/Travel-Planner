package com.jpspm.travelplanner.ui.reservation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.jpspm.travelplanner.domain.model.AttachmentKind
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.attachment_add
import com.jpspm.travelplanner.resources.attachment_add_camera
import com.jpspm.travelplanner.resources.attachment_add_image
import com.jpspm.travelplanner.resources.attachment_add_pdf
import com.jpspm.travelplanner.resources.attachment_adding
import com.jpspm.travelplanner.resources.attachment_delete_title
import com.jpspm.travelplanner.resources.attachment_open
import com.jpspm.travelplanner.resources.attachments_empty
import com.jpspm.travelplanner.resources.section_attachments
import com.jpspm.travelplanner.ui.components.SectionHeader
import com.jpspm.travelplanner.ui.components.label
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberCameraPickerLauncher
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import org.jetbrains.compose.resources.stringResource

/** Anexo exibido na lista: já salvo ([model] = caminho local) ou recém-escolhido ([model] = arquivo). */
data class AttachmentItem(
    val key: String,
    val kind: AttachmentKind,
    val name: String,
    val model: Any?,
)

/**
 * Anexos de uma reserva (RF-18): imagem ou print da galeria (QR code, ingresso), foto da câmera ou PDF.
 * Usado no detalhe da reserva e no formulário.
 */
@Composable
fun AttachmentsBlock(
    items: List<AttachmentItem>,
    onAdd: (PlatformFile, AttachmentKind) -> Unit,
    onRemove: (AttachmentItem) -> Unit,
    onOpen: ((AttachmentItem) -> Unit)? = null,
    busy: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val imagePicker = rememberFilePickerLauncher(type = FileKitType.Image) { file -> file?.let { onAdd(it, AttachmentKind.QR) } }
    val pdfPicker = rememberFilePickerLauncher(type = FileKitType.File("pdf")) { file -> file?.let { onAdd(it, AttachmentKind.PDF) } }
    val camera = rememberCameraPickerLauncher { file -> file?.let { onAdd(it, AttachmentKind.PHOTO) } }

    SectionHeader(stringResource(Res.string.section_attachments))
    if (items.isEmpty()) {
        Text(
            stringResource(Res.string.attachments_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    } else {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            items.forEach { item ->
                AttachmentTile(item, onClick = onOpen?.let { { it(item) } }, onRemove = { onRemove(item) })
            }
        }
    }
    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        OutlinedButton(onClick = { menuOpen = true }, enabled = !busy) {
            Icon(Icons.Default.Add, contentDescription = null)
            Text(
                stringResource(if (busy) Res.string.attachment_adding else Res.string.attachment_add),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text(stringResource(Res.string.attachment_add_image)) }, onClick = { menuOpen = false; imagePicker.launch() })
            DropdownMenuItem(text = { Text(stringResource(Res.string.attachment_add_pdf)) }, onClick = { menuOpen = false; pdfPicker.launch() })
            DropdownMenuItem(text = { Text(stringResource(Res.string.attachment_add_camera)) }, onClick = { menuOpen = false; camera.launch() })
        }
    }
}

@Composable
private fun AttachmentTile(item: AttachmentItem, onClick: (() -> Unit)?, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.size(width = 104.dp, height = 148.dp)) {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(104.dp).clip(shape).let { m -> if (onClick != null) m.clickable(onClick = onClick) else m },
        ) {
            if (item.kind == AttachmentKind.PDF || item.model == null) {
                Box(contentAlignment = Alignment.Center) { Text("📄", style = MaterialTheme.typography.displaySmall) }
            } else {
                AsyncImage(
                    model = item.model,
                    contentDescription = stringResource(Res.string.attachment_open, item.name),
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(item.kind.label),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(Res.string.attachment_delete_title),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
