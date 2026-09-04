package com.clashfit.ui.nav

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ground2
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel
import kotlin.reflect.KClass
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow

/**
 * The four top-level destinations. Every other screen belongs to exactly one of them, which is
 * how the bar knows which tab to light when you are three screens deep.
 *
 * The social tab is called Leaderboard because that is what people open it for. It still holds
 * friends, badges and challenges underneath, but naming a tab after the hub rather than after the
 * thing in the hub makes somebody read a label and guess.
 *
 * Library left the bar and moved under Train. Choosing what to train is part of training, and a
 * permanent tab for a list you visit once a week was spending a fifth of the bar on browsing.
 */
enum class Tab(val label: String, val icon: ImageVector, val root: Route, val owns: Set<KClass<out Route>>) {
    TRAIN(
        "Train", AppIcons.Bolt, Home,
        setOf(Home::class, Modes::class, ExercisePicker::class, Session::class, Summary::class,
            DuelLobby::class, RaidRoom::class, Roster::class,
            Library::class, ExerciseDetail::class,
            // The health tools are opened from this tab, so this tab owns them. A route belongs to
            // exactly one tab: reachable from two, the bar lights whichever one the owns-set
            // happens to name and disagrees with the stack you are actually in.
            RunHome::class, RunActive::class, RunSummary::class, Alarms::class, AlarmEdit::class,
            Breathing::class, Posture::class, Desk::class, Clinic::class),
    ),

    LEADERBOARD(
        "Leaderboard", AppIcons.Trophy, Compete,
        setOf(Compete::class, Leaderboard::class, Friends::class, Achievements::class,
            Weekly::class, Challenge::class),
    ),
    PROGRESS(
        "Progress", AppIcons.Chart, Progress,
        setOf(Progress::class, Streaks::class, History::class, Character::class, Ghosts::class),
    ),
    YOU(
        "You", AppIcons.Person, You,
        setOf(You::class, Settings::class, Privacy::class, Preflight::class,
            Account::class, About::class, Help::class, BossPreview::class),
    );

    companion object {
        fun owning(destination: NavDestination?): Tab? =
            destination?.let { d -> entries.firstOrNull { tab -> tab.owns.any { d.hasRoute(it) } } }
    }
}

/**
 * Screens where the bar would be in the way: a fight, a run in progress, a hold, a breathing
 * drill. The bar hides and the screen takes the whole display.
 */
private val IMMERSIVE: Set<KClass<out Route>> = setOf(
    Splash::class, Onboarding::class, SignUp::class, SignIn::class, ResetPassword::class, ProfileSetup::class, CameraPrimer::class,
    Session::class, RunActive::class,
    DuelLobby::class, RaidRoom::class, Roster::class, Breathing::class, Posture::class,
)

private fun NavDestination.isImmersive(): Boolean = IMMERSIVE.any { hasRoute(it) }

/**
 * Material's compact-to-medium breakpoint. Below it a bottom bar is right; at and above it the
 * navigation belongs down the side. This matters more than it used to: an app targeting SDK 36
 * or later does not get to lock orientation on a large screen, so the tablet and the unfolded
 * foldable will be landscape whatever the manifest says.
 */
private val WIDE_BREAKPOINT = 600.dp

/**
 * Switch tabs the way Android expects: each tab keeps its own stack, tapping a tab you are
 * already on returns you to its root, and the stack under the home tab is never discarded.
 */
fun NavHostController.switchTo(tab: Tab) {
    navigate(tab.root) {
        popUpTo<Home> { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** The shell that wraps every routed screen: a bottom bar on a phone, a rail on anything wider. */
@Composable
fun MainScaffold(nav: NavHostController, content: @Composable (PaddingValues) -> Unit) {
    val entry by nav.currentBackStackEntryAsState()
    val destination = entry?.destination
    val current = Tab.owning(destination)
    val showBar = destination != null && !destination.isImmersive()

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE_BREAKPOINT

        // Zero content insets on this Scaffold: the bar and the rail pad themselves against the
        // system bars, each screen's own top bar pads against the status bar, and AppNavHost
        // consumes what this hands down so the nested scaffold never pads the same edge twice.
        val scaffold: @Composable (@Composable () -> Unit) -> Unit = { bottom ->
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = bottom,
                content = content,
            )
        }

        if (wide && showBar) {
            Row(Modifier.fillMaxSize()) {
                // NavigationRail applies its own system-bar insets.
                NavigationRail(containerColor = Ground2, contentColor = InkMuted) {
                    Tab.entries.forEach { tab ->
                        NavigationRailItem(
                            selected = tab == current,
                            onClick = { nav.switchTo(tab) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { TabLabel(tab.label) },
                            alwaysShowLabel = true,
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = Ember,
                                selectedTextColor = Ember,
                                indicatorColor = Panel,
                                unselectedIconColor = InkMuted,
                                unselectedTextColor = InkMuted,
                            ),
                        )
                    }
                }
                scaffold {}
            }
        } else {
            scaffold {
                if (showBar) {
                    NavigationBar(containerColor = Ground2, contentColor = InkMuted, tonalElevation = 0.dp) {
                        Tab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = tab == current,
                                onClick = { nav.switchTo(tab) },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { TabLabel(tab.label) },
                                alwaysShowLabel = true,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Ember,
                                    selectedTextColor = Ember,
                                    indicatorColor = Panel,
                                    unselectedIconColor = InkMuted,
                                    unselectedTextColor = InkMuted,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A tab label that stays on one line.
 *
 * Four tabs give each label a quarter of the screen. "Leaderboard" fits at the default text size
 * and breaks into "Leaderboa / rd" at 1.5x — which is a worse outcome for the person who raised
 * the text size than a slightly smaller word would have been. The label is capped at 1.15x of
 * its own size; everything else on every screen still scales the whole way.
 */
@Composable
private fun TabLabel(text: String) {
    val scale = LocalDensity.current.fontScale
    val base = MaterialTheme.typography.labelMedium
    // Expressed in sp, which the renderer multiplies by the scale again — so dividing by the
    // scale here pins the drawn size, rather than merely reducing it a little.
    val style = if (scale > 1.15f) base.copy(fontSize = base.fontSize * (1.15f / scale)) else base
    Text(text, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}
