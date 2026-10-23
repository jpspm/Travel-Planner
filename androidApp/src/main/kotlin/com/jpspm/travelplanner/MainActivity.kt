package com.jpspm.travelplanner

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.jpspm.travelplanner.di.bindActivity
import com.jpspm.travelplanner.di.onNewIntent as handleNewIntent
import java.util.Locale

class MainActivity : ComponentActivity() {

    /**
     * A interface é só em pt-BR (RNF-17). Fixar o idioma aqui faz os componentes do sistema, como o
     * calendário e o relógio do Material, também aparecerem em português num aparelho em outro idioma.
     */
    override fun attachBaseContext(newBase: Context) {
        val locale = Locale.forLanguageTag("pt-BR")
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration).apply { setLocale(locale) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        bindActivity(this)

        setContent {
            App()
        }
    }

    /** Arquivo compartilhado com o app já aberto (RF-44). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNewIntent(this, intent)
    }
}
