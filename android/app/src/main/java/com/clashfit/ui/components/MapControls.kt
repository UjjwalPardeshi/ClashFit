package com.clashfit.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.clashfit.map.RouteMapState
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule

/**
 * Zoom and recentre, floating over a map.
 *
 * These exist because osmdroid's built-in zoom widget looks like Android 4 and because pinching is
 * not the only way people expect to zoom a map — a thumb on the same hand that is holding the
 * phone mid-run cannot pinch at all. The recentre button only appears once the player has actually
 * moved the map, so a map nobody has touched carries no clutter.
 */
@Composable
fun MapControls(state: RouteMapState, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AnimatedVisibility(
            visible = state.canRecentre,
            enter = fadeIn() + scaleIn(initialScale = 0.7f),
            exit = fadeOut() + scaleOut(targetScale = 0.7f),
        ) {
            MapButton(AppIcons.Recentre, "Recentre the map", accent = true) { state.recentre() }
        }
        Column(
            Modifier.clip(RoundedCornerShape(14.dp)).background(Ground.copy(alpha = 0.82f))
                .border(1.dp, Rule, RoundedCornerShape(14.dp)),
        ) {
            MapButton(AppIcons.Plus, "Zoom in", framed = false) { state.zoomIn() }
            MapButton(AppIcons.Minus, "Zoom out", framed = false) { state.zoomOut() }
        }
    }
}

@Composable
private fun MapButton(
    icon: ImageVector,
    description: String,
    accent: Boolean = false,
    framed: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(44.dp)
            .then(
                if (framed) {
                    Modifier.clip(CircleShape).background(Ground.copy(alpha = 0.82f)).border(1.dp, Rule, CircleShape)
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (accent) Ember else Ink,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The one-time notice about where the streets come from.
 *
 * It lives here rather than on one screen because it has to appear wherever a map can first be
 * drawn. It used to sit only on the Outdoors tab, which meant a player who reached Zombie Run from
 * the Modes screen never saw it — and, since every map waits on this answer, never got a map
 * either. The chase would have run on the plain grid forever with no way to say yes.
 *
 * Not dismissible by tapping outside. Both answers are one tap and both are reversible in
 * Settings; a dialog you can dismiss without answering would leave the maps waiting on a question
 * nobody asked again.
 */
@Composable
fun StreetMapNotice(onShowStreets: () -> Unit, onKeepOffline: () -> Unit) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text("Your route is drawn on real streets", color = Ink) },
        text = {
            Text(
                "The map comes from OpenStreetMap and is downloaded as you look at it, so asking " +
                    "for the right tiles tells their servers roughly where you were. Your route " +
                    "itself never leaves this phone. Turn streets off and it draws on our own grid " +
                    "instead, with the radio off. Settings can change this at any time.",
                style = MaterialTheme.typography.bodyMedium,
                color = InkMuted,
            )
        },
        confirmButton = {
            TextButton(onClick = onShowStreets) { Text("Show streets", color = Ember) }
        },
        dismissButton = {
            TextButton(onClick = onKeepOffline) { Text("Keep it offline", color = InkMuted) }
        },
        containerColor = Panel,
    )
}

/** A small caption that sits over a map, for the attribution a tile provider is owed. */
@Composable
fun MapCaption(text: String, modifier: Modifier = Modifier, colour: Color = Ink.copy(alpha = 0.55f)) {
    Text(
        text,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Ground.copy(alpha = 0.55f)),
        style = MaterialTheme.typography.labelSmall,
        color = colour,
    )
}
