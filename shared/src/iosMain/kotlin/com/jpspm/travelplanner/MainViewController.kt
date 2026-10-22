package com.jpspm.travelplanner

import androidx.compose.ui.window.ComposeUIViewController
import com.jpspm.travelplanner.data.local.createDatabase
import com.jpspm.travelplanner.di.initKoin
import com.jpspm.travelplanner.platform.Connectivity
import com.jpspm.travelplanner.platform.IosConnectivity
import org.koin.dsl.module

private val koinStarted by lazy {
    initKoin(
        module {
            single { createDatabase() }
            single<Connectivity> { IosConnectivity() }
        }
    )
}

fun MainViewController() = ComposeUIViewController {
    koinStarted
    App()
}
