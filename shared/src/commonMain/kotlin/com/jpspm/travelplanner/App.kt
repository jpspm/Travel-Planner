package com.jpspm.travelplanner

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import io.github.vinceglb.filekit.coil.addPlatformFileSupport
import io.ktor.client.HttpClient
import org.koin.compose.koinInject
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.jpspm.travelplanner.ui.navigation.backEnter
import com.jpspm.travelplanner.ui.navigation.backExit
import com.jpspm.travelplanner.ui.navigation.forwardEnter
import com.jpspm.travelplanner.ui.navigation.forwardExit
import com.jpspm.travelplanner.ui.navigation.screen
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.jpspm.travelplanner.ui.editors.ActivityEditorScreen
import com.jpspm.travelplanner.ui.editors.DestinationEditorScreen
import com.jpspm.travelplanner.ui.editors.ReservationEditorScreen
import com.jpspm.travelplanner.ui.navigation.ActivityEditorRoute
import com.jpspm.travelplanner.ui.navigation.AttachmentViewerRoute
import com.jpspm.travelplanner.ui.navigation.CityRoute
import com.jpspm.travelplanner.ui.navigation.IdeaEditorRoute
import com.jpspm.travelplanner.ui.navigation.NearbyRoute
import com.jpspm.travelplanner.ui.navigation.ShareTargetRoute
import com.jpspm.travelplanner.ui.share.ShareTargetScreen
import com.jpspm.travelplanner.data.background.IncomingShare
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavDestination.Companion.hasRoute
import com.jpspm.travelplanner.ui.trip.NearbyScreen
import com.jpspm.travelplanner.ui.editors.IdeaEditorScreen
import com.jpspm.travelplanner.ui.navigation.DestinationEditorRoute
import com.jpspm.travelplanner.ui.navigation.ReservationDetailRoute
import com.jpspm.travelplanner.ui.navigation.ReservationEditorRoute
import com.jpspm.travelplanner.ui.navigation.TripDetailRoute
import com.jpspm.travelplanner.ui.navigation.TripEditorRoute
import com.jpspm.travelplanner.ui.navigation.TripsRoute
import com.jpspm.travelplanner.ui.reservation.AttachmentViewerScreen
import com.jpspm.travelplanner.ui.reservation.ReservationDetailScreen
import com.jpspm.travelplanner.ui.theme.TravelPlannerTheme
import com.jpspm.travelplanner.ui.trip.CityScreen
import com.jpspm.travelplanner.ui.trip.TripDetailScreen
import com.jpspm.travelplanner.ui.trips.TripEditorScreen
import com.jpspm.travelplanner.ui.trips.TripsScreen

