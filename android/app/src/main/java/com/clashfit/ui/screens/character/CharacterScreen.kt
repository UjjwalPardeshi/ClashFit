package com.clashfit.ui.screens.character

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.core.config.ExerciseSpec
import com.clashfit.core.model.Family
import com.clashfit.data.SessionEntity
import com.clashfit.data.StreakEntity
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.Bar
import com.clashfit.ui.components.ChartCard
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.RadarChart
import com.clashfit.ui.components.RuleRow
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.ScreenEmptyState
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.nav.Modes
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.grouped
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.theme.Brass
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Fresh
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Success
import com.clashfit.ui.theme.Working

/**
 * The character sheet: seven domains, one shape. Five of them are earned from measured reps. Two
 * need sleep and food data this build does not collect, and they say so rather than showing a
 * number nobody earned. docs/22-HEALTH-DOMAINS.md §1
 */
@Composable
fun CharacterScreen(graph: AppGraph, nav: NavHostController) {
    val sessionsFlow = remember(graph) { graph.db.sessions().recent(limit = 200) }
    val streakFlow = remember(graph) { graph.db.streak().observe() }
    val sessions by sessionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val streak by streakFlow.collectAsStateWithLifecycle(initialValue = null)
    val exercises by graph.config.exercises.collectAsStateWithLifecycle()
    val sheet = remember(sessions, streak, exercises) { CharacterStats.compute(sessions, streak, exercises) }

    ScreenScaffold(title = "Character", onBack = { nav.navigateUp() }) { padding ->
        if (sheet.sessions == 0) {
            // Seven domains at zero draw a dot in the middle of a spider web, and a page of empty
            // bars under it. Nothing there is information — so the page waits, centred, with the
            // one thing worth tapping.
            ScreenEmptyState(
                title = "Your sheet is blank",
                body = "Seven domains, each earned from movement the camera measured — never from a " +
                    "plan you wrote when you were fresh. One fight starts filling it in.",
                modifier = Modifier.padding(padding),
                icon = AppIcons.Shield,
                action = { PrimaryButton("Start training") { nav.navigate(Modes) } },
            )
            return@ScreenScaffold
        }
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 28.dp),
        ) {
            Text(
                "Every domain is earned from measured movement, never from a plan you wrote when you were fresh.",
                style = MaterialTheme.typography.bodyMedium, color = InkMuted,
            )

            SectionGap(16)
            ChartCard("Your shape", subtitle = "Seven domains, out of 100") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    RadarChart(
                        // The full name. "NUT" is not a word for nourishment, and a chart whose
                            // axes need a decoder is not showing anybody anything.
                            stats = Domain.entries.map { it.title to sheet.value(it) / 100f },
                        size = 300,
                        color = Ember,
                    )
                }
            }

            SectionGap(20)
            SectionTitle("Earned from your reps")
            SectionGap(10)
            AppCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Domain.entries.filter { it.measured }.forEach { d ->
                        DomainBar(d, sheet.value(d))
                    }
                }
            }

            SectionGap(20)
            SectionTitle("Not tracked yet")
            SectionGap(10)
            AppCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Domain.entries.filterNot { it.measured }.forEach { d ->
                        DomainBar(d, 0f, dimmed = true)
                    }
                    Text(
                        "Energy comes from sleep and Nourishment from meals. ClashFit measures neither, so it will not " +
                            "put a number on either one.",
                        style = MaterialTheme.typography.bodySmall, color = InkFaint,
                    )
                }
            }

            SectionGap(20)
            SectionTitle("Totals")
            SectionGap(10)
            ListGroup {
                RuleRow("Sessions", "${sheet.sessions}")
                InnerDivider()
                RuleRow("Reps", "${sheet.totalReps.grouped()}")
                InnerDivider()
                RuleRow("Average form", "${(sheet.formAvg * 100).toInt()}%")
                InnerDivider()
                RuleRow("Movements tried", "${sheet.distinctExercises} of ${exercises.size}")
                InnerDivider()
                RuleRow("Current streak", "${streak?.current ?: 0} days")
            }
        }
    }
}

@Composable
private fun DomainBar(d: Domain, value: Float, dimmed: Boolean = false) {
    val shown = if (dimmed) 0f else value
    Column(
        Modifier.fillMaxWidth().semantics {
            contentDescription = if (dimmed) "${d.title}, not measured yet" else "${d.title}, ${shown.toInt()} of 100"
        },
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(d.title, style = MaterialTheme.typography.titleSmall, color = if (dimmed) InkMuted else Ink)
            Text(
                if (dimmed) "Not measured" else "${shown.toInt()}",
                style = MaterialTheme.typography.titleSmall,
                color = if (dimmed) InkFaint else d.color,
            )
        }
        Bar(shown / 100f, Modifier.padding(top = 8.dp), color = if (dimmed) InkFaint else d.color, height = 10)
        Text(d.source, style = MaterialTheme.typography.bodySmall, color = InkFaint, modifier = Modifier.padding(top = 6.dp))
    }
}

