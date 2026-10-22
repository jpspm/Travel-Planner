package com.jpspm.travelplanner.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Mantém a tela ligada e no brilho máximo enquanto estiver na composição (RF-19). */
@Composable
expect fun KeepScreenBright()

/** Link que abre o local no app de mapas/navegação do aparelho (RF-16). */
expect fun externalMapUri(latitude: Double, longitude: Double, label: String): String

/** Exibe um PDF local, página por página. */
@Composable
expect fun PdfView(path: String, modifier: Modifier = Modifier)
