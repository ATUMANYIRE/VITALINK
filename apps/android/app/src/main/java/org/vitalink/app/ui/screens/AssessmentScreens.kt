package org.vitalink.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.vitalink.app.Draft
import org.vitalink.app.UiState
import org.vitalink.app.ui.*
import org.vitalink.app.ui.components.*
import org.vitalink.app.ui.theme.ClinicalValue
import org.vitalink.app.ui.theme.VitalinkColors as C
import org.vitalink.domain.*

@Composable
fun AssessmentProgress(step: Int) {
    val labels = listOf("Intake", "Observations", "Warning signs")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Assessment · Step $step of 3", style = MaterialTheme.typography.labelMedium, color = C.Muted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            labels.forEachIndexed { index, _ ->
                Surface(Modifier.weight(1f).height(3.dp), color = if (index < step) C.Forest else C.Border,
                    shape = RoundedCornerShape(2.dp)) {}
            }
        }
    }
}

@Composable
fun IntakeScreen(state: UiState, edit: ((Draft) -> Draft) -> Unit, onNext: () -> Unit) {
    var attempted by remember { mutableStateOf(false) }
    val a = remember(state.draft) { state.draft.assessment() }
    val allErrors = state.draft.errors(a)
    val patientError = allErrors.firstOrNull { it.contains("synthetic patient", ignoreCase = true) }
    val gestationError = allErrors.firstOrNull { it.contains("Gestational", ignoreCase = true) }
    AssessmentProgress(1)
    PageHeading("Start with the person.", "Use a synthetic identifier to connect observations and referrals.")
    OutlinedTextField(state.draft.patientId, { text -> edit { it.copy(patientId = text) } },
        Modifier.fillMaxWidth().testTag("patient-id-input"), label = { Text("Synthetic patient identifier") },
        placeholder = { Text("SYN-001") }, singleLine = true, isError = attempted && patientError != null,
        supportingText = { Text(if (attempted && patientError != null) patientError else "Required. Do not enter a name, phone number or national ID.") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), shape = RoundedCornerShape(16.dp))
    OutlinedTextField(state.draft.gestation, { text -> edit { it.copy(gestation = text) } },
        Modifier.fillMaxWidth().testTag("gestation-input"), label = { Text("Gestational age") },
        suffix = { Text("weeks") }, singleLine = true, isError = attempted && gestationError != null,
        supportingText = { Text(if (attempted && gestationError != null) gestationError else "Optional · 0–45 completed weeks. Leave blank if unknown.") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        shape = RoundedCornerShape(16.dp))
    val recentIds = state.records.assessments.sortedByDescending { it.createdAt }.map { it.assessment.patientId }.distinct().take(3)
    if (recentIds.isNotEmpty()) {
        SectionHeading("Recent identifiers")
        recentIds.forEach { id ->
            OutlinedButton(onClick = { edit { it.copy(patientId = id) } }, modifier = Modifier.heightIn(min = 48.dp)) { Text(id) }
        }
    }
    Text("Entries stay in this session until saved. Saved assessments remain available offline.",
        style = MaterialTheme.typography.bodyMedium, color = C.Muted)
    PrimaryAction("Continue to observations", {
        attempted = true
        if (patientError == null && gestationError == null) onNext()
    }, enabled = !state.busy)
}

@Composable
fun VitalsScreen(state: UiState, edit: ((Draft) -> Draft) -> Unit, onNext: () -> Unit) {
    var attempted by remember { mutableStateOf(false) }
    val a = remember(state.draft) { state.draft.assessment() }
    val errors = state.draft.errors(a).filter { it.contains("Systolic", true) || it.contains("Diastolic", true) }
    val focus = LocalFocusManager.current
    AssessmentProgress(2)
    PageHeading("Record blood pressure.", "Enter measured values in mmHg. Check the reading and the measurement technique.")
    Text("Patient ${state.draft.patientId}", style = MaterialTheme.typography.labelMedium, color = C.Forest)
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val stack = maxWidth < 320.dp || LocalDensity.current.fontScale > 1.3f
        if (stack) Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            PressureField("Systolic", "systolic-input", state.draft.systolic, attempted && errors.any { it.contains("Systolic", true) },
                { text -> edit { it.copy(systolic = text) } })
            PressureField("Diastolic", "diastolic-input", state.draft.diastolic, attempted && errors.any { it.contains("Diastolic", true) },
                { text -> edit { it.copy(diastolic = text) } })
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PressureField("Systolic", "systolic-input", state.draft.systolic, attempted && errors.any { it.contains("Systolic", true) },
                { text -> edit { it.copy(systolic = text) } }, Modifier.weight(1f))
            PressureField("Diastolic", "diastolic-input", state.draft.diastolic, attempted && errors.any { it.contains("Diastolic", true) },
                { text -> edit { it.copy(diastolic = text) } }, Modifier.weight(1f))
        }
    }
    if (attempted) ValidationErrors(errors)
    OutlinePanel {
        Text("An unavailable measurement stays missing.", style = MaterialTheme.typography.titleSmall)
        Text("Leave unavailable values blank. Missing data need clinical review and must not be treated as normal.",
            style = MaterialTheme.typography.bodyMedium, color = C.Muted)
    }
    Text("Severe systolic ≥160 OR diastolic ≥110 mmHg prompts urgent clinical assessment. Do not wait for connectivity.",
        style = MaterialTheme.typography.bodyMedium, color = C.Muted)
    PrimaryAction("Continue to warning signs", {
        attempted = true
        if (errors.isEmpty()) { focus.clearFocus(); onNext() }
    }, enabled = !state.busy)
}

