package com.jpspm.travelplanner.di

import com.jpspm.travelplanner.data.backup.LocalBackupRepository
import com.jpspm.travelplanner.data.export.HtmlExporter
import com.jpspm.travelplanner.data.files.FileStore
import com.jpspm.travelplanner.ui.trip.BudgetViewModel
import com.jpspm.travelplanner.ui.trip.ChecklistViewModel
import com.jpspm.travelplanner.ui.trip.IdeasViewModel
import com.jpspm.travelplanner.ui.editors.IdeaEditorViewModel
import com.jpspm.travelplanner.ui.trip.HtmlExportViewModel
import com.jpspm.travelplanner.data.settings.SettingsStore
import com.jpspm.travelplanner.domain.repository.BackupRepository
import com.jpspm.travelplanner.ui.backup.BackupViewModel
import com.jpspm.travelplanner.data.remote.OpenPlaceSearch
import com.jpspm.travelplanner.data.remote.defaultHttpClient
import com.jpspm.travelplanner.data.repository.PlaceWriter
import com.jpspm.travelplanner.domain.repository.PlaceSearch
import com.jpspm.travelplanner.ui.place.PlacePickerViewModel
import io.ktor.client.HttpClient
import com.jpspm.travelplanner.data.repository.LocalActivityRepository
import com.jpspm.travelplanner.data.repository.LocalBudgetRepository
import com.jpspm.travelplanner.data.repository.LocalChecklistRepository
import com.jpspm.travelplanner.data.repository.LocalIdeaRepository
import com.jpspm.travelplanner.domain.repository.IdeaRepository
import com.jpspm.travelplanner.domain.repository.BudgetRepository
import com.jpspm.travelplanner.domain.repository.ChecklistRepository
import com.jpspm.travelplanner.data.repository.LocalMapSnapshotRepository
import com.jpspm.travelplanner.domain.repository.MapSnapshotRepository
import com.jpspm.travelplanner.data.repository.LocalDestinationRepository
import com.jpspm.travelplanner.data.repository.LocalReservationRepository
import com.jpspm.travelplanner.data.repository.LocalTripRepository
import com.jpspm.travelplanner.domain.repository.ActivityRepository
import com.jpspm.travelplanner.domain.repository.DestinationRepository
import com.jpspm.travelplanner.domain.repository.ReservationRepository
import com.jpspm.travelplanner.domain.repository.TripRepository
import com.jpspm.travelplanner.ui.editors.ActivityEditorViewModel
import com.jpspm.travelplanner.ui.editors.DestinationEditorViewModel
import com.jpspm.travelplanner.ui.editors.ReservationEditorViewModel
import com.jpspm.travelplanner.ui.reservation.AttachmentViewerViewModel
import com.jpspm.travelplanner.ui.reservation.ReservationDetailViewModel
import com.jpspm.travelplanner.ui.trip.TripDetailViewModel
import com.jpspm.travelplanner.ui.trips.TripEditorViewModel
import com.jpspm.travelplanner.ui.trips.TripsViewModel
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.time.Clock

val dataModule = module {
    single<Clock> { Clock.System }
    single { FileStore() }
    single<HttpClient> { defaultHttpClient() }
    single<PlaceSearch> { OpenPlaceSearch(get(), get()) }
    single { PlaceWriter(get(), get(), get(), get()) }
    single { SettingsStore() }
    single<BackupRepository> { LocalBackupRepository(get(), get(), get(), get(), get()) }
    single<TripRepository> { LocalTripRepository(get(), get(), get()) }
    single<DestinationRepository> { LocalDestinationRepository(get(), get(), get(), get()) }
    single<ActivityRepository> { LocalActivityRepository(get(), get(), get(), get()) }
    single<ReservationRepository> { LocalReservationRepository(get(), get(), get(), get()) }
    single<MapSnapshotRepository> { LocalMapSnapshotRepository(get(), get(), get()) }
    single<BudgetRepository> { LocalBudgetRepository(get(), get()) }
    single<ChecklistRepository> { LocalChecklistRepository(get(), get()) }
    single<IdeaRepository> { LocalIdeaRepository(get(), get(), get(), get()) }
    single { HtmlExporter(get(), get(), get(), get(), get(), get()) }
}

val uiModule = module {
    viewModel { TripsViewModel(get(), get(), get(), get(), get()) }
    viewModel { params -> TripEditorViewModel(params.getOrNull(), get()) }
    viewModel { params -> TripDetailViewModel(params.get(), get(), get(), get(), get(), get(), get()) }
    viewModel { params -> DestinationEditorViewModel(params[0], params.values.getOrNull(1) as String?, get(), get()) }
    viewModel { params ->
        ActivityEditorViewModel(params[0], params.values.getOrNull(1) as String?, params.values.getOrNull(2) as String?, get(), get())
    }
    viewModel { params ->
        ReservationEditorViewModel(
            params[0], params.values.getOrNull(1) as String?, params.values.getOrNull(2) as String?,
            params.values.getOrNull(3) as String?, get(), get(), get(),
        )
    }
    viewModel { params -> ReservationDetailViewModel(params.get(), get(), get(), get()) }
    viewModel { params -> AttachmentViewerViewModel(params.get(), get()) }
    viewModel { PlacePickerViewModel(get()) }
    viewModel { BackupViewModel(get()) }
    viewModel { HtmlExportViewModel(get()) }
    viewModel { params -> BudgetViewModel(params.get(), get(), get(), get()) }
    viewModel { params -> ChecklistViewModel(params.get(), get()) }
    viewModel { params -> IdeasViewModel(params.get(), get(), get()) }
    viewModel { params -> IdeaEditorViewModel(params[0], params.values.getOrNull(1) as String?, get(), get()) }
}

/** [platformModule] fornece o [com.jpspm.travelplanner.data.local.AppDatabase] da plataforma. */
fun initKoin(platformModule: Module): KoinApplication = startKoin {
    modules(platformModule, dataModule, uiModule)
}
