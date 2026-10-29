package org.vitalink.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.vitalink.app.UiState
import org.vitalink.app.data.SavedReferral
import org.vitalink.app.ui.*
import org.vitalink.app.ui.components.*
import org.vitalink.app.ui.theme.ClinicalValue
import org.vitalink.app.ui.theme.VitalinkColors as C
import org.vitalink.domain.*
import java.time.Instant

@Composable
fun AssessmentListRow(assessment: Assessment, referralState: ReferralState? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth(), color = C.White,
        shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, C.Border)) {
        Row(Modifier.padding(18.dp).heightIn(min = 60.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(assessment.patientId, style = MaterialTheme.typography.titleMedium)
                Text("${assessment.systolic ?: "—"} / ${assessment.diastolic ?: "—"} mmHg",
                    style = MaterialTheme.typography.bodyLarge)
                Text(recordedTime(assessment.measuredAt), style = MaterialTheme.typography.bodySmall, color = C.Muted)
                if (referralState != null) Text(referralLabel(referralState), style = MaterialTheme.typography.labelMedium, color = C.Forest)
                val urgency = ClinicalRules.assess(assessment).urgency
                Text(urgencyLabel(urgency), style = MaterialTheme.typography.labelMedium,
                    color = when (urgency) { Urgency.URGENT -> C.Urgent; Urgency.REVIEW_REQUIRED -> C.Review; else -> C.Muted })
            }
            AppIcon(AppIcons.Chevron, tint = C.Muted)
        }
    }
}

@Composable
fun RecordsScreen(state: UiState, onOpen: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(0) }
    val filtered = state.records.assessments.sortedByDescending { it.createdAt }
        .filter { it.assessment.patientId.contains(query.trim(), ignoreCase = true) }
    val pages = ((filtered.size + 19) / 20).coerceAtLeast(1)
    val current = page.coerceIn(0, pages - 1)
    PageHeading("Every observation,\nkept together.", "Saved assessment snapshots are available on this device, including offline.", "RECORDS")
    OutlinedTextField(query, { query = it; page = 0 }, Modifier.fillMaxWidth(),
        label = { Text("Search synthetic identifier") }, leadingIcon = { AppIcon(AppIcons.Search) },
        singleLine = true, shape = RoundedCornerShape(16.dp))
    Text("${filtered.size} saved assessment${if (filtered.size == 1) "" else "s"}",
        style = MaterialTheme.typography.labelMedium, color = C.Muted)
    if (filtered.isEmpty()) {
        EmptyState(if (query.isBlank()) "No assessments saved yet" else "No matching identifier",
            if (query.isBlank()) "Start an assessment from Home. Save it to keep an encrypted snapshot on this device."
            else "Check the synthetic identifier or clear your search.")
    } else filtered.drop(current * 20).take(20).forEach { saved ->
        AssessmentListRow(saved.assessment, state.records.referrals.firstOrNull { it.assessment.id == saved.assessment.id }?.state) {
            onOpen(saved.assessment.id)
        }
    }
    if (pages > 1) LocalPagination(current, pages, { page-- }, { page++ })
    ResearchFootnote()
}

