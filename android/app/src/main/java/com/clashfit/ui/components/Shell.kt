package com.clashfit.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.PanelLift
import com.clashfit.ui.theme.Rule
import com.clashfit.ui.theme.RuleSoft

/*
 * The screen shell. Every destination that is not a full-screen experience (a fight, a run,
 * an alarm ringing) sits inside one of these: a top bar with the screen's name, a back arrow
 * when there is somewhere to go back to, and the content padded clear of it.
 */

/** The app's top bar. Ink on ground, the back glyph in ember. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClashTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Text(title, style = MaterialTheme.typography.titleLarge, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(AppIcons.Back, contentDescription = "Back", tint = Ember) }
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
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = { ClashTopBar(title, onBack, actions) },
        bottomBar = bottomBar,
        containerColor = Ground,
        contentColor = Ink,
        content = content,
    )
}

/** An icon in the top bar's action slot, with the 48dp target IconButton guarantees. */
@Composable
fun BarAction(icon: ImageVector, contentDescription: String, tint: Color = Ink, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, contentDescription = contentDescription, tint = tint) }
}

/** The one-pixel rule between things that are not in the same card. */
@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, thickness = 1.dp, color = Rule)
}

/** A settings row with a real switch. Lives inside a ListGroup. */
@Composable
fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, supporting: String? = null) {
    Row(
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }
            .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Ink)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = InkMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ground, checkedTrackColor = Ember,
                uncheckedThumbColor = InkMuted, uncheckedTrackColor = PanelLift, uncheckedBorderColor = Rule,
            ),
        )
    }
}

/** A navigation row: optional icon bubble, label, supporting line, value, chevron. Lives inside a ListGroup. */
@Composable
fun NavRow(
    label: String,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    value: String? = null,
    supporting: String? = null,
    tint: Color = Ember,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) IconBubble(icon, tint = tint)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = Ink)
            if (supporting != null) Text(supporting, style = MaterialTheme.typography.bodySmall, color = InkMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (value != null) Text(value, style = MaterialTheme.typography.bodyMedium, color = InkMuted)
        Icon(AppIcons.Chevron, contentDescription = null, tint = InkFaint, modifier = Modifier.size(18.dp))
    }
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
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icon != null) IconBubble(icon, size = 64)
        Text(title, style = MaterialTheme.typography.titleLarge, color = Ink, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = InkMuted, textAlign = TextAlign.Center)
        if (action != null) Box(Modifier.padding(top = 12.dp)) { action() }
    }
}

/**
 * An empty state that owns the whole screen, rather than sitting at the top of one.
 *
 * [EmptyState] is written to sit inside a column of other content. When it IS the content — a
 * character sheet before the first fight, a history with no sessions — hugging the top leaves a
 * screen that is one paragraph and then a metre of nothing, which reads as a page that failed to
 * load rather than a page waiting for you.
 */
@Composable
fun ScreenEmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(bottom = 48.dp)) {
            EmptyState(title, body, icon = icon, action = action)
        }
    }
}

@Composable
fun LoadingState(message: String = "Loading", modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator(color = Ember, trackColor = RuleSoft, strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
        Text(message, style = MaterialTheme.typography.labelLarge, color = InkMuted, modifier = Modifier.padding(top = 16.dp))
    }
}
