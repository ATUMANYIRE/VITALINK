package org.vitalink.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.vitalink.app.data.SavedReferral
import org.vitalink.domain.*
import java.time.Instant

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(
                primary = Color(0xFF17665F), onPrimary = Color.White,
                secondary = Color(0xFF172D3B), secondaryContainer = Color(0xFFE9F0EA), onSecondaryContainer = Color(0xFF172D3B),
                surfaceVariant = Color(0xFFF0EEE7), onSurfaceVariant = Color(0xFF3D4A50),
                background = Color(0xFFFAF8F3), surface = Color(0xFFFFFEFB),
                onBackground = Color(0xFF172D3B), onSurface = Color(0xFF172D3B),
                error = Color(0xFF9C2828), errorContainer = Color(0xFFFFE8E2),
            )) { VitalinkScreen() }
        }
    }
}

@Composable private fun VitalinkScreen(vm: AssessmentViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var deleteAll by remember { mutableStateOf(false) }
    var handoff by remember { mutableStateOf<String?>(null) }
    var retryId by remember { mutableStateOf<String?>(null) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            listOf("Assessment", "Referrals", "History", "Account").forEachIndexed { i, label ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text("0${i + 1}", fontSize = 12.sp) }, label = { Text(label) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("V I T A L I N K", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("Care begins\nwhere you are.", fontFamily = FontFamily.Serif, fontSize = 34.sp, lineHeight = 38.sp)
            Text(if (state.online) "Network available · server delivery checked separately" else "Offline · assessment and secure saving available", style = MaterialTheme.typography.labelMedium)
            HorizontalDivider()
            Text("Research prototype · synthetic patient data only. No diagnosis or medication advice.", style = MaterialTheme.typography.bodySmall)
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { msg ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.small) {
                    Column(Modifier.padding(16.dp)) { Text(msg); TextButton(onClick = vm::dismissMessage) { Text("Dismiss") } }
                }
            }
            if (state.errors.isNotEmpty()) {
                Surface(color = MaterialTheme.colorScheme.errorContainer) { Column(Modifier.padding(16.dp)) { state.errors.forEach { Text(it) } } }
            }
            when (tab) {
                0 -> AssessmentForm(state, vm)
                1 -> {
                    Text("Referral handoff", style = MaterialTheme.typography.headlineSmall)
                    Text("Local save, server receipt and clinician acknowledgment are separate events.")
                    Button(onClick = vm::sync, enabled = !state.busy) { Text("Synchronize and refresh status") }
                    if (state.records.referrals.isEmpty()) EmptyNote("No referrals saved", "Create a referral from an assessment. It can be saved without connectivity.")
                    state.records.referrals.forEach { r -> ReferralCard(r, state.busy, { handoff = HandoffSummary.render(r.id, r.assessment, r.state) }, { deleteId = r.id }, { retryId = r.id }) }
                }
                2 -> {
                    Text("Measurement history", style = MaterialTheme.typography.headlineSmall)
                    Text("Immutable assessment snapshots stored on this device.")
                    if (state.records.assessments.isEmpty()) EmptyNote("No measurements yet", "Saved measurements remain available offline after restart.")
                    state.records.assessments.forEach { saved ->
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssessmentSummary(saved.assessment)
                                val referral = state.records.referrals.find { it.assessment.id == saved.assessment.id }
                                if (referral == null) {
                                    Button(onClick = { vm.refer(saved.assessment) }, enabled = !state.busy) { Text("Create referral from snapshot") }
                                    TextButton(onClick = { vm.deleteAssessment(saved.assessment.id) }, enabled = !state.busy) { Text("Delete assessment") }
                                } else Text("Linked referral: ${referral.id}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                3 -> {
                    AccountPanel(state, vm)
                    HorizontalDivider()
                    Text("Privacy and retention", style = MaterialTheme.typography.titleLarge)
                    Text("Clinical payloads and account tokens are encrypted with an Android Keystore-backed key. Opaque record IDs and delivery timestamps remain visible to the database. Device backup is disabled.")
                    Text("Closed referrals and unlinked assessments are removed locally after 30 days. Unsent referrals are kept until explicitly deleted. Deleting locally does not delete the server record.")
                    OutlinedButton(onClick = { deleteAll = true }, enabled = !state.busy) { Text("Delete all local records and sign out") }
                    Text("Clinical rules ${ClinicalRules.VERSION}", style = MaterialTheme.typography.labelMedium)
                    Text(RulesOnlyInference().infer(state.draft.assessment()).explanation)
                    Text("Offline fallback: use your facility's established emergency contact and transport pathway. A saved referral does not mean a hospital has been notified.")
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    deleteId?.let { id -> AlertDialog(onDismissRequest = { deleteId = null }, title = { Text("Delete local referral?") },
        text = { Text("This permanently removes the device copy. An unsent referral will not reach a clinician. Use local handoff procedures first. Server records are unaffected.") },
        confirmButton = { TextButton(onClick = { vm.deleteReferral(id); deleteId = null }) { Text("Delete local copy") } },
        dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Keep record") } }) }
    if (deleteAll) AlertDialog(onDismissRequest = { deleteAll = false }, title = { Text("Delete all device records?") },
        text = { Text("All local measurements, pending referrals and this device's session will be removed. Any unsent referral will be lost. Contact the receiving clinician through the local escalation pathway before deleting urgent handoffs.") },
        confirmButton = { TextButton(onClick = { vm.deleteAll(); deleteAll = false }) { Text("Delete device data") } },
        dismissButton = { TextButton(onClick = { deleteAll = false }) { Text("Cancel") } })
    handoff?.let { text -> AlertDialog(onDismissRequest = { handoff = null }, title = { Text("Offline handoff summary") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(text) } },
        confirmButton = { TextButton(onClick = { handoff = null }) { Text("Close summary") } }) }
    retryId?.let { id -> AlertDialog(onDismissRequest = { retryId = null }, title = { Text("Retry after review?") },
        text = { Text("Check the reported error with technical support. Sign in again for authentication errors. This retries the same immutable snapshot and identifier; it cannot resolve a conflicting server snapshot by changing patient data. Urgent cases still require direct local escalation.") },
        confirmButton = { TextButton(onClick = { vm.retryReferral(id); retryId = null }) { Text("Retry unchanged referral") } },
        dismissButton = { TextButton(onClick = { retryId = null }) { Text("Cancel") } }) }
}

@Composable private fun AssessmentForm(state: UiState, vm: AssessmentViewModel) {
    val draft = state.draft
    val findings = ClinicalRules.assess(draft.systolic.toIntOrNull(), draft.diastolic.toIntOrNull(), draft.symptoms)
    Text("Maternal assessment", style = MaterialTheme.typography.headlineSmall)
    Text("Draft · not saved", style = MaterialTheme.typography.labelMedium)
    if (findings.urgency == Urgency.URGENT) FindingsPanel(findings)
    OutlinedTextField(value = draft.patientId, onValueChange = { v -> vm.edit { it.copy(patientId = v) } }, label = { Text("Synthetic patient ID") }, placeholder = { Text("SYN-001") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MeasurementField("Systolic mmHg", draft.systolic, { v -> vm.edit { it.copy(systolic = v) } }, Modifier.weight(1f))
        MeasurementField("Diastolic mmHg", draft.diastolic, { v -> vm.edit { it.copy(diastolic = v) } }, Modifier.weight(1f))
    }
    MeasurementField("Gestational age · weeks (optional)", draft.gestation, { v -> vm.edit { it.copy(gestation = v) } }, Modifier.fillMaxWidth())
    Text("Leave an unavailable measurement blank. Missing values require review.", style = MaterialTheme.typography.bodySmall)
    Text("Concerning symptoms", style = MaterialTheme.typography.titleMedium)
    Text("Ask and assess. A normal blood pressure does not cancel a concerning symptom.", style = MaterialTheme.typography.bodySmall)
    Symptom.entries.forEach { symptom ->
        Row(Modifier.fillMaxWidth()) {
            Checkbox(checked = symptom in draft.symptoms, onCheckedChange = { checked -> vm.edit { it.copy(symptoms = if (checked) it.symptoms + symptom else it.symptoms - symptom) } })
            TextButton(onClick = { vm.edit { it.copy(symptoms = if (symptom in it.symptoms) it.symptoms - symptom else it.symptoms + symptom) } }) { Text(symptom.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) }
        }
    }
    if (findings.urgency != Urgency.URGENT) FindingsPanel(findings)
    Text("Offline referrals are addressed to ${state.session?.facilityId ?: "demo-facility"}. Sign in with the intended facility before referral creation when available.", style = MaterialTheme.typography.bodySmall)
    Button(onClick = { vm.save(true) }, enabled = !state.busy && !state.storageError, modifier = Modifier.fillMaxWidth()) { Text("Save offline referral") }
    OutlinedButton(onClick = { vm.save(false) }, enabled = !state.busy && !state.storageError, modifier = Modifier.fillMaxWidth()) { Text("Save assessment only") }
}

@Composable private fun MeasurementField(label: String, value: String, change: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(value = value, onValueChange = change, label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = modifier)
}
@Composable private fun FindingsPanel(f: Findings) {
    Surface(color = if (f.urgency == Urgency.URGENT) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(when(f.urgency) { Urgency.URGENT -> "Urgent clinical assessment"; Urgency.REVIEW_REQUIRED -> "Clinical review required"; Urgency.NO_TRIGGER -> "No configured rule triggered" }, style = MaterialTheme.typography.titleMedium)
            Text(f.message)
            if (f.triggers.isNotEmpty()) Text(f.triggers.joinToString(" · ") { it.lowercase().replace('_', ' ') }, style = MaterialTheme.typography.bodySmall)
            if (f.missing.isNotEmpty()) Text("Missing: ${f.missing.joinToString()}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable private fun AssessmentSummary(a: Assessment) {
    Text(a.patientId, style = MaterialTheme.typography.titleMedium)
    Text("${a.systolic ?: "—"} / ${a.diastolic ?: "—"} mmHg", style = MaterialTheme.typography.headlineSmall)
    Text("${a.measuredAt} · ${a.source.lowercase()}", style = MaterialTheme.typography.bodySmall)
    Text("Gestation: ${a.gestationalWeeks ?: "missing"} weeks")
    Text("Symptoms: ${a.symptoms.joinToString { it.name.lowercase().replace('_', ' ') }.ifBlank { "none reported" }}", style = MaterialTheme.typography.bodySmall)
    Text("${ClinicalRules.assess(a).urgency} · ${a.ruleVersion}", style = MaterialTheme.typography.labelMedium)
}
@Composable private fun ReferralCard(r: SavedReferral, busy: Boolean, handoff: () -> Unit, delete: () -> Unit, retry: () -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AssessmentSummary(r.assessment)
            Text(r.state.name.replace('_', ' '), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(when(r.state) {
                ReferralState.DRAFT -> "Not queued or delivered."
                ReferralState.PENDING_SYNC -> "Saved locally. Server delivery is unconfirmed."
                ReferralState.SENT -> "Transmission in progress. Delivery is unconfirmed."
                ReferralState.SERVER_RECEIVED -> "Server confirmed receipt. No clinician acknowledgment yet."
                ReferralState.ACKNOWLEDGED -> "Explicitly acknowledged by ${r.acknowledgedBy} at ${r.acknowledgedAt}."
                ReferralState.CLOSED -> "Closed after clinical acknowledgment."
            })
            if (ClinicalRules.assess(r.assessment).urgency == Urgency.URGENT) Text("Urgent: follow local escalation now. Do not wait for delivery or acknowledgment.", color = MaterialTheme.colorScheme.error)
            if (r.lastErrorCode != null) Text("${r.lastErrorCode} · attempts ${r.attemptCount}. ${if (r.blocked) "Action required; sign in for authentication errors. Conflicts or invalid responses require technical review; the snapshot was not changed." else "Retry scheduled with bounded backoff."}", style = MaterialTheme.typography.bodySmall)
            Text("Referral ${r.id}\nFacility ${r.facilityId}", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = handoff) { Text("View handoff summary") }
            if (r.blocked) OutlinedButton(onClick = retry, enabled = !busy) { Text("Retry after technical review") }
            TextButton(onClick = delete, enabled = !busy && r.state != ReferralState.SENT) { Text("Delete local copy") }
        }
    }
}
@Composable private fun AccountPanel(state: UiState, vm: AssessmentViewModel) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Text("Device account", style = MaterialTheme.typography.headlineSmall)
    if (state.session != null) {
        Text("${state.session.username} · ${state.session.role}\nFacility ${state.session.facilityId}\nSession expires ${state.session.expiresAt}")
        OutlinedButton(onClick = vm::logout, enabled = !state.busy) { Text("Sign out") }
    } else {
        Text("Sign in to synchronize. Offline assessment and saving do not require connectivity.")
        OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.login(username, password); password = "" }, enabled = !state.busy && username.isNotBlank() && password.isNotBlank()) { Text("Sign in securely") }
    }
}
@Composable private fun EmptyNote(title: String, body: String) {
    Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(title, style = MaterialTheme.typography.titleLarge); Text(body) }
}
