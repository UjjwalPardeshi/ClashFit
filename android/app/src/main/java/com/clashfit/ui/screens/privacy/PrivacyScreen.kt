package com.clashfit.ui.screens.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import com.clashfit.AppGraph
import com.clashfit.ui.components.Headline
import com.clashfit.ui.components.Kicker
import com.clashfit.ui.components.RuleRow
import com.clashfit.ui.components.SectionGap
import com.clashfit.ui.theme.Ink
import com.clashfit.ui.theme.InkFaint
import com.clashfit.ui.theme.Panel
import com.clashfit.ui.theme.Rule

/** No-cloud diagram text, list of permissions requested and why, INTERNET verification. */
@Composable
fun PrivacyScreen(graph: AppGraph, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Headline("PRIVACY")
        SectionGap(24)

        // No-cloud principles
        Kicker("On-Device Only")
        SectionGap(12)
        PrivacyPrinciple("Offline", "No internet required. The app works in airplane mode.")
        PrivacyPrinciple("On-Device", "Every rep is analysed on your phone, nowhere else.")
        PrivacyPrinciple("No Account", "Sign up? Never. Your data stays yours.")
        PrivacyPrinciple("No Upload", "No video, no images, no landmarks leave your device.")
        PrivacyPrinciple("No Tracking", "Zero telemetry. Zero analytics. Zero ads.")

        SectionGap(28)

        // Permissions requested
        Kicker("Permissions")
        SectionGap(12)
        PermissionRow("Camera", "Measure your movement in real-time. The referee's eye.")
        PermissionRow("Microphone", "Hear the rep impact sound even with the screen off.")
        PermissionRow("Notification", "Wake-up alarms and activity reminders.")
        PermissionRow("Location", "Run tracking only — maps stay on the phone.")

        SectionGap(12)
        Box(
            Modifier
                .fillMaxWidth()
                .background(Panel)
                .border(1.dp, Rule)
                .padding(12.dp)
        ) {
            Text(
                "Internet: NOT REQUESTED · This package declares zero network permissions.",
                style = MaterialTheme.typography.bodySmall,
                color = InkFaint
            )
        }

        SectionGap(28)

        // Radio-off claim
        Kicker("Radio Off")
        SectionGap(12)
        Text(
            "Everything that watches your body runs with the radio off. Outbreak is the one mode that goes online, and it says so before it starts. 0 frames uploaded — still literally true, and the stronger claim anyway: no video, no landmarks, no biometrics ever leave the device, in any mode, including this one.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        SectionGap(28)

        // Data retention
        Kicker("What We Keep")
        SectionGap(12)
        Text(
            "Per-rep scalars: depth, range, tempo, alignment, damage. Fatigue state, form scores, boss health. Session metadata.",
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            "What we never keep: video, images, landmarks, audio recordings, phone identifiers.",
            style = MaterialTheme.typography.bodyMedium,
            color = InkFaint,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        SectionGap(20)
    }
}

@Composable
private fun PrivacyPrinciple(title: String, description: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .background(Panel)
            .border(1.dp, Rule)
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = Ink)
            Text(description, style = MaterialTheme.typography.bodySmall, color = InkFaint)
        }
    }
}

@Composable
private fun PermissionRow(permission: String, reason: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(permission, style = MaterialTheme.typography.labelMedium, color = Ink)
        Text(reason, style = MaterialTheme.typography.bodySmall, color = InkFaint, modifier = Modifier.padding(top = 4.dp))
    }
}

fun NavGraphBuilder.privacyRoutes(graph: AppGraph, nav: NavHostController) {
    composable<com.clashfit.ui.nav.Privacy> {
        PrivacyScreen(graph)
    }
}