@Composable
private fun PressureField(label: String, tag: String, value: String, isError: Boolean,
                          onValue: (String) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value, onValue, Modifier.fillMaxWidth().testTag(tag).semantics {
            contentDescription = "$label blood pressure, mmHg"
        }, placeholder = { Text("—", style = ClinicalValue, color = C.Muted) },
            suffix = { Text("mmHg", style = MaterialTheme.typography.bodySmall, color = C.Muted) },
            singleLine = true, textStyle = ClinicalValue, isError = isError,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            shape = RoundedCornerShape(18.dp))
    }
}

@Composable
fun SymptomsScreen(state: UiState, edit: ((Draft) -> Draft) -> Unit, onNext: () -> Unit) {
    AssessmentProgress(3)
    PageHeading("Listen for warning signs.", "Ask about every sign. A concerning symptom remains urgent even when blood pressure is normal.")
    Text("Select reported signs", style = MaterialTheme.typography.titleMedium)
    Symptom.entries.forEach { symptom ->
        val selected = symptom in state.draft.symptoms
        Surface(Modifier.fillMaxWidth(), color = C.White, border = BorderStroke(1.dp, if (selected) C.Forest else C.Border),
            shape = RoundedCornerShape(16.dp)) {
            Row(Modifier.fillMaxWidth().toggleable(selected, role = Role.Checkbox, onValueChange = { checked ->
                edit { it.copy(symptoms = if (checked) it.symptoms + symptom else it.symptoms - symptom) }
            }).padding(horizontal = 16.dp, vertical = 10.dp).heightIn(min = 44.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Checkbox(selected, null)
                Text(symptomLabel(symptom), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
    Text("Unselected signs have not been clinically excluded. If the history is uncertain, seek clinical review.",
        style = MaterialTheme.typography.bodyMedium, color = C.Muted)
    PrimaryAction("Review assessment", onNext, enabled = !state.busy)
}

@Composable
fun AssessmentOutcome(assessment: Assessment, recorded: Boolean) {
    val findings = ClinicalRules.assess(assessment)
    PageHeading(if (recorded) "Assessment record" else "Review the assessment",
        if (recorded) "An immutable snapshot saved on this device." else "Check the observations before saving or creating a referral.")
    ClinicalAlert(findings, includeNoTrigger = true)
    OutlinePanel {
        Text("RECORDED OBSERVATIONS", style = MaterialTheme.typography.labelSmall, color = C.Muted)
        Text(assessment.patientId, style = MaterialTheme.typography.titleLarge)
        Text("${assessment.systolic ?: "—"} / ${assessment.diastolic ?: "—"}", style = ClinicalValue)
        Text("Blood pressure · mmHg", style = MaterialTheme.typography.bodyMedium, color = C.Muted)
        HorizontalDivider(color = C.Border)
        DetailLine("Gestational age", assessment.gestationalWeeks?.let { "$it weeks" } ?: "Not recorded")
        DetailLine("Measurement time", recordedTime(assessment.measuredAt))
        DetailLine("Source", if (assessment.source == "DEVICE") "Device measurement" else "Manually entered measurement")
        DetailLine("Reported warning signs", assessment.symptoms.sortedBy { it.name }.joinToString { symptomLabel(it) }.ifBlank { "No signs recorded; this does not exclude symptoms." })
    }
    SectionHeading("Decision support")
    Text("${urgencyLabel(findings.urgency)} · deterministic clinical rules", style = MaterialTheme.typography.titleSmall)
    if (findings.triggers.isEmpty()) {
        Text("These limited rules did not trigger. This does not establish health or exclude complications.",
            style = MaterialTheme.typography.bodyLarge, color = C.Muted)
    } else findings.triggers.forEach { trigger ->
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppIcon(if (findings.urgency == Urgency.URGENT) AppIcons.Warning else AppIcons.Info,
                tint = if (findings.urgency == Urgency.URGENT) C.Urgent else C.Review)
            Text(triggerLabel(trigger), style = MaterialTheme.typography.bodyLarge)
        }
    }
    SectionHeading("Next action")
    Text(if (findings.urgency == Urgency.URGENT)
        "Contact the responsible clinician or local emergency pathway now. A referral can document the handoff; saving it does not notify a hospital."
    else "Follow local clinical review and referral guidance. Create a referral if required by the clinician or local protocol.",
        style = MaterialTheme.typography.bodyLarge)
    Text("Rules-only mode · ${assessment.ruleVersion}. No validated predictive model or clinical risk probability is available.",
        style = MaterialTheme.typography.bodyMedium, color = C.Muted)
}
