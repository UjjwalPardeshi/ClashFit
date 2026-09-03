package com.clashfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.clashfit.ui.nav.AppNavHost
import com.clashfit.ui.theme.ClashFitTheme

/** Single activity, Compose host. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = AppGraph.of(this)
        setContent {
            ClashFitTheme {
                AppNavHost(graph)
            }
        }
    }
}