@Composable
fun ReferralsScreen(state: UiState, onOpen: (String) -> Unit, onSync: () -> Unit) {
    var filter by remember { mutableStateOf("All") }
    var page by remember { mutableIntStateOf(0) }
    val filtered = state.records.referrals.filter { referral -> when (filter) {
        "Pending" -> !isDeliveryConfirmed(referral.state)
        "Received" -> referral.state == ReferralState.SERVER_RECEIVED
        "Acknowledged" -> referral.state == ReferralState.ACKNOWLEDGED
        "Closed" -> referral.state == ReferralState.CLOSED
        else -> true
    } }.sortedWith(compareBy<SavedReferral> { when (ClinicalRules.assess(it.assessment).urgency) {
        Urgency.URGENT -> 0; Urgency.REVIEW_REQUIRED -> 1; Urgency.NO_TRIGGER -> 2
    } }.thenBy { it.createdAt })
    val pages = ((filtered.size + 19) / 20).coerceAtLeast(1)
    val current = page.coerceIn(0, pages - 1)
    PageHeading("Follow the handoff.", "Local saving, server receipt and clinician acknowledgment are separate events.", "REFERRALS")
    PrimaryAction(if (state.busy) "Checking statuses…" else "Synchronize referrals", onSync,
        enabled = !state.busy && !state.storageError, icon = AppIcons.Sync)
    if (!state.online) Text("Offline mode · a saved referral has not necessarily notified the receiving facility. Use local handoff procedures for urgent findings.",
        style = MaterialTheme.typography.bodyMedium, color = C.Muted)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("All", "Pending", "Received", "Acknowledged", "Closed").forEach { title ->
            FilterChip(selected = filter == title, onClick = { filter = title; page = 0 },
                label = { Text(title, style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.heightIn(min = 48.dp),
                colors = FilterChipDefaults.filterChipColors(containerColor = C.White,
                    selectedContainerColor = C.PinkSurface, selectedLabelColor = C.Forest))
        }
    }
    if (filtered.isEmpty()) EmptyState("No ${if (filter == "All") "" else filter.lowercase() + " "}referrals",
        "Referrals created from assessments appear here with their confirmed delivery state.", AppIcons.Referral)
    else filtered.drop(current * 20).take(20).forEach { referral -> ReferralListRow(referral) { onOpen(referral.id) } }
    if (pages > 1) LocalPagination(current, pages, { page-- }, { page++ })
    ResearchFootnote()
}

@Composable
private fun LocalPagination(page: Int, total: Int, previous: () -> Unit, next: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        TextButton(previous, enabled = page > 0, modifier = Modifier.heightIn(min = 48.dp)) { Text("Previous") }
        Text("${page + 1} of $total", style = MaterialTheme.typography.labelMedium)
        TextButton(next, enabled = page + 1 < total, modifier = Modifier.heightIn(min = 48.dp)) { Text("Next") }
    }
}

@Composable
private fun ReferralListRow(referral: SavedReferral, onOpen: () -> Unit) {
    val unverified = referral.state in setOf(ReferralState.ACKNOWLEDGED, ReferralState.CLOSED) &&
        !hasClinicalAcknowledgment(referral.state, referral.acknowledgedAt, referral.acknowledgedBy)
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth(), color = C.White,
        shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, C.Border)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(referral.assessment.patientId, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                AppIcon(AppIcons.Chevron, tint = C.Muted)
            }
            val urgency = ClinicalRules.assess(referral.assessment).urgency
            Text(urgencyLabel(urgency), style = MaterialTheme.typography.labelMedium,
                color = if (urgency == Urgency.URGENT) C.Urgent else if (urgency == Urgency.REVIEW_REQUIRED) C.Review else C.Muted)
            Text(if (unverified) "Acknowledgment not verified" else referralLabel(referral.state),
                style = MaterialTheme.typography.titleSmall, color = C.Forest)
            Text(if (unverified) "This record lacks verifiable clinician acknowledgment. Refresh it or request technical review."
                else deliveryExplanation(referral.state), style = MaterialTheme.typography.bodyMedium, color = C.Muted)
            if (referral.blocked) Text("Action needed before retry", style = MaterialTheme.typography.labelMedium, color = C.Urgent)
            Text("Reference ${referral.id.take(8)} · ${recordedTime(referral.assessment.measuredAt)}",
                style = MaterialTheme.typography.bodySmall, color = C.Muted)
        }
    }
}

