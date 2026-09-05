package com.clashfit.ui.screens.social

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clashfit.AppGraph
import com.clashfit.cloud.Friend
import com.clashfit.cloud.FriendCodes
import com.clashfit.cloud.FriendResult
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.Avatar
import com.clashfit.ui.components.EmptyState
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.LinkButton
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SecondaryButton
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.theme.AvatarPalette
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Gassed
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.MonoReadout
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule
import com.clashfit.ui.theme.Success
import kotlinx.coroutines.launch

/** Your code, a field for theirs, and the list. Friends are mutual: adding once adds both ways. */
@Composable
fun FriendsScreen(graph: AppGraph, onBack: () -> Unit) {
    val friendsFlow = remember(graph) { graph.friends.friends }
    val codeFlow = remember(graph) { graph.friends.myCode }
    val friends by friendsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val myCode by codeFlow.collectAsStateWithLifecycle(initialValue = null)
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) } // text to isSuccess

    ScreenScaffold(title = "Friends", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 28.dp),
        ) {
            AppCard(Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("Your friend code", style = MaterialTheme.typography.titleSmall, color = InkMuted)
                    Text(
                        myCode?.chunked(3)?.joinToString(" ") ?: "— — —",
                        style = MaterialTheme.typography.displaySmall, color = Ink, modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Text("Anyone with this code can add you. It is not secret, just yours.", style = MaterialTheme.typography.bodySmall, color = InkFaint)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("Copy", Modifier.weight(1f), enabled = myCode != null) {
                            myCode?.let { clipboard.setText(AnnotatedString(it)) }
                        }
                        SecondaryButton("Share", Modifier.weight(1f), enabled = myCode != null) {
                            val text = "Add me on ClashFit. My friend code is ${myCode ?: ""}."
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Share your code"))
                        }
                    }
                }
            }

            SectionGap(24)
            SectionTitle("Add a friend")
            SectionGap(10)
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().take(8); message = null },
                label = { Text("Their code") },
                placeholder = { Text("ABC 123", color = InkFaint) },
                singleLine = true,
                textStyle = MonoReadout.copy(color = Ink, fontSize = MaterialTheme.typography.titleMedium.fontSize),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, capitalization = KeyboardCapitalization.Characters),
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Panel, unfocusedContainerColor = Panel,
                    focusedBorderColor = Ember, unfocusedBorderColor = Rule,
                    focusedLabelColor = Ember, unfocusedLabelColor = InkMuted, cursorColor = Ember,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            message?.let { (text, ok) ->
                Text(text, style = MaterialTheme.typography.bodySmall, color = if (ok) Success else Gassed, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(12.dp))
            PrimaryButton(if (busy) "Adding…" else "Add friend", enabled = !busy && FriendCodes.isValid(code)) {
                busy = true
                scope.launch {
                    message = when (val r = graph.friends.addByCode(code)) {
                        is FriendResult.Added -> { code = ""; "${r.friend.displayName} added." to true }
                        is FriendResult.Failed -> r.message to false
                    }
                    busy = false
                }
            }

            SectionGap(28)
            SectionTitle("Your friends", action = if (friends.isNotEmpty()) "${friends.size}" else null, onAction = null)
            SectionGap(10)
            if (friends.isEmpty()) {
                EmptyState("Nobody yet", "Share your code, or type a friend's. The friends board fills up from here.", icon = AppIcons.People)
            } else {
                ListGroup {
                    friends.forEachIndexed { i, f ->
                        FriendRow(f) { scope.launch { graph.friends.remove(f.uid) } }
                        if (i < friends.lastIndex) InnerDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun FriendRow(f: Friend, onRemove: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Avatar(f.displayName, size = 40, color = AvatarPalette.at(f.displayName.hashCode()))
        Column(Modifier.weight(1f)) {
            Text(f.displayName, style = MaterialTheme.typography.bodyLarge, color = Ink)
            Text("Level ${f.level}", style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
        LinkButton("Remove", color = InkFaint, onClick = onRemove)
    }
}
