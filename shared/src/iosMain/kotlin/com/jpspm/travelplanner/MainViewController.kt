package com.jpspm.travelplanner

import androidx.compose.ui.window.ComposeUIViewController
import com.jpspm.travelplanner.data.local.createDatabase
import com.jpspm.travelplanner.di.initKoin
import com.jpspm.travelplanner.platform.Connectivity
import com.jpspm.travelplanner.platform.IosConnectivity
import com.jpspm.travelplanner.platform.HomeWidgetUpdater
import com.jpspm.travelplanner.platform.NoBackground
import com.jpspm.travelplanner.platform.ReminderScheduler
import org.koin.dsl.module

private val koinStarted by lazy {
    initKoin(
        module {
            single { createDatabase() }
            single<Connectivity> { IosConnectivity() }
            single<ReminderScheduler> { NoBackground }
            single<HomeWidgetUpdater> { NoBackground }
        }
    )
}

fun MainViewController() = ComposeUIViewController {
    koinStarted
    App()
}
