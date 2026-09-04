package com.clashfit.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.LinkButton
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.EmberDeep
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.RuleSoft

private data class Page(val icon: ImageVector, val title: String, val body: String)

private val PAGES = listOf(
    Page(AppIcons.Bolt, "Exercise in front\nof the camera.", "Your phone watches your form, counts each rep, and scores the quality. Clean reps do more damage. Your camera never leaves your phone."),
    Page(AppIcons.Trophy, "Sixteen ways\nto fight.", "Boss fights, time attacks, survival, duels, raids, yoga sigils and a clinic protocol. Fifty-one exercises across five kinds of movement."),
    Page(AppIcons.People, "Race your\nfriends.", "Weekly challenges, a global and friends leaderboard, badges, and a level that only clean reps can raise."),
)

/** The first thing a new player sees. Three pages, two buttons, no wall of text. */
@Composable
fun WelcomeScreen(onCreateAccount: () -> Unit, onSignIn: () -> Unit) {
    val pager = rememberPagerState(pageCount = { PAGES.size })
    Column(Modifier.fillMaxSize().background(Ground).safeDrawingPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).clip(CircleShape).background(Ember), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Bolt, contentDescription = null, tint = Ground, modifier = Modifier.size(16.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text("ClashFit", style = MaterialTheme.typography.titleMedium, color = Ink)
        }

        HorizontalPager(pager, Modifier.weight(1f)) { i ->
            val p = PAGES[i]
            Column(
                Modifier.fillMaxSize().padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.size(168.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Ember, EmberDeep))),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(120.dp).clip(CircleShape).background(Ground.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        Icon(p.icon, contentDescription = null, tint = Ground, modifier = Modifier.size(64.dp))
                    }
                }
                Spacer(Modifier.height(36.dp))
                Text(p.title.uppercase(), style = MaterialTheme.typography.headlineLarge, color = Ink, textAlign = TextAlign.Center)
                Spacer(Modifier.height(14.dp))
                Text(p.body, style = MaterialTheme.typography.bodyLarge, color = InkMuted, textAlign = TextAlign.Center)
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(PAGES.size) { i ->
                val active = pager.currentPage == i
                Box(
                    Modifier.padding(4.dp).height(6.dp).width(if (active) 22.dp else 6.dp).clip(CircleShape)
                        .background(if (active) Ember else RuleSoft),
                )
            }
        }

        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            PrimaryButton("Create account", onClick = onCreateAccount)
            Spacer(Modifier.height(6.dp))
            LinkButton("I already have an account", onClick = onSignIn)
        }
    }
}
