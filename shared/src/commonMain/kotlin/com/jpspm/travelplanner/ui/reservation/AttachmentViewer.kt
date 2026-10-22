package com.jpspm.travelplanner.ui.reservation

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.jpspm.travelplanner.domain.model.Attachment
import com.jpspm.travelplanner.domain.model.AttachmentKind
import com.jpspm.travelplanner.domain.repository.ReservationRepository
import com.jpspm.travelplanner.platform.KeepScreenBright
import com.jpspm.travelplanner.platform.PdfView
import com.jpspm.travelplanner.resources.Res
import com.jpspm.travelplanner.resources.action_close
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

class AttachmentViewerViewModel(
    attachmentId: String,
    reservations: ReservationRepository,
) : ViewModel() {
    var attachment by mutableStateOf<Attachment?>(null)
        private set

    init {
        viewModelScope.launch { attachment = reservations.getAttachment(attachmentId) }
    }
}

/**
 * Anexo em tela cheia, fundo preto e brilho máximo, para leitura do QR code no embarque
 * ou na portaria (RF-19). Imagens aceitam zoom com pinça.
 */
@Composable
fun AttachmentViewerScreen(
    attachmentId: String,
    onClose: () -> Unit,
    viewModel: AttachmentViewerViewModel = koinViewModel { parametersOf(attachmentId) },
) {
    KeepScreenBright()
    val attachment = viewModel.attachment
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            attachment == null -> Unit
            attachment.kind == AttachmentKind.PDF -> PdfView(attachment.filePath, Modifier.fillMaxSize().safeDrawingPadding())
            else -> ZoomableImage(attachment)
        }
        IconButton(
            onClick = onClose,
            colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.6f), contentColor = Color.White),
            modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding().padding(8.dp),
        ) {
            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.action_close))
        }
    }
}

@Composable
private fun ZoomableImage(attachment: Attachment) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    AsyncImage(
        model = attachment.filePath,
        contentDescription = attachment.fileName,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}
