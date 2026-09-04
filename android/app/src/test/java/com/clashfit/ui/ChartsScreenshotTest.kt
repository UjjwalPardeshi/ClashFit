package com.clashfit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import com.clashfit.ui.components.BarChart
import com.clashfit.ui.components.ChartCard
import com.clashfit.ui.components.ChartLegend
import com.clashfit.ui.components.DonutChart
import com.clashfit.ui.components.Heatmap
import com.clashfit.ui.components.RadarChart
import com.clashfit.ui.components.StackedBar
import com.clashfit.ui.components.TrendLine
import com.clashfit.ui.theme.ClashFitTheme
import com.clashfit.ui.theme.Clean
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Ok
import com.clashfit.ui.theme.Shallow
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sin
import kotlin.random.Random

/**
 * Every chart with realistic data, on one sheet. The app's own screens render empty on a fresh
 * database, so this is the only way to see whether the charts actually read well.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ChartsScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun chartGallery() {
        // A plausible eight weeks of training: improving, with the odd bad day.
        val rng = Random(7)
        val form = List(20) { i -> (0.52f + i * 0.014f + sin(i * 1.7f) * 0.05f).coerceIn(0f, 1f) }
        val week = listOf(24f, 0f, 31f, 18f, 0f, 42f, 27f)
        val days = List(84) { if (rng.nextInt(10) < 6) rng.nextFloat() else 0f }

        compose.setContent {
            ClashFitTheme {
                Column(
                    Modifier.fillMaxSize().background(Ground).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ChartCard("Form over your last 20 sessions", subtitle = "The dashed line is your average.") {
                        TrendLine(form, marker = form.average().toFloat())
                    }
                    ChartCard("Reps this week") {
                        BarChart(week, listOf("M", "T", "W", "T", "F", "S", "S"), unit = " reps")
                    }
                    ChartCard("Last twelve weeks", subtitle = "One square a day. Brighter is more reps.") {
                        Heatmap(days, weeks = 12)
                    }
                    ChartCard("Every rep you have ever done", subtitle = "How the referee graded them") {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            DonutChart(listOf(Clean to 412, Ok to 233, Shallow to 96)) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("741", style = MaterialTheme.typography.headlineSmall, color = Ink)
                                    Text("REPS", style = MaterialTheme.typography.labelSmall, color = InkMuted)
                                }
                            }
                            ChartLegend(
                                listOf(
                                    Triple(Clean, "Clean", "412"),
                                    Triple(Ok, "Ok", "233"),
                                    Triple(Shallow, "Shallow", "96"),
                                ),
                                Modifier.weight(1f),
                            )
                        }
                    }
                    ChartCard("This set", subtitle = "Verdict for every rep, in order") {
                        StackedBar(listOf(Clean to 12, Ok to 5, Shallow to 2), description = "12 clean, 5 ok, 2 shallow")
                    }
                    ChartCard("Your shape", subtitle = "Seven domains, out of 100") {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            RadarChart(
                                listOf(
                                    "PWR" to 0.72f, "STA" to 0.55f, "FOC" to 0.81f, "MOB" to 0.40f,
                                    "ENR" to 0f, "NUT" to 0f, "RES" to 0.63f,
                                ),
                                size = 300,
                            )
                        }
                    }
                }
            }
        }
        repeat(4) { Thread.sleep(250); compose.waitForIdle() }
        compose.onRoot().captureRoboImage("screenshots/23-charts.png")
    }
}
