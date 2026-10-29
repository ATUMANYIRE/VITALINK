package org.vitalink.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.vitalink.app.Draft
import org.vitalink.app.UiState
import org.vitalink.app.ui.components.*
import org.vitalink.app.ui.theme.EditorialDisplay
import org.vitalink.app.ui.theme.VitalinkColors as C
import org.vitalink.domain.ReferralState
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

fun Draft.hasEntries() = listOf(patientId, systolic, diastolic, gestation).any { it.isNotBlank() } || symptoms.isNotEmpty()

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically) {
            Text("VITALINK", style = MaterialTheme.typography.titleLarge, letterSpacing = 2.sp, color = C.Forest)
            Text("FIELD CARE", style = MaterialTheme.typography.labelSmall, color = C.Muted, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(16.dp))
        BrandMark(Modifier.size(136.dp).align(Alignment.CenterHorizontally))
        Text("Maternal care,\nconnected.", Modifier.semantics { heading() }, style = EditorialDisplay)
        Text("Assess maternal health. Identify warning signs. Coordinate referrals—even offline.",
            style = MaterialTheme.typography.bodyLarge, color = C.Muted)
        PrimaryAction("Open workspace", onStart)
        HorizontalDivider(color = C.Border)
        Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
            WelcomeLine(AppIcons.Assessment, "Assessment at the point of care", "Record observations and reported warning signs.")
            WelcomeLine(AppIcons.Offline, "Keep working without internet", "Save encrypted records on this device.")
            WelcomeLine(AppIcons.Received, "Know where the handoff stands", "Track server receipt and clinician acknowledgment separately.")
        }
        ResearchFootnote()
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun WelcomeLine(icon: Int, title: String, detail: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        AppIcon(icon, tint = C.Forest)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = C.Muted)
        }
    }
}

@Composable
fun HomeScreen(state: UiState, onAssessment: () -> Unit, onNew: () -> Unit,
               onReferrals: () -> Unit, onRecords: () -> Unit, onSync: () -> Unit,
               onAssessmentDetail: (String) -> Unit, onReferralDetail: (String) -> Unit) {
    val greeting = when (LocalTime.now().hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Hello" }
    val practitioner = state.session?.username?.replaceFirstChar { it.titlecase() } ?: "practitioner"
    val pending = state.records.referrals.count { it.state == ReferralState.PENDING_SYNC || it.state == ReferralState.SENT }
    val awaiting = state.records.referrals.count { it.state == ReferralState.SERVER_RECEIVED }
    val enabled = !state.busy
    Text(LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
        style = MaterialTheme.typography.labelMedium, color = C.Muted)
    Text("$greeting,\n$practitioner.", Modifier.semantics { heading() },
        style = EditorialDisplay.copy(fontSize = 34.sp, lineHeight = 42.sp))
    Text("A clear assessment. A connected handoff.", style = MaterialTheme.typography.bodyLarge, color = C.Muted)
    PrimaryAction(if (state.draft.hasEntries()) "Resume assessment" else "Start assessment", onAssessment,
        enabled = enabled, icon = AppIcons.Add)
    if (state.draft.hasEntries()) {
        Text("An unfinished assessment is held in this app session. Save it to keep a device record.",
            style = MaterialTheme.typography.bodyMedium, color = C.Muted)
        TextButton(onNew, Modifier.heightIn(min = 48.dp), enabled = enabled) { Text("Start a different assessment") }
    }
    OutlinePanel {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(if (state.online) AppIcons.Sync else AppIcons.Offline, tint = C.Forest)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (state.online) "Network available" else "Working offline", style = MaterialTheme.typography.titleSmall)
                Text(if (pending > 0) "$pending referral${if (pending == 1) "" else "s"} awaiting delivery" else "Local assessment is available",
                    style = MaterialTheme.typography.bodyMedium, color = C.Muted)
            }
        }
        Text("Connectivity does not confirm delivery. Check each referral’s status after synchronization.",
            style = MaterialTheme.typography.bodyMedium, color = C.Muted)
        TextButton(onSync, Modifier.heightIn(min = 48.dp), enabled = enabled) {
            AppIcon(AppIcons.Sync); Spacer(Modifier.width(8.dp)); Text("Check referral delivery")
        }
    }
    if (state.storageError) {
        OutlinePanel {
            Text("Saved records are unavailable", style = MaterialTheme.typography.titleMedium)
            Text("Record counts and delivery states cannot be verified. You can still check observations with the on-device rules, but saving is unavailable until encrypted storage can be read.",
                style = MaterialTheme.typography.bodyMedium, color = C.Muted)
        }
    } else {
        SectionHeading("Your workspace")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            WorkCount(state.records.assessments.size, "Saved assessments", onRecords, Modifier.weight(1f))
            WorkCount(awaiting, "Awaiting acknowledgment", onReferrals, Modifier.weight(1f))
        }
        SectionHeading("Recent work", "All records", onRecords)
        val recent = state.records.assessments.sortedByDescending { it.createdAt }.take(3)
        if (recent.isEmpty()) {
            EmptyState("Your first assessment starts here", "Saved observations and referrals will appear here, including while offline.")
        } else {
            recent.forEach { saved ->
                val linked = state.records.referrals.firstOrNull { it.assessment.id == saved.assessment.id }
                AssessmentListRow(saved.assessment, linked?.state) {
                    if (linked != null) onReferralDetail(linked.id) else onAssessmentDetail(saved.assessment.id)
                }
            }
        }
    }
    ResearchFootnote()
}

@Composable
private fun WorkCount(value: Int, label: String, onClick: () -> Unit, modifier: Modifier) {
    Surface(onClick, modifier, color = C.White, shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, C.Border)) {
        Column(Modifier.padding(18.dp).heightIn(min = 100.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineLarge, color = C.Forest)
            Text(label, style = MaterialTheme.typography.bodyMedium, color = C.Muted)
        }
    }
}