@Composable
fun ReferralDetailScreen(referral: SavedReferral, busy: Boolean, onSync: () -> Unit,
                         onHandoff: () -> Unit, onRetry: () -> Unit, onDelete: () -> Unit,
                         onAssessment: () -> Unit) {
    val findings = ClinicalRules.assess(referral.assessment)
    val unverified = referral.state in setOf(ReferralState.ACKNOWLEDGED, ReferralState.CLOSED) &&
        !hasClinicalAcknowledgment(referral.state, referral.acknowledgedAt, referral.acknowledgedBy)
    PageHeading("Referral details", referral.assessment.patientId)
    ClinicalAlert(findings)
    OutlinePanel {
        Text(if (unverified) "Acknowledgment not verified" else referralLabel(referral.state), style = MaterialTheme.typography.titleLarge, color = C.Forest)
        Text(if (unverified) "This record lacks verifiable clinician acknowledgment. Refresh it or request technical review."
            else deliveryExplanation(referral.state), style = MaterialTheme.typography.bodyLarge)
        DetailLine("Referral identifier", referral.id)
        DetailLine("Receiving facility", referral.facilityId)
        DetailLine("Saved on this device", recordedTime(Instant.ofEpochMilli(referral.createdAt).toString()))
    }
    SectionHeading("Handoff progress")
    ReferralTimeline(referral)
    if (hasClinicalAcknowledgment(referral.state, referral.acknowledgedAt, referral.acknowledgedBy)) {
        OutlinePanel {
            DetailLine("Acknowledged by", requireNotNull(referral.acknowledgedBy))
            DetailLine("Acknowledgment time", recordedTime(requireNotNull(referral.acknowledgedAt)))
            Text("Acknowledgment records a coordination action. It is not proof that treatment occurred.",
                style = MaterialTheme.typography.bodyMedium, color = C.Muted)
        }
    } else if (referral.state in setOf(ReferralState.ACKNOWLEDGED, ReferralState.CLOSED)) {
        Notice("Acknowledgment evidence is unavailable on this record. Refresh or request technical review; do not assume clinical receipt.")
    }
    if (referral.lastErrorCode != null) OutlinePanel {
        Text("Delivery needs attention", style = MaterialTheme.typography.titleSmall, color = C.Urgent)
        Text(readableSyncError(referral.lastErrorCode), style = MaterialTheme.typography.bodyMedium)
        Text("Attempts recorded: ${referral.attemptCount}", style = MaterialTheme.typography.bodySmall, color = C.Muted)
        if (!referral.blocked && referral.nextAttemptAt != null) {
            DetailLine("Next eligible retry", recordedTime(Instant.ofEpochMilli(referral.nextAttemptAt).toString()))
        }
    }
    PrimaryAction("View offline handoff summary", onHandoff, icon = AppIcons.Referral)
    SecondaryAction("Refresh delivery status", onSync, enabled = !busy)
    if (referral.blocked && referral.state == ReferralState.PENDING_SYNC) {
        SecondaryAction("Retry after technical review", onRetry, enabled = !busy)
    }
    TextButton(onAssessment, Modifier.heightIn(min = 48.dp)) { Text("View assessment observations") }
    HorizontalDivider(color = C.Border)
    Text("Use established telephone or in-person handoff procedures when connectivity is unavailable. Urgent clinical assessment must not wait for synchronization.",
        style = MaterialTheme.typography.bodyMedium, color = C.Muted)
    TextButton(onDelete, enabled = !busy && referral.state != ReferralState.SENT,
        modifier = Modifier.heightIn(min = 48.dp)) {
        AppIcon(AppIcons.Delete, tint = C.Urgent); Spacer(Modifier.width(8.dp)); Text("Delete device copy", color = C.Urgent)
    }
    ResearchFootnote()
}

@Composable
private fun ReferralTimeline(referral: SavedReferral) {
    val states = ReferralState.entries
    val current = states.indexOf(referral.state)
    states.forEachIndexed { index, step ->
        val acknowledged = hasClinicalAcknowledgment(referral.state, referral.acknowledgedAt, referral.acknowledgedBy)
        val confirmed = when (step) {
            ReferralState.DRAFT -> true
            ReferralState.PENDING_SYNC -> current >= 1
            ReferralState.SENT -> current > 2
            ReferralState.SERVER_RECEIVED -> isDeliveryConfirmed(referral.state)
            ReferralState.ACKNOWLEDGED -> acknowledged
            ReferralState.CLOSED -> referral.state == ReferralState.CLOSED
        }
        val active = step == referral.state
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp).semantics {
            contentDescription = "${referralLabel(step)}. ${if (confirmed) "Confirmed" else if (active) "Current, not delivery confirmation" else "Not confirmed"}"
        }, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AppIcon(if (confirmed) AppIcons.Confirmed else AppIcons.Time,
                tint = if (confirmed || active) C.Forest else C.Border)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(referralLabel(step), style = MaterialTheme.typography.titleSmall, color = if (confirmed || active) C.Ink else C.Muted)
                Text(when (step) {
                    ReferralState.DRAFT -> "Assessment prepared"
                    ReferralState.PENDING_SYNC -> "Committed locally; delivery unconfirmed"
                    ReferralState.SENT -> "Attempting transmission, not confirmed delivery"
                    ReferralState.SERVER_RECEIVED -> "Backend confirmed receipt"
                    ReferralState.ACKNOWLEDGED -> "Requires explicit clinician action and evidence"
                    ReferralState.CLOSED -> "Coordination closed; no recovery claim"
                }, style = MaterialTheme.typography.bodyMedium, color = C.Muted)
            }
        }
        if (index != states.lastIndex) HorizontalDivider(Modifier.padding(start = 38.dp, top = 8.dp, bottom = 8.dp), color = C.Border)
    }
}
