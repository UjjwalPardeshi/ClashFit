package com.clashfit.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clashfit.AppGraph
import com.clashfit.meta.Achievement
import com.clashfit.meta.AchievementCatalog
import com.clashfit.meta.Tier
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.BadgeTile
import com.clashfit.ui.components.Bar
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.components.color
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted

/** Every badge, earned ones lit. Locked badges show their name, not how to get them. */
@Composable
fun AchievementsScreen(graph: AppGraph, onBack: () -> Unit) {
    val stateFlow = remember(graph) { graph.meta.state }
    val meta by stateFlow.collectAsStateWithLifecycle(initialValue = null)
    val unlocked = remember(meta) { meta?.unlocked?.map { it.id }?.toSet() ?: emptySet() }
    val all = AchievementCatalog.all
    val byTier = remember { Tier.entries.map { t -> t to all.filter { it.tier == t } } }

    ScreenScaffold(title = "Badges", onBack = onBack) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "head", span = { GridItemSpan(3) }) {
                Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Text("${unlocked.size} of ${all.size} earned", style = MaterialTheme.typography.titleLarge, color = Ink)
                    Text(
                        "Badges come from showing up and from clean reps. None of them can be bought, and none make the game easier.",
                        style = MaterialTheme.typography.bodySmall, color = InkMuted, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
                    )
                    Bar(if (all.isEmpty()) 0f else unlocked.size.toFloat() / all.size, height = 8)
                }
            }
            byTier.forEach { (tier, list) ->
                if (list.isEmpty()) return@forEach
                item(key = "tier-${tier.name}", span = { GridItemSpan(3) }) {
                    SectionTitle(
                        tier.name.lowercase().replaceFirstChar { it.uppercase() } + " · ${list.count { it.id in unlocked }}/${list.size}",
                        Modifier.padding(top = 12.dp, bottom = 2.dp),
                    )
                }
                items(list, key = { it.id }) { a ->
                    BadgeTile(a, unlocked = a.id in unlocked, icon = iconFor(a))
                }
            }
        }
    }
}

/** A glyph per badge family, from the id's prefix. */
fun iconFor(a: Achievement): ImageVector = when {
    a.id.startsWith("streak") -> AppIcons.Flame
    a.id.startsWith("clean") -> AppIcons.Check
    a.id.startsWith("damage") -> AppIcons.Bolt
    a.id.startsWith("first_win") || a.id.startsWith("weekly") -> AppIcons.Trophy
    a.id.startsWith("versus") || a.id.startsWith("raid") -> AppIcons.People
    a.id.startsWith("clinic") -> AppIcons.Heart
    a.id.startsWith("five_families") -> AppIcons.Grid
    a.id.startsWith("level") || a.id.startsWith("pb") -> AppIcons.Star
    else -> AppIcons.Bolt
}

@Suppress("unused")
private fun tierColor(t: Tier) = t.color()
