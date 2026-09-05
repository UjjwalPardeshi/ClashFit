package com.clashfit.ui.screens.social

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clashfit.AppGraph
import com.clashfit.auth.AuthState
import com.clashfit.cloud.Board
import com.clashfit.cloud.CloudAvailability
import com.clashfit.cloud.LeaderboardEntry
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.Avatar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import com.clashfit.ui.components.LoadingState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.clashfit.ui.components.EmptyState
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.theme.AvatarPalette
import com.clashfit.ui.theme.Bronze
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.EmberTint
import com.clashfit.ui.theme.Gold
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.PanelLift
import com.clashfit.ui.theme.Rule
import com.clashfit.ui.theme.Silver
import com.clashfit.ui.components.RankInsignia

/** Global and friends boards for the week and all time. Honest when the cloud is not there. */
@Composable
fun LeaderboardScreen(graph: AppGraph, onBack: () -> Unit, onSignIn: () -> Unit, onFriends: () -> Unit) {
    var board by rememberSaveable { mutableStateOf(Board.WEEKLY_DAMAGE) }
    var friendsOnly by rememberSaveable { mutableStateOf(false) }
    val auth by graph.auth.state.collectAsStateWithLifecycle()
    val availabilityFlow = remember(graph) { graph.leaderboard.availability }
    val availability by availabilityFlow.collectAsStateWithLifecycle(initialValue = CloudAvailability.Unavailable("Connecting…"))
    // A board that has not answered yet is not an empty board. Starting at emptyList() meant the
    // screen said "Nobody on this board yet" for as long as the round trip took, which is a
    // sentence that is both wrong and discouraging. Null is "still asking".
    //
    // The key includes [refresh]: the Firestore listener is live, so there is nothing to re-fetch
    // — pulling tears the subscription down and opens a new one, which is the thing that actually
    // recovers a listener that died with the network.
    var refresh by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val entriesFlow = remember(graph, board, friendsOnly, refresh) {
        if (friendsOnly) graph.leaderboard.friends(board) else graph.leaderboard.top(board)
    }
    val entries by entriesFlow.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    ScreenScaffold(title = "Leaderboard", onBack = onBack) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                refresh++
                scope.launch { delay(700); refreshing = false }
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
        ) {
            item(key = "scope") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false to "Everyone", true to "Friends").forEachIndexed { i, (v, label) ->
                        SegmentedButton(
                            selected = friendsOnly == v,
                            onClick = { friendsOnly = v },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = 2),
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = Ember, activeContentColor = Ground,
                                inactiveContainerColor = Panel, inactiveContentColor = InkMuted, inactiveBorderColor = Rule, activeBorderColor = Ember,
                            ),
                        ) { Text(label, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            item(key = "boards") {
                LazyRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(Board.entries, key = { it.name }) { b ->
                        FilterChip(
                            selected = b == board, onClick = { board = b },
                            label = { Text(b.title, style = MaterialTheme.typography.labelLarge) },
                            shape = CircleShape,
                            colors = FilterChipDefaults.filterChipColors(containerColor = Panel, labelColor = InkMuted, selectedContainerColor = EmberTint, selectedLabelColor = Ember),
                            border = FilterChipDefaults.filterChipBorder(enabled = true, selected = b == board, borderColor = Rule, selectedBorderColor = Ember),
                        )
                    }
                }
            }

            val unavailable = availability as? CloudAvailability.Unavailable
            if (unavailable != null) {
                item(key = "unavailable") {
                    AppCard(Modifier.fillMaxWidth().padding(top = 16.dp), container = PanelLift) {
                        Column {
                            Text("The board is not reachable", style = MaterialTheme.typography.titleMedium, color = Ink)
                            Text(unavailable.reason, style = MaterialTheme.typography.bodySmall, color = InkMuted, modifier = Modifier.padding(top = 4.dp))
                            if (auth is AuthState.SignedOut) {
                                PrimaryButton("Sign in", Modifier.padding(top = 14.dp), onClick = onSignIn)
                            }
                        }
                    }
                }
            } else if (entries == null) {
                item(key = "loading") { LoadingState("Reading the board", Modifier.padding(top = 60.dp)) }
            } else if (entries!!.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        if (friendsOnly) "No friends on the board yet" else "Nobody on this board yet",
                        if (friendsOnly) "Add a friend with their code and race them here." else "Finish one set and you are on it.",
                        icon = AppIcons.Trophy,
                        action = if (friendsOnly) ({ PrimaryButton("Add friends", onClick = onFriends) }) else null,
                    )
                }
            } else {
                item(key = "list") {
                    ListGroup(Modifier.padding(top = 16.dp)) {
                        entries!!.forEachIndexed { i, e ->
                            EntryRow(e, board.unit)
                            if (i < entries!!.lastIndex) InnerDivider()
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun EntryRow(e: LeaderboardEntry, unit: String) {
    val medal: Color? = when (e.rank) { 1 -> Gold; 2 -> Silver; 3 -> Bronze; else -> null }
    Row(
        Modifier.fillMaxWidth().background(if (e.isMe) EmberTint else Color.Transparent).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(medal ?: Panel), contentAlignment = Alignment.Center) {
            Text("${e.rank}", style = MaterialTheme.typography.labelLarge, color = if (medal != null) Ground else InkMuted)
        }
        Avatar(e.displayName, size = 36, color = AvatarPalette.at(e.displayName.hashCode()))
        Column(Modifier.weight(1f)) {
            Text(if (e.isMe) "${e.displayName} (you)" else e.displayName, style = MaterialTheme.typography.bodyLarge, color = Ink)
            Text("Level ${e.level}", style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
        // Their rank, worn. A board of names and numbers tells you who is ahead; a board of
        // emblems tells you what they are, which is the part worth catching up to.
        RankInsignia(e.level, size = 32)
        Text("${e.value} $unit", style = MaterialTheme.typography.titleSmall, color = if (e.isMe) Ember else Ink)
    }
}
