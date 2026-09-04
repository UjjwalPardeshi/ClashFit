package com.clashfit

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.clashfit.ui.nav.AppNavHost
import com.clashfit.ui.theme.ClashFitTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Single activity, Compose host. */
class MainActivity : ComponentActivity() {
    private val _deskExerciseId = MutableStateFlow<String?>(null)
    val deskExerciseId = _deskExerciseId.asStateFlow()

    private val _shortcutAction = MutableStateFlow<String?>(null)
    val shortcutAction = _shortcutAction.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIntentExtras(intent)

        val graph = AppGraph.of(this)
        setContent {
            ClashFitTheme {
                AppNavHost(graph, deskExerciseId, shortcutAction)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntentExtras(intent)
    }

    private fun handleIntentExtras(intent: Intent) {
        // Handle desk exercise deep-link
        val exerciseId = intent.getStringExtra("desk_exercise_id")
        if (exerciseId != null) {
            _deskExerciseId.value = exerciseId
        }

        // Handle shortcut actions
        val action = intent.getStringExtra("shortcut_action")
        if (action != null) {
            _shortcutAction.value = action
        }
    }
}
