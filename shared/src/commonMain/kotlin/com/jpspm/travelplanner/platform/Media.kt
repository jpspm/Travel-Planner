package com.jpspm.travelplanner.platform

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.flow.StateFlow

/** Codifica a imagem em JPEG (imagens estáticas do mapa, RF-15). */
expect fun ImageBitmap.encodeJpeg(quality: Int = 85): ByteArray

/** Estado da conexão: sem internet, o roteiro mostra a imagem salva do mapa (RNF-02). */
interface Connectivity {
    val online: StateFlow<Boolean>
}
