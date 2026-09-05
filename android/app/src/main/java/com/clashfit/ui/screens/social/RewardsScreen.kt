package com.clashfit.ui.screens.social

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clashfit.AppGraph
import com.clashfit.data.VoucherEntity
import com.clashfit.meta.Voucher
import com.clashfit.meta.VoucherCatalog
import com.clashfit.meta.VoucherKind
import com.clashfit.ui.components.AppCard
import com.clashfit.ui.components.AppIcons
import com.clashfit.ui.components.SecondaryButton
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.components.SectionTitle
import com.clashfit.ui.components.ScreenScaffold
import com.clashfit.ui.components.Tag
import com.clashfit.ui.theme.Brass
import com.clashfit.ui.theme.Ember
import com.clashfit.ui.theme.Fresh
import com.clashfit.ui.theme.Ground
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.InkMuted
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.PanelLift
import com.clashfit.ui.theme.Rival
import com.clashfit.ui.theme.RuleSoft
import com.clashfit.ui.theme.Success
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The colour a partner category is drawn in, so the shelf reads as a shelf rather than a list. */
private fun VoucherKind.tint(): Color = when (this) {
    VoucherKind.NUTRITION -> Success
    VoucherKind.GEAR -> Ember
    VoucherKind.GYM -> Rival
    VoucherKind.RECOVERY -> Fresh
    VoucherKind.CLINIC -> Brass
}

/**
 * What training has earned, from someone other than the app.
 *
 * XP and badges are the game paying you in its own currency, which costs nothing and everybody
 * knows it. A voucher is the argument that measured effort is worth something to a third party —
 * the thing that turns "a fitness game" into "a fitness game a brand would fund". That is the whole
 * reason this screen exists, and it is why the vouchers are rarer than the badges.
 *
 * A locked card shows the brand and what earns it but never the code, so the shelf reads as a
 * shelf: things you could have, and the two of them you do.
 */
@Composable
fun RewardsScreen(graph: AppGraph, onBack: () -> Unit) {
    val earned by graph.db.vouchers().all().collectAsStateWithLifecycle(initialValue = emptyList())
    val byId = earned.associateBy { it.id }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    ScreenScaffold(title = "Rewards", onBack = onBack) { padding ->
        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).padding(top = 4.dp, bottom = 28.dp),
        ) {
            Text(
                "Partners put these up for measured training. They are not bought, and they are not " +
                    "given for opening the app — every one is earned by something the camera counted.",
                style = MaterialTheme.typography.bodyMedium, color = InkMuted,
            )
            val have = VoucherCatalog.all.filter { it.id in byId }
            val want = VoucherCatalog.all.filterNot { it.id in byId }

            if (have.isNotEmpty()) {
                SectionGap(26)
                SectionTitle("Earned", action = null)
                SectionGap(10)
                have.forEach { v ->
                    VoucherCard(
                        voucher = v,
                        row = byId[v.id],
                        onCopy = { copyCode(context, v) },
                        onToggleUsed = { used ->
                            scope.launch {
                                if (used) graph.db.vouchers().markRedeemed(v.id, graph.clock.nowMs())
                                else graph.db.vouchers().markUnredeemed(v.id)
                            }
                        },
                    )
                    SectionGap(10)
                }
            }

            SectionGap(if (have.isEmpty()) 26 else 16)
            SectionTitle(if (have.isEmpty()) "How to earn one" else "Still to earn", action = null)
            SectionGap(10)
            want.forEach { v ->
                VoucherCard(voucher = v, row = null, onCopy = {}, onToggleUsed = {})
                SectionGap(10)
            }
        }
    }
}

/**
 * One voucher, earned or not.
 *
 * A locked one shows the brand and what earns it but not the code, because the code is the reward.
 * An earned one shows everything, and can be marked used — nothing enforces that, it is the
 * player's own record of what they have spent.
 */
@Composable
private fun VoucherCard(
    voucher: Voucher,
    row: VoucherEntity?,
    onCopy: () -> Unit,
    onToggleUsed: (Boolean) -> Unit,
) {
    val locked = row == null
    val used = row?.redeemedAtMs != null
    val tint = if (locked) InkFaint else voucher.kind.tint()

    AppCard(Modifier.fillMaxWidth(), padding = 0, container = if (locked) Panel else PanelLift) {
        Column {
            // The stub: brand and offer, on a wash of the partner's colour.
            Column(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(tint.copy(alpha = if (locked) 0.06f else 0.18f), Color.Transparent)))
                    .padding(16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                    Column(Modifier.padding(end = 12.dp)) {
                        Text(
                            voucher.brand.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = tint,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            voucher.offer,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (locked) InkMuted else Ink,
                        )
                    }
                    if (locked) {
                        Icon(AppIcons.Shield, contentDescription = "Not earned yet", tint = InkFaint, modifier = Modifier.size(20.dp))
                    } else if (used) {
                        Tag("Used", color = InkMuted)
                    } else {
                        Icon(AppIcons.Check, contentDescription = "Earned", tint = tint, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // The perforation, which is the one bit of skeuomorphism this app allows itself: it is
            // what makes a card read as a voucher rather than as another list row.
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).size(width = 0.dp, height = 1.dp).background(RuleSoft))

            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (locked) "Earn it: ${voucher.milestone}" else voucher.milestone,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkMuted,
                )
                if (!locked) {
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                            .background(Ground).border(1.dp, tint.copy(alpha = 0.30f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Text(
                            voucher.code,
                            style = MaterialTheme.typography.titleSmall,
                            color = if (used) InkFaint else Ink,
                        )
                    }
                    var justCopied by remember { mutableStateOf(false) }
                    LaunchedEffect(justCopied) {
                        if (justCopied) { delay(1600); justCopied = false }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(
                            if (justCopied) "Copied" else "Copy code",
                            Modifier.weight(1f),
                        ) { onCopy(); justCopied = true }
                        SecondaryButton(
                            if (used) "Mark unused" else "Mark used",
                            Modifier.weight(1f),
                            onClick = { onToggleUsed(!used) },
                        )
                    }
                }
            }
        }
    }
}

private fun copyCode(context: Context, v: Voucher) {
    val clip = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clip.setPrimaryClip(ClipData.newPlainText("${v.brand} reward code", v.code))
}
