package com.clashfit.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clashfit.AppGraph
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.LinkButton
import com.clashfit.ui.components.PrimaryButton
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Gassed
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule
import com.clashfit.ui.theme.Success

/* Create an account, sign in, and reset a password. One view model, three screens. */

@Composable
private fun authViewModel(graph: AppGraph): AuthViewModel =
    viewModel(key = "auth", factory = AuthViewModel.factory(graph.auth))

@Composable
fun SignUpScreen(graph: AppGraph, onBack: () -> Unit, onSignIn: () -> Unit, onDone: () -> Unit) {
    val vm = authViewModel(graph)
    val f by vm.form.collectAsStateWithLifecycle()
    ScreenScaffold(title = "Create account", onBack = onBack) { padding ->
        AuthColumn(padding) {
            Text("You, on the board.", style = MaterialTheme.typography.headlineMedium, color = Ink)
            Text(
                "Your name is what friends and the leaderboard see. Your camera never goes anywhere; only scores do.",
                style = MaterialTheme.typography.bodyMedium, color = InkMuted, modifier = Modifier.padding(top = 6.dp, bottom = 22.dp),
            )
            AuthField("Display name", f.name, vm::onName, error = f.nameError, keyboard = KeyboardType.Text)
            AuthField("Email", f.email, vm::onEmail, error = f.emailError, keyboard = KeyboardType.Email)
            AuthField("Password", f.password, vm::onPassword, error = f.passwordError, keyboard = KeyboardType.Password, secret = true, help = "At least 8 characters")
            AuthField("Confirm password", f.confirm, vm::onConfirm, error = f.confirmError, keyboard = KeyboardType.Password, secret = true)
            Banner(f.banner)
            Spacer(Modifier.height(8.dp))
            SubmitButton("Create account", f.busy) { vm.signUp(onDone) }
            LocalNote(vm.isCloud)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("Already have an account?", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                LinkButton("Sign in", onClick = onSignIn)
            }
        }
    }
}

@Composable
fun SignInScreen(graph: AppGraph, onBack: () -> Unit, onSignUp: () -> Unit, onForgot: () -> Unit, onDone: () -> Unit) {
    val vm = authViewModel(graph)
    val f by vm.form.collectAsStateWithLifecycle()
    ScreenScaffold(title = "Sign in", onBack = onBack) { padding ->
        AuthColumn(padding) {
            Text("Welcome back.", style = MaterialTheme.typography.headlineMedium, color = Ink)
            Text("Your streak, level and badges are where you left them.", style = MaterialTheme.typography.bodyMedium, color = InkMuted, modifier = Modifier.padding(top = 6.dp, bottom = 22.dp))
            AuthField("Email", f.email, vm::onEmail, error = f.emailError, keyboard = KeyboardType.Email)
            AuthField("Password", f.password, vm::onPassword, error = f.passwordError, keyboard = KeyboardType.Password, secret = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { LinkButton("Forgot password?", onClick = onForgot) }
            Banner(f.banner)
            SubmitButton("Sign in", f.busy) { vm.signIn(onDone) }
            LocalNote(vm.isCloud)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("New here?", style = MaterialTheme.typography.bodyMedium, color = InkMuted)
                LinkButton("Create an account", onClick = onSignUp)
            }
        }
    }
}

@Composable
fun ResetPasswordScreen(graph: AppGraph, onBack: () -> Unit) {
    val vm = authViewModel(graph)
    val f by vm.form.collectAsStateWithLifecycle()
    ScreenScaffold(title = "Reset password", onBack = onBack) { padding ->
        AuthColumn(padding) {
            Text("We will email you a link.", style = MaterialTheme.typography.headlineMedium, color = Ink)
            Text("Open it on this phone and choose a new password.", style = MaterialTheme.typography.bodyMedium, color = InkMuted, modifier = Modifier.padding(top = 6.dp, bottom = 22.dp))
            AuthField("Email", f.email, vm::onEmail, error = f.emailError, keyboard = KeyboardType.Email)
            Banner(f.banner)
            if (f.resetSent) {
                AppCard(Modifier.fillMaxWidth(), container = Success.copy(alpha = 0.14f), padding = 14) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(AppIcons.Check, contentDescription = null, tint = Success, modifier = Modifier.size(20.dp))
                        Text("Sent. Check your inbox, and the spam folder once.", style = MaterialTheme.typography.bodyMedium, color = Ink)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            SubmitButton(if (f.resetSent) "Send again" else "Send reset link", f.busy) { vm.sendReset() }
        }
    }
}

// ── pieces ────────────────────────────────────────────────────────────────────────────────

@Composable
private fun AuthColumn(padding: androidx.compose.foundation.layout.PaddingValues, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState()).imePadding()
            .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 28.dp),
    ) { content() }
}

@Composable
private fun AuthField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    error: String?,
    keyboard: KeyboardType,
    secret: Boolean = false,
    help: String? = null,
) {
    var show by rememberSaveable { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = {
            when {
                error != null -> Text(error, color = Gassed)
                help != null -> Text(help, color = InkFaint)
            }
        },
        visualTransformation = if (secret && !show) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        trailingIcon = if (secret) {
            {
                IconButton(onClick = { show = !show }) {
                    Icon(AppIcons.Eye, contentDescription = if (show) "Hide password" else "Show password", tint = if (show) Ember else InkFaint)
                }
            }
        } else null,
        shape = MaterialTheme.shapes.small,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Panel, unfocusedContainerColor = Panel, errorContainerColor = Panel,
            focusedBorderColor = Ember, unfocusedBorderColor = Rule, errorBorderColor = Gassed,
            focusedLabelColor = Ember, unfocusedLabelColor = InkMuted, errorLabelColor = Gassed,
            focusedTextColor = Ink, unfocusedTextColor = Ink, errorTextColor = Ink, cursorColor = Ember,
        ),
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
    )
}

@Composable
private fun Banner(text: String?) {
    if (text == null) return
    AppCard(Modifier.fillMaxWidth().padding(vertical = 6.dp), container = Gassed.copy(alpha = 0.14f), padding = 14) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Ink)
    }
}

@Composable
private fun SubmitButton(text: String, busy: Boolean, onClick: () -> Unit) {
    if (busy) {
        androidx.compose.material3.Button(
            onClick = {}, enabled = false,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(disabledContainerColor = Ember.copy(alpha = 0.6f), disabledContentColor = Ground),
        ) { CircularProgressIndicator(color = Ground, strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp)) }
    } else {
        PrimaryButton(text, onClick = onClick)
    }
}

@Composable
private fun LocalNote(isCloud: Boolean) {
    if (!isCloud) {
        Text(
            "Your account stays on this phone. Nothing is uploaded, and everything works with no network at all.",
            style = MaterialTheme.typography.bodySmall, color = InkFaint, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    } else {
        Spacer(Modifier.height(8.dp))
    }
}