/** The seven domains, in the order the design doc lists them. */
enum class Domain(val title: String, val short: String, val source: String, val color: Color, val measured: Boolean) {
    POWER("Power", "PWR", "Strength rep quality and the damage it does", Ember, true),
    STAMINA("Stamina", "STA", "Cadence work and reps banked over time", Working, true),
    FOCUS("Focus", "FOC", "How steady your form holds as fatigue rises", Brass, true),
    MOBILITY("Mobility", "MOB", "Holds, yoga and the range of movements you train", Fresh, true),
    ENERGY("Energy", "ENR", "Sleep duration and regularity", InkMuted, false),
    NOURISHMENT("Nourishment", "NUT", "Meals and hydration logged", InkMuted, false),
    RESILIENCE("Resilience", "RES", "Days in a row, rest days included", Success, true),
}

/**
 * Turns a session history into the seven domains. Every number here is derived from something the
 * app actually measured; nothing is invented to fill a bar.
 */
object CharacterStats {

    data class Sheet(
        val power: Float = 0f,
        val stamina: Float = 0f,
        val focus: Float = 0f,
        val mobility: Float = 0f,
        val resilience: Float = 0f,
        val totalReps: Int = 0,
        val sessions: Int = 0,
        val formAvg: Float = 0f,
        val distinctExercises: Int = 0,
    ) {
        fun value(d: Domain): Float = when (d) {
            Domain.POWER -> power
            Domain.STAMINA -> stamina
            Domain.FOCUS -> focus
            Domain.MOBILITY -> mobility
            Domain.RESILIENCE -> resilience
            Domain.ENERGY, Domain.NOURISHMENT -> 0f
        }
    }

    fun compute(
        sessions: List<SessionEntity>,
        streak: StreakEntity?,
        exercises: Map<String, ExerciseSpec> = emptyMap(),
    ): Sheet {
        val resilience = (streak?.current ?: 0).coerceIn(0, 100).toFloat()
        if (sessions.isEmpty()) return Sheet(resilience = resilience)

        fun familyOf(s: SessionEntity): Family? = exercises[s.exerciseId]?.familyEnum
        fun inFamilies(vararg f: Family) = sessions.filter { familyOf(it) in f }

        val totalReps = sessions.sumOf { it.totalReps }
        val formAvg = sessions.map { it.formMean }.average().toFloat()

        // Power: damage from strength work, weighted by how clean it was.
        val strength = inFamilies(Family.REP_CYCLE)
        val power = if (strength.isEmpty()) 0f else {
            // Averaged over the last ten strength sessions rather than summed over all of them,
            // so Power reads as "how hard you hit lately" and does not peg at 100 after a fortnight.
            val recent = strength.take(10)
            val perSession = recent.sumOf { (it.totalDamage * it.formMean).toDouble() } / recent.size
            (perSession / 22.0).toFloat()
        }

        // Stamina: cadence work, plus the endurance that reps banked over time represent.
        val cardio = inFamilies(Family.CADENCE, Family.BALLISTIC)
        val stamina = (cardio.sumOf { it.totalReps } * 1.5f) + (totalReps / 12f)

        // Focus: how steady form stays as fatigue rises. A session that ends GASSED but keeps its
        // form is worth far more than an easy one, which is exactly what focus should mean.
        val underLoad = sessions.filter { it.peakBand == "FADING" || it.peakBand == "GASSED" }
        val focus = if (underLoad.isEmpty()) formAvg * 45f
        else (underLoad.map { it.formMean }.average().toFloat() * 100f)

        // Mobility: holds and yoga, plus the breadth of movements trained.
        val range = inFamilies(Family.ISOMETRIC_HOLD, Family.POSE_MATCH)
        val distinct = sessions.map { it.exerciseId }.distinct().size
        val mobility = (range.size * 6f) + (distinct * 5f)

        return Sheet(
            power = power.coerceIn(0f, 100f),
            stamina = stamina.coerceIn(0f, 100f),
            focus = focus.coerceIn(0f, 100f),
            mobility = mobility.coerceIn(0f, 100f),
            resilience = resilience,
            totalReps = totalReps,
            sessions = sessions.size,
            formAvg = formAvg,
            distinctExercises = distinct,
        )
    }
}

fun NavGraphBuilder.characterRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.Character> { CharacterScreen(graph, nav) }
}
