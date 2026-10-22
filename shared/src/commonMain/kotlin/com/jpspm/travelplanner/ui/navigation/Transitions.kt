package com.jpspm.travelplanner.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

private const val DURATION_MS = 300

/*
 * Navegação sempre na horizontal: avançar entra pela direita e empurra a tela atual para a esquerda;
 * voltar faz o caminho inverso.
 */
val forwardEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(DURATION_MS, easing = FastOutSlowInEasing))
}
val forwardExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(DURATION_MS, easing = FastOutSlowInEasing))
}
val backEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(DURATION_MS, easing = FastOutSlowInEasing))
}
val backExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(DURATION_MS, easing = FastOutSlowInEasing))
}

/**
 * `true` quando a tela terminou de entrar e não está saindo. O mapa é desenhado numa superfície nativa
 * que não acompanha a animação de deslizar; por isso ele só aparece com a tela parada.
 */
val LocalScreenSettled = compositionLocalOf { true }

/** Destino de navegação que informa [LocalScreenSettled] para o conteúdo. */
inline fun <reified T : Any> NavGraphBuilder.screen(noinline content: @Composable (NavBackStackEntry) -> Unit) {
    composable<T> { entry ->
        val settled = transition.currentState == EnterExitState.Visible && transition.targetState == EnterExitState.Visible
        CompositionLocalProvider(LocalScreenSettled provides settled) { content(entry) }
    }
}