@Composable
fun App() {
    // Coil: fotos da Wikimedia pela internet (com o User-Agent do app) e arquivos escolhidos na galeria.
    val httpClient = koinInject<HttpClient>()
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components {
                add(KtorNetworkFetcherFactory(httpClient = { httpClient }))
                addPlatformFileSupport()
            }
            .build()
    }
    TravelPlannerTheme {
        val nav = rememberNavController()
        // Arquivos compartilhados de outro app abrem a escolha da reserva (RF-44).
        val incoming = koinInject<IncomingShare>()
        val sharedFiles by incoming.files.collectAsState()
        LaunchedEffect(sharedFiles) {
            if (sharedFiles.isNotEmpty() && nav.currentDestination?.hasRoute<ShareTargetRoute>() != true) nav.navigate(ShareTargetRoute)
        }
        // Fundo do tema atrás das telas: nada de branco aparecendo durante as transições.
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = nav,
                startDestination = TripsRoute,
                enterTransition = forwardEnter,
                exitTransition = forwardExit,
                popEnterTransition = backEnter,
                popExitTransition = backExit,
            ) {
                screen<TripsRoute> {
                    TripsScreen(
                        onNewTrip = { nav.navigate(TripEditorRoute()) },
                        onOpenTrip = { nav.navigate(TripDetailRoute(it)) },
                        onOpenReservation = { nav.navigate(ReservationDetailRoute(it)) },
                        onOpenAttachment = { nav.navigate(AttachmentViewerRoute(it)) },
                    )
                }
                screen<TripEditorRoute> { entry ->
                    val route = entry.toRoute<TripEditorRoute>()
                    TripEditorScreen(
                        tripId = route.tripId,
                        onBack = nav::back,
                        onSaved = { id ->
                            if (route.tripId == null) {
                                nav.navigate(TripDetailRoute(id)) { popUpTo<TripsRoute>() }
                            } else nav.back()
                        },
                    )
                }
                screen<TripDetailRoute> { entry ->
                    val tripId = entry.toRoute<TripDetailRoute>().tripId
                    TripDetailScreen(
                        tripId = tripId,
                        onBack = nav::back,
                        onEditTrip = { nav.navigate(TripEditorRoute(tripId)) },
                        onOpenTrip = { nav.navigate(TripDetailRoute(it)) { popUpTo<TripsRoute>() } },
                        onNewActivity = { date -> nav.navigate(ActivityEditorRoute(tripId, date = date)) },
                        onEditActivity = { nav.navigate(ActivityEditorRoute(tripId, activityId = it)) },
                        onNewReservation = { type, date -> nav.navigate(ReservationEditorRoute(tripId, type = type.name, date = date)) },
                        onImportReservation = { nav.navigate(ReservationEditorRoute(tripId, fromImport = true)) },
                        onOpenReservation = { nav.navigate(ReservationDetailRoute(it)) },
                        onNewDestination = { nav.navigate(DestinationEditorRoute(tripId)) },
                        onOpenCity = { nav.navigate(CityRoute(tripId, it)) },
                    onOpenIdea = { nav.navigate(IdeaEditorRoute(tripId, it)) },
                    onNewIdea = { kind -> nav.navigate(IdeaEditorRoute(tripId, kind = kind)) },
                    onScheduleIdeaAsReservation = { ideaId, type, date ->
                        nav.navigate(ReservationEditorRoute(tripId, type = type.name, date = date, ideaId = ideaId))
                    },
                    onOpenNearby = { nav.navigate(NearbyRoute(tripId)) },
                    )
                }
                screen<ShareTargetRoute> {
                    ShareTargetScreen(
                        onDone = { id -> nav.navigate(ReservationDetailRoute(id)) { popUpTo<ShareTargetRoute> { inclusive = true } } },
                        onCreateReservation = { tripId ->
                            nav.navigate(ReservationEditorRoute(tripId, fromImport = true)) { popUpTo<ShareTargetRoute> { inclusive = true } }
                        },
                        onCancel = nav::back,
                    )
                }
                screen<NearbyRoute> { entry ->
                    NearbyScreen(entry.toRoute<NearbyRoute>().tripId, onBack = nav::back)
                }
                screen<IdeaEditorRoute> { entry ->
                    val route = entry.toRoute<IdeaEditorRoute>()
                    IdeaEditorScreen(route.tripId, route.ideaId, route.kind, onBack = nav::back)
                }
                screen<CityRoute> { entry ->
                    val route = entry.toRoute<CityRoute>()
                    CityScreen(
                        tripId = route.tripId,
                        cityKey = route.cityKey,
                        onBack = nav::back,
                        onEditDestination = { nav.navigate(DestinationEditorRoute(route.tripId, it)) },
                        onOpenActivity = { nav.navigate(ActivityEditorRoute(route.tripId, activityId = it)) },
                        onOpenReservation = { nav.navigate(ReservationDetailRoute(it)) },
                    )
                }
                screen<DestinationEditorRoute> { entry ->
                    val route = entry.toRoute<DestinationEditorRoute>()
                    DestinationEditorScreen(route.tripId, route.destinationId, onBack = nav::back)
                }
                screen<ActivityEditorRoute> { entry ->
                    val route = entry.toRoute<ActivityEditorRoute>()
                    ActivityEditorScreen(route.tripId, route.activityId, route.date, onBack = nav::back)
                }
                screen<ReservationEditorRoute> { entry ->
                    val route = entry.toRoute<ReservationEditorRoute>()
                    ReservationEditorScreen(
                        tripId = route.tripId,
                        reservationId = route.reservationId,
                        type = route.type,
                        fromImport = route.fromImport,
                        date = route.date,
                    ideaId = route.ideaId,
                        onBack = nav::back,
                        onSaved = { id ->
                            // Reserva nova: abre o detalhe, onde se adicionam os anexos (QR code, PDF).
                            if (route.reservationId == null) {
                                nav.navigate(ReservationDetailRoute(id)) { popUpTo<ReservationEditorRoute> { inclusive = true } }
                            } else nav.back()
                        },
                    )
                }
                screen<ReservationDetailRoute> { entry ->
                    val reservationId = entry.toRoute<ReservationDetailRoute>().reservationId
                    ReservationDetailScreen(
                        reservationId = reservationId,
                        onBack = nav::back,
                        onEdit = { tripId -> nav.navigate(ReservationEditorRoute(tripId, reservationId = reservationId)) },
                        onOpenAttachment = { nav.navigate(AttachmentViewerRoute(it)) },
                    )
                }
                screen<AttachmentViewerRoute> { entry ->
                    AttachmentViewerScreen(entry.toRoute<AttachmentViewerRoute>().attachmentId, onClose = nav::back)
                }
            }
        }
    }
}

/** Volta uma tela, ignorando toques repetidos que esvaziariam a pilha. */
private fun NavHostController.back() {
    if (previousBackStackEntry != null) popBackStack()
}
