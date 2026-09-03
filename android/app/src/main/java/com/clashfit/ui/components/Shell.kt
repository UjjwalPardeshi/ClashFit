package com.clashfit.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Rule
import com.clashfit.ui.theme.RuleSoft

/*
 * The screen shell. Every destination that is not a full-screen experience (a fight, a run,
 * an alarm ringing) sits inside one of these: a Material top bar with the screen's name, a
 * back affordance when there is somewhere to go back to, and the content padded clear of it.
 *
 * Kept separate from Kit.kt on purpose. Kit is the visual language; this is structure.
 */

/** The app's top bar: the display face, ink on ground, an ember back glyph. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClashTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                color = Ink,
                maxLines = 1,
            )
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(AppIcons.Back, contentDescription = "Back", tint = Ember)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Ground,
            scrolledContainerColor = Ground,
            titleContentColor = Ink,
            navigationIconContentColor = Ember,
            actionIconContentColor = Ink,
        ),
    )
}

/**
 * A titled screen. The content lambda receives the padding the bar occupies; apply it to the
 * scrolling container, not to every child.
 */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = { ClashTopBar(title, onBack, actions) },
        containerColor = Ground,
        contentColor = Ink,
        content = content,
    )
}

/** The one-pixel rule the whole app draws lists with. */
@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = Rule)
}

/** A settings row with a real switch. The switch alone guarantees the 48dp target. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, supporting: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(end = 16.dp)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, color = Ink)
                if (supporting != null) {
                    Text(supporting, style = MaterialTheme.typography.bodySmall, color = InkMuted)
                }
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Ground,
                    checkedTrackColor = Ember,
                    uncheckedThumbColor = InkMuted,
                    uncheckedTrackColor = RuleSoft,
                    uncheckedBorderColor = Rule,
                ),
            )
        }
        AppDivider()
    }
}

/** A navigation row: icon, label, optional value, chevron. 56dp tall. */
@Composable
fun NavRow(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    value: String? = null,
    supporting: String? = null,
) {
    RuleRow(
        label = label,
        onClick = onClick,
        trailing = {
            if (value != null) {
                Text(value, style = MaterialTheme.typography.labelSmall, color = InkMuted, modifier = Modifier.padding(end = 10.dp))
            }
            Icon(AppIcons.Chevron, contentDescription = null, tint = InkMuted, modifier = Modifier.size(18.dp))
        },
    )
}

/** Centred, with an optional glyph above and an action below. Never a bare spinner. */
@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) {
            Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Ember, modifier = Modifier.size(32.dp))
            }
        }
        Text(title.uppercase(), style = MaterialTheme.typography.headlineMedium, color = Ink, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = InkMuted, textAlign = TextAlign.Center)
        if (action != null) {
            Box(Modifier.padding(top = 14.dp)) { action() }
        }
    }
}

@Composable
fun LoadingState(message: String = "Loading", modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = Ember, trackColor = RuleSoft, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        Text(
            message.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = InkMuted,
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
