package com.clashfit.ui.screens.social

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clashfit.AppGraph
import com.clashfit.auth.AuthRules
import com.clashfit.auth.AuthState
import com.clashfit.data.Prefs
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.Avatar
import com.clashfit.ui.components.InnerDivider
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.ListGroup
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.components.RuleRow
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.SecondaryButton
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.theme.AvatarPalette
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Gassed
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule
import com.clashfit.ui.theme.Success
import kotlinx.coroutines.launch

/** Who you are signed in as, the name others see, sign out, and the one irreversible button. */
@Composable
fun AccountScreen(graph: AppGraph, onBack: () -> Unit, onSignedOut: () -> Unit) {
    val auth by graph.auth.state.collectAsStateWithLifecycle()
    val settings by graph.prefs.settings.collectAsStateWithLifecycle(initialValue = Prefs.Settings())
    val scope = rememberCoroutineScope()
    val user = (auth as? AuthState.SignedIn)?.user

    var name by rememberSaveable(user?.displayName) { mutableStateOf(user?.displayName ?: "") }
    var saving by remember { mutableStateOf(false) }
    var saved by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    ScreenScaffold(title = "Account", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 28.dp),
        ) {
            AppCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Avatar(user?.shownName() ?: "?", size = 64, color = AvatarPalette.at(settings.avatarColor))
                    Column {
                        Text(user?.shownName() ?: "Not signed in", style = MaterialTheme.typography.titleLarge, color = Ink)
                        Text(user?.email ?: "", style = MaterialTheme.typography.bodySmall, color = InkMuted)
                        Text(
                            if (graph.auth.isCloud) "Synced to the cloud" else "Stored on this phone",
                            style = MaterialTheme.typography.labelSmall, color = if (graph.auth.isCloud) Success else InkMuted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }

            SectionGap(24)
            SectionTitle("Display name")
            Text("What friends and the leaderboard see.", style = MaterialTheme.typography.bodySmall, color = InkMuted)
            SectionGap(10)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = null; saved = false },
                singleLine = true,
                isError = nameError != null,
                supportingText = { nameError?.let { Text(it, color = Gassed) } ?: if (saved) Text("Saved", color = Success) else Unit },
                shape = MaterialTheme.shapes.small,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Panel, unfocusedContainerColor = Panel, errorContainerColor = Panel,
                    focusedBorderColor = Ember, unfocusedBorderColor = Rule, errorBorderColor = Gassed,
                    focusedTextColor = Ink, unfocusedTextColor = Ink, cursorColor = Ember,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            SecondaryButton(if (saving) "Saving…" else "Save name", enabled = !saving && name.trim() != user?.displayName) {
                if (!AuthRules.nameOk(name)) { nameError = "Two to twenty-four characters."; return@SecondaryButton }
                saving = true
                scope.launch {
                    val err = graph.auth.updateDisplayName(name.trim())
                    saving = false
                    if (err == null) saved = true else nameError = err.message
                }
            }

            SectionGap(28)
            SectionTitle("Details")
            SectionGap(10)
            ListGroup {
                RuleRow("Email", user?.email ?: "—")
                InnerDivider()
                RuleRow("Goal", Prefs.Goal.entries.firstOrNull { it.name == settings.goal }?.title ?: "—")
                InnerDivider()
                RuleRow("Account", if (graph.auth.isCloud) "Firebase" else "This phone only")
            }

            SectionGap(28)
            PrimaryButton("Sign out") {
                scope.launch { graph.auth.signOut(); onSignedOut() }
            }
            Spacer(Modifier.height(28.dp))
            Kicker("Danger zone", color = Gassed)
            Text(
                "Deleting the account removes your name from every board and cannot be undone. Sessions on this phone stay until you clear data.",
                style = MaterialTheme.typography.bodySmall, color = InkMuted, modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
            )
            SecondaryButton("Delete account") { confirmDelete = true }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Panel, titleContentColor = Ink, textContentColor = InkMuted,
            title = { Text("Delete your account?", style = MaterialTheme.typography.titleLarge) },
            text = { Text("Your name leaves every leaderboard and your friends lose you. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { if (graph.auth.deleteAccount() == null) onSignedOut() }
                }) { Text("Delete", color = Gassed) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", color = Ember) } },
        )
    }
}
