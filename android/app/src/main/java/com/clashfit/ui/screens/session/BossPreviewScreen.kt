package com.clashfit.ui.screens.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.core.model.CombatState
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.FilterPill
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import kotlinx.coroutines.delay

/**
 * The boss, on its own, with every state one tap away.
 *
 * The 3D renderer is native code, and the only way to see it working used to be to start a fight
 * and do squats until the boss changed phase. That is a terrible way to find out whether a device
 * can run it, and an impossible one thirty seconds before a demo.
 *
 * This is also the answer to "show me the boss" from someone who does not want to exercise first.
 */
@Composable
fun BossPreviewScreen(graph: AppGraph, nav: NavHostController) {
    val settings by graph.prefs.settings.collectAsStateWithLifecycle(initialValue = null)
    var state by remember { mutableStateOf(States.first()) }
    // A hit is a spike, not a state, so it decays on its own like it does in a fight.
    var jolt by remember { mutableStateOf(0f) }
    var hitTick by remember { mutableStateOf(0) }
    // What is actually rendering, reported by the stage rather than assumed from the setting.
    var live3d by remember { mutableStateOf(false) }
    LaunchedEffect(hitTick) {
        if (hitTick == 0) return@LaunchedEffect
        jolt = 1f
        repeat(12) { delay(16); jolt = (jolt - 0.085f).coerceAtLeast(0f) }
        jolt = 0f
    }

    ScreenScaffold(title = "Boss preview", onBack = { nav.navigateUp() }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Box(Modifier.weight(1f).fillMaxWidth().background(Ground)) {
                BossStage(
                    state.combat,
                    jolt = jolt,
                    shake = 0f,
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    allow3d = settings?.boss3d ?: true,
                    onRenderer = { live3d = it },
                )
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text(state.title, style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(state.why, style = MaterialTheme.typography.bodySmall, color = InkMuted)

                SectionGap(12)
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    States.forEach { s ->
                        FilterPill(s.short, selected = s.title == state.title, onClick = { state = s })
                    }
                    FilterPill("Hit", selected = false, onClick = { hitTick++ })
                }

                SectionGap(12)
                AppCard(Modifier.fillMaxWidth(), padding = 12) {
                    Text(
                        when {
                            live3d ->
                                "Showing the 3D model. If it looks wrong on this device, turn off " +
                                    "3D boss in Settings — the fight is identical either way."
                            settings?.boss3d == false ->
                                "Showing the drawn boss, because 3D boss is off in Settings."
                            else ->
                                "Showing the drawn boss: the 3D renderer would not start on this " +
                                    "device, so the app fell back on its own. Nothing else changes."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = InkFaint,
                    )
                }
            }
        }
    }
}

/** One entry per state the boss can be in, and the fight condition that produces it. */
private data class BossState(
    val title: String,
    val short: String,
    val why: String,
    val combat: CombatState,
)

private fun combat(
    hp: Int,
    phase: String,
    staggered: Boolean = false,
    dead: Boolean = false,
) = CombatState(
    bossId = "pacemaker", bossName = "The Pacemaker", hp = hp, maxHp = 1200,
    phaseLabel = phase, phaseModifier = 1f, reps = 0, totalDamage = 1200 - hp,
    dead = dead, comboStreak = 0, comboMultiplier = 1f, lastDamage = null,
    staggered = staggered, mercyActive = false,
)

private val States = listOf(
    BossState("Opening", "Idle", "Full health. Breathing, ring turning, waiting.", combat(1200, "opening")),
    BossState("Enrage", "Enrage", "Below sixty per cent. Hunched, faster, guard up.", combat(660, "enrage")),
    BossState("Desperation", "Desperate", "Below thirty. Everything it has left.", combat(300, "desperation")),
    BossState("Staggered", "Stagger", "The shell opens and the core is exposed. Hit it now.", combat(240, "desperation", staggered = true)),
    BossState("Down", "Death", "Plates blow off, the knees go, the core is last out.", combat(0, "desperation", dead = true)),
)

fun NavGraphBuilder.bossPreviewRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.BossPreview> { BossPreviewScreen(graph, nav) }
}
