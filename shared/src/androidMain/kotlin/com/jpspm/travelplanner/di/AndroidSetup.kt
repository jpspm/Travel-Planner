package com.jpspm.travelplanner.di

import android.content.Context
import androidx.activity.ComponentActivity
import com.jpspm.travelplanner.data.local.createDatabase
import com.jpspm.travelplanner.platform.AndroidConnectivity
import com.jpspm.travelplanner.platform.Connectivity
import com.jpspm.travelplanner.platform.AndroidHomeWidgetUpdater
import com.jpspm.travelplanner.platform.AndroidReminderScheduler
import com.jpspm.travelplanner.platform.HomeWidgetUpdater
import com.jpspm.travelplanner.platform.ReminderScheduler
import com.jpspm.travelplanner.platform.handleShareIntent
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.dialogs.init
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** Chamado uma vez no `Application.onCreate`. */
fun initAndroid(context: Context) {
    initKoin(
        module {
            single { createDatabase(context) }
            single<Connectivity> { AndroidConnectivity(context.applicationContext) }
            single<ReminderScheduler> { AndroidReminderScheduler(context.applicationContext) }
            single<HomeWidgetUpdater> { AndroidHomeWidgetUpdater(context.applicationContext) }
        }
    ).androidContext(context.applicationContext)
}

/** Chamado no `onCreate` da Activity, antes de `setContent`, para os seletores de arquivo e câmera. */
fun bindActivity(activity: ComponentActivity) {
    FileKit.init(activity)
    handleShareIntent(activity, activity.intent)
}

/** Chamado no `onNewIntent` da Activity: arquivos compartilhados com o app já aberto (RF-44). */
fun onNewIntent(activity: ComponentActivity, intent: android.content.Intent) {
    handleShareIntent(activity, intent)
}
