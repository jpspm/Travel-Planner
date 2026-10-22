package com.jpspm.travelplanner.di

import android.content.Context
import androidx.activity.ComponentActivity
import com.jpspm.travelplanner.data.local.createDatabase
import com.jpspm.travelplanner.platform.AndroidConnectivity
import com.jpspm.travelplanner.platform.Connectivity
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
        }
    ).androidContext(context.applicationContext)
}

/** Chamado no `onCreate` da Activity, antes de `setContent`, para os seletores de arquivo e câmera. */
fun bindActivity(activity: ComponentActivity) {
    FileKit.init(activity)
}
