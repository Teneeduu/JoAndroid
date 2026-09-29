package com.teneeduu.jo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.teneeduu.jo.ui.JoApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val openSettings = intent.getBooleanExtra(EXTRA_OPEN_SETTINGS, false)
        setContent { JoApp(openSettingsAtLaunch = openSettings) }
    }

    companion object {
        /** Opens Jo straight on the settings sheet; CI uses it to screenshot that screen. */
        const val EXTRA_OPEN_SETTINGS = "open_settings"
    }
}
