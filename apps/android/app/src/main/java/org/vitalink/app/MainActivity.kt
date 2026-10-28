package org.vitalink.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.vitalink.app.data.SavedReferral
import org.vitalink.domain.*

// --- CLINICAL & MATERNAL COLOR PALETTE ---
private val BrandWhite = Color(0xFFFFFFFF)
private val BrandCard = Color(0xFFF8FAFC)
private val BrandBorder = Color(0xFFE2E8F0)
private val BrandInk = Color(0xFF0F172A)
private val BrandMuted = Color(0xFF475569)
private val BrandEmerald = Color(0xFF059669)
private val BrandEmeraldDark = Color(0xFF047857)
private val BrandEmeraldWash = Color(0xFFECFDF5)
private val BrandMaternalRose = Color(0xFFE11D48)
private val BrandMaternalBlush = Color(0xFFFFF1F2)
private val BrandMaternalBorder = Color(0xFFFECDD3)
private val BrandAmber = Color(0xFFB45309)
private val BrandAmberWash = Color(0xFFFEF3C7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = BrandEmerald,
                    onPrimary = Color.White,
                    secondary = BrandInk,
                    onSecondary = Color.White,
                    secondaryContainer = BrandEmeraldWash,
                    onSecondaryContainer = BrandEmeraldDark,
                    surfaceVariant = BrandCard,
                    onSurfaceVariant = BrandMuted,
                    background = BrandWhite,
                    surface = BrandWhite,
                    onBackground = BrandInk,
                    onSurface = BrandInk,
                    error = BrandMaternalRose,
                    errorContainer = BrandMaternalBlush,
                    onErrorContainer = BrandMaternalRose,
                    outline = BrandBorder,
                )
            ) {
                VitalinkScreen()
            }
        }
    }
}

@Composable
private fun VitalinkScreen(vm: AssessmentViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var deleteAll by remember { mutableStateOf(false) }
    var handoff by remember { mutableStateOf<String?>(null) }
    var retryId by remember { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = BrandWhite,
        bottomBar = {
            NavigationBar(
                containerColor = BrandWhite,
                tonalElevation = 8.dp,
                modifier = Modifier.background(BrandWhite)
            ) {
                val tabs = listOf(
                    Triple("Assessment", "Screening", "🩺"),
                    Triple("Referrals", "Outbox", "🏥"),
                    Triple("History", "Records", "📋"),
                    Triple("Account", "Facility", "🛡️")
                )
                tabs.forEachIndexed { i, (label, sub, icon) ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(icon, fontSize = 18.sp)
                            }
                        },
                        label = {
                            Text(
                                label,
                                fontSize = 11.sp,
                                fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal,
                                color = if (tab == i) BrandEmerald else BrandMuted
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BrandEmerald,
                            unselectedIconColor = BrandMuted,
                            indicatorColor = BrandEmeraldWash
                        )
                    )
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- MATERNAL HEADER ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            color = BrandMaternalBlush,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, BrandMaternalBorder)
                        ) {
                            Text(
                                "♥ MATERNAL & NEWBORN",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                color = BrandMaternalRose,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "VITALINK",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = BrandInk,
                        letterSpacing = 1.sp
                    )
                }
                Surface(
                    color = if (state.online) BrandEmeraldWash else BrandCard,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (state.online) BrandEmerald else BrandBorder)
                ) {
                    Text(
                        if (state.online) "● Network active" else "● Offline Safe",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = if (state.online) BrandEmeraldDark else BrandMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider(color = BrandBorder)

            Text(
                "Research prototype · synthetic patient data only. Instant on-device preeclampsia triage.",
                style = MaterialTheme.typography.bodySmall,
                color = BrandMuted
            )

            if (state.busy) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = BrandEmerald,
                    trackColor = BrandEmeraldWash
                )
            }

            state.message?.let { msg ->
                Surface(
                    color = BrandEmeraldWash,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BrandEmerald)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(msg, color = BrandEmeraldDark, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        TextButton(onClick = vm::dismissMessage) {
                            Text("Dismiss", color = BrandEmeraldDark, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (state.errors.isNotEmpty()) {
                Surface(
                    color = BrandMaternalBlush,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BrandMaternalBorder)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        state.errors.forEach {
                            Text("⚠️ $it", color = BrandMaternalRose, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            when (tab) {
                0 -> AssessmentForm(state, vm)
                1 -> {
                    Text("Referral Handoff Outbox", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = BrandInk)
                    Text("Saved locally ≠ received by server ≠ acknowledged by doctor.", color = BrandMuted, fontSize = 13.sp)
                    Button(
                        onClick = vm::sync,
                        enabled = !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
                    ) {
                        Text("Synchronize and refresh status", fontWeight = FontWeight.Bold)
                    }
                    if (state.records.referrals.isEmpty()) {
                        EmptyNote("No referrals saved", "Create a referral from an assessment. It saves instantly on this phone without network.")
                    }
                    state.records.referrals.forEach { r ->
                        ReferralCard(r, state.busy, { handoff = HandoffSummary.render(r.id, r.assessment, r.state) }, { deleteId = r.id }, { retryId = r.id })
                    }
                }
                2 -> {
                    Text("Measurement History", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = BrandInk)
                    Text("Immutable assessment snapshots stored on this device with KeyStore encryption.", color = BrandMuted, fontSize = 13.sp)
                    if (state.records.assessments.isEmpty()) {
                        EmptyNote("No measurements yet", "Saved maternal assessments remain available offline after app restart.")
                    }
                    state.records.assessments.forEach { saved ->
                        OutlinedCard(
                            Modifier.fillMaxWidth(),
                            colors = CardDefaults.outlinedCardColors(containerColor = BrandWhite),
                            border = BorderStroke(1.dp, BrandBorder)
                        ) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                AssessmentSummary(saved.assessment)
                                val referral = state.records.referrals.find { it.assessment.id == saved.assessment.id }
                                if (referral == null) {
                                    Button(
                                        onClick = { vm.refer(saved.assessment) },
                                        enabled = !state.busy,
                                        colors = ButtonDefaults.buttonColors(containerColor = BrandInk)
                                    ) {
                                        Text("Create referral from snapshot")
                                    }
                                    TextButton(onClick = { vm.deleteAssessment(saved.assessment.id) }, enabled = !state.busy) {
                                        Text("Delete assessment", color = BrandMaternalRose)
                                    }
                                } else {
                                    Text("Linked referral: ${referral.id.take(8)}...", style = MaterialTheme.typography.bodySmall, color = BrandEmerald)
                                }
                            }
                        }
                    }
                }
                3 -> {
                    AccountPanel(state, vm)
                    HorizontalDivider(color = BrandBorder)
                    Text("Privacy and Retention", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandInk)
                    Text(
                        "Clinical payloads and account tokens are encrypted with Android Keystore-backed AES-256-GCM. Opaque record IDs remain visible for outbox indexation. Device backup is disabled.",
                        color = BrandMuted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                    OutlinedButton(
                        onClick = { deleteAll = true },
                        enabled = !state.busy,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = BrandMaternalRose),
                        border = BorderStroke(1.dp, BrandMaternalBorder)
                    ) {
                        Text("Delete all local records and sign out")
                    }
                    Text("Clinical safety rules: ${ClinicalRules.VERSION}", style = MaterialTheme.typography.labelMedium, color = BrandMuted)
                    Text(RulesOnlyInference().infer(state.draft.assessment()).explanation, fontSize = 12.sp, color = BrandMuted)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { deleteId = null },
            title = { Text("Delete local referral?", fontWeight = FontWeight.Bold) },
            text = { Text("This permanently removes the device copy. An unsent referral will not reach a clinician. Use local handoff procedures first. Server records are unaffected.") },
            confirmButton = { TextButton(onClick = { vm.deleteReferral(id); deleteId = null }) { Text("Delete local copy", color = BrandMaternalRose) } },
            dismissButton = { TextButton(onClick = { deleteId = null }) { Text("Keep record") } }
        )
    }

    if (deleteAll) {
        AlertDialog(
            onDismissRequest = { deleteAll = false },
            title = { Text("Delete all device records?", fontWeight = FontWeight.Bold) },
            text = { Text("All local measurements, pending referrals and this device's session will be removed. Any unsent referral will be lost. Contact the receiving clinician through local escalation before deleting urgent handoffs.") },
            confirmButton = { TextButton(onClick = { vm.deleteAll(); deleteAll = false }) { Text("Delete device data", color = BrandMaternalRose) } },
            dismissButton = { TextButton(onClick = { deleteAll = false }) { Text("Cancel") } }
        )
    }

    handoff?.let { text ->
        AlertDialog(
            onDismissRequest = { handoff = null },
            title = { Text("Offline Handoff Summary", fontWeight = FontWeight.Bold) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp) } },
            confirmButton = { TextButton(onClick = { handoff = null }) { Text("Close summary", fontWeight = FontWeight.Bold) } }
        )
    }

    retryId?.let { id ->
        AlertDialog(
            onDismissRequest = { retryId = null },
            title = { Text("Retry after technical review?", fontWeight = FontWeight.Bold) },
            text = { Text("Check the reported error with technical support. Sign in again for authentication errors. This retries the same immutable snapshot and identifier.") },
            confirmButton = { TextButton(onClick = { vm.retryReferral(id); retryId = null }) { Text("Retry unchanged referral") } },
            dismissButton = { TextButton(onClick = { retryId = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AssessmentForm(state: UiState, vm: AssessmentViewModel) {
    val draft = state.draft
    val findings = ClinicalRules.assess(draft.systolic.toIntOrNull(), draft.diastolic.toIntOrNull(), draft.symptoms)

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Maternal Assessment", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = BrandInk)
                Text("Offline doorstep screening · Mother & fetal vitals", style = MaterialTheme.typography.bodySmall, color = BrandMuted)
            }
            Surface(
                color = BrandCard,
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, BrandBorder)
            ) {
                Text("DRAFT", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandMuted)
            }
        }

        // Live decision support banner if urgent
        if (findings.urgency == Urgency.URGENT) {
            FindingsPanel(findings)
        }

        // Patient ID card
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(containerColor = BrandWhite),
            border = BorderStroke(1.dp, BrandBorder)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("PATIENT IDENTIFIER", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandMuted, letterSpacing = 0.5.sp)
                OutlinedTextField(
                    value = draft.patientId,
                    onValueChange = { v -> vm.edit { it.copy(patientId = v) } },
                    label = { Text("Synthetic Patient ID") },
                    placeholder = { Text("SYN-001") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = BrandEmerald,
                        unfocusedBorderColor = BrandBorder
                    )
                )
            }
        }

        // Blood Pressure card
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(containerColor = BrandWhite),
            border = BorderStroke(1.dp, BrandBorder)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("BLOOD PRESSURE (MMHG)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandMuted, letterSpacing = 0.5.sp)
                    Text("Severe: ≥160 or ≥110", fontSize = 11.sp, color = BrandMaternalRose, fontWeight = FontWeight.Bold)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MeasurementField("Systolic mmHg", draft.systolic, { v -> vm.edit { it.copy(systolic = v) } }, Modifier.weight(1f))
                    MeasurementField("Diastolic mmHg", draft.diastolic, { v -> vm.edit { it.copy(diastolic = v) } }, Modifier.weight(1f))
                }
            }
        }

        // Gestational age card
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(containerColor = BrandWhite),
            border = BorderStroke(1.dp, BrandBorder)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val gestationInt = draft.gestation.toIntOrNull()
                val trimesterLabel = when {
                    gestationInt == null -> "Pregnancy progress"
                    gestationInt in 1..12 -> "1st Trimester (1–12 weeks)"
                    gestationInt in 13..27 -> "2nd Trimester (13–27 weeks)"
                    gestationInt in 28..42 -> "3rd Trimester · Peak Preeclampsia Risk"
                    else -> "Out of range"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("GESTATIONAL AGE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandMuted, letterSpacing = 0.5.sp)
                    if (gestationInt != null && gestationInt in 1..42) {
                        Surface(
                            color = if (gestationInt >= 28) BrandMaternalBlush else BrandEmeraldWash,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                trimesterLabel,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (gestationInt >= 28) BrandMaternalRose else BrandEmeraldDark
                            )
                        }
                    }
                }

                MeasurementField(
                    "Gestational age · weeks (optional)",
                    draft.gestation,
                    { v -> vm.edit { it.copy(gestation = v) } },
                    Modifier.fillMaxWidth()
                )
            }
        }

        // Concerning Symptoms card
        OutlinedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.outlinedCardColors(containerColor = BrandWhite),
            border = BorderStroke(1.dp, BrandBorder)
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("MATERNAL RED-FLAG SYMPTOMS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = BrandMuted, letterSpacing = 0.5.sp)
                Text(
                    "Ask and assess. A normal blood pressure does not cancel a concerning symptom.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BrandMuted
                )

                val symptomIcons = mapOf(
                    Symptom.SEVERE_HEADACHE to "🤕",
                    Symptom.VISUAL_DISTURBANCE to "👁️",
                    Symptom.UPPER_ABDOMINAL_PAIN to "⚡",
                    Symptom.BREATHLESSNESS to "🫁",
                    Symptom.SEIZURE to "🌪️",
                    Symptom.HEAVY_BLEEDING to "🩸",
                    Symptom.REDUCED_FETAL_MOVEMENT to "👶"
                )

                Symptom.entries.forEach { symptom ->
                    val isChecked = symptom in draft.symptoms
                    val icon = symptomIcons[symptom] ?: "⚠️"
                    val label = symptom.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                vm.edit {
                                    it.copy(symptoms = if (isChecked) it.symptoms - symptom else it.symptoms + symptom)
                                }
                            },
                        color = if (isChecked) BrandMaternalBlush else BrandCard,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (isChecked) BrandMaternalRose else BrandBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(icon, fontSize = 18.sp)
                                Text(
                                    label,
                                    fontSize = 14.sp,
                                    fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isChecked) BrandMaternalRose else BrandInk
                                )
                            }
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    vm.edit {
                                        it.copy(symptoms = if (checked) it.symptoms + symptom else it.symptoms - symptom)
                                    }
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = BrandMaternalRose,
                                    checkmarkColor = Color.White
                                )
                            )
                        }
                    }
                }
            }
        }

        // Live decision support banner if not urgent
        if (findings.urgency != Urgency.URGENT) {
            FindingsPanel(findings)
        }

        Text(
            "Offline referrals are addressed to ${state.session?.facilityId ?: "demo-facility"}.",
            style = MaterialTheme.typography.bodySmall,
            color = BrandMuted
        )

        // Action Buttons
        Button(
            onClick = { vm.save(true) },
            enabled = !state.busy && !state.storageError,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BrandInk)
        ) {
            Text("Save offline referral", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        OutlinedButton(
            onClick = { vm.save(false) },
            enabled = !state.busy && !state.storageError,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, BrandBorder)
        ) {
            Text("Save assessment only", color = BrandInk, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun MeasurementField(label: String, value: String, change: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = change,
        label = { Text(label, fontSize = 13.sp) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BrandEmerald,
            unfocusedBorderColor = BrandBorder
        )
    )
}

@Composable
private fun FindingsPanel(f: Findings) {
    val isUrgent = f.urgency == Urgency.URGENT
    val isReview = f.urgency == Urgency.REVIEW_REQUIRED

    Surface(
        color = when {
            isUrgent -> BrandMaternalBlush
            isReview -> BrandAmberWash
            else -> BrandEmeraldWash
        },
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.5.dp,
            when {
                isUrgent -> BrandMaternalRose
                isReview -> BrandAmber
                else -> BrandEmerald
            }
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (isUrgent) "🚨" else if (isReview) "⚠️" else "✅", fontSize = 18.sp)
                Text(
                    text = when (f.urgency) {
                        Urgency.URGENT -> "Urgent clinical assessment"
                        Urgency.REVIEW_REQUIRED -> "Clinical review required"
                        Urgency.NO_TRIGGER -> "No configured rule triggered"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isUrgent -> BrandMaternalRose
                        isReview -> BrandAmber
                        else -> BrandEmeraldDark
                    }
                )
            }

            Text(
                f.message,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = BrandInk,
                lineHeight = 18.sp
            )

            if (isUrgent) {
                Text(
                    "Do not wait for synchronization — follow local escalation now.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = BrandMaternalRose
                )
            }

            if (f.triggers.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    f.triggers.forEach { trigger ->
                        Surface(
                            color = Color.White.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, if (isUrgent) BrandMaternalBorder else BrandBorder)
                        ) {
                            Text(
                                trigger.lowercase().replace('_', ' '),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = BrandInk
                            )
                        }
                    }
                }
            }

            if (f.missing.isNotEmpty()) {
                Text("Missing: ${f.missing.joinToString()}", style = MaterialTheme.typography.bodySmall, color = BrandMuted)
            }
        }
    }
}

@Composable
private fun AssessmentSummary(a: Assessment) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(a.patientId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandInk)
            Surface(
                color = BrandCard,
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, BrandBorder)
            ) {
                Text(a.source.lowercase(), modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), fontSize = 11.sp, color = BrandMuted)
            }
        }

        Text("${a.systolic ?: "—"} / ${a.diastolic ?: "—"} mmHg", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = BrandInk)
        Text("Gestation: ${a.gestationalWeeks ?: "missing"} weeks", fontSize = 13.sp, color = BrandMuted)
        Text("Symptoms: ${a.symptoms.joinToString { it.name.lowercase().replace('_', ' ') }.ifBlank { "none reported" }}", style = MaterialTheme.typography.bodySmall, color = BrandMuted)

        val urgency = ClinicalRules.assess(a).urgency
        Surface(
            color = if (urgency == Urgency.URGENT) BrandMaternalBlush else BrandCard,
            shape = RoundedCornerShape(4.dp)
        ) {
            Text(
                "$urgency · ${a.ruleVersion}",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (urgency == Urgency.URGENT) BrandMaternalRose else BrandMuted
            )
        }
    }
}

@Composable
private fun ReferralCard(r: SavedReferral, busy: Boolean, handoff: () -> Unit, delete: () -> Unit, retry: () -> Unit) {
    OutlinedCard(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.outlinedCardColors(containerColor = BrandWhite),
        border = BorderStroke(1.dp, BrandBorder)
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AssessmentSummary(r.assessment)
            HorizontalDivider(color = BrandBorder)

            val stateColor = when (r.state) {
                ReferralState.DRAFT -> BrandMuted
                ReferralState.PENDING_SYNC -> BrandAmber
                ReferralState.SENT -> BrandInk
                ReferralState.SERVER_RECEIVED -> BrandEmerald
                ReferralState.ACKNOWLEDGED -> BrandEmeraldDark
                ReferralState.CLOSED -> BrandMuted
            }

            Surface(
                color = when (r.state) {
                    ReferralState.PENDING_SYNC -> BrandAmberWash
                    ReferralState.SERVER_RECEIVED, ReferralState.ACKNOWLEDGED -> BrandEmeraldWash
                    else -> BrandCard
                },
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, stateColor.copy(alpha = 0.5f))
            ) {
                Text(
                    r.state.name.replace('_', ' '),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = stateColor
                )
            }

            Text(
                when (r.state) {
                    ReferralState.DRAFT -> "Not queued or delivered."
                    ReferralState.PENDING_SYNC -> "Saved locally. Server delivery is unconfirmed."
                    ReferralState.SENT -> "Transmission in progress. Delivery is unconfirmed."
                    ReferralState.SERVER_RECEIVED -> "Server confirmed receipt. No clinician acknowledgment yet."
                    ReferralState.ACKNOWLEDGED -> "Explicitly acknowledged by ${r.acknowledgedBy} at ${r.acknowledgedAt}."
                    ReferralState.CLOSED -> "Closed after clinical acknowledgment."
                },
                fontSize = 13.sp,
                color = BrandInk
            )

            if (ClinicalRules.assess(r.assessment).urgency == Urgency.URGENT) {
                Text("Urgent: follow local escalation now. Do not wait for delivery or acknowledgment.", color = BrandMaternalRose, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Text("Referral ${r.id.take(8)}... · Facility: ${r.facilityId}", style = MaterialTheme.typography.bodySmall, color = BrandMuted)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = handoff, shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, BrandBorder)) {
                    Text("View handoff summary", fontSize = 12.sp, color = BrandInk)
                }
                if (r.blocked) {
                    OutlinedButton(onClick = retry, enabled = !busy, shape = RoundedCornerShape(6.dp)) {
                        Text("Retry", fontSize = 12.sp)
                    }
                }
                TextButton(onClick = delete, enabled = !busy && r.state != ReferralState.SENT) {
                    Text("Delete", color = BrandMaternalRose, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun AccountPanel(state: UiState, vm: AssessmentViewModel) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Device Account & Sync", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = BrandInk)
        if (state.session != null) {
            Surface(
                color = BrandEmeraldWash,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, BrandEmerald)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Authenticated User: ${state.session.username}", fontWeight = FontWeight.Bold, color = BrandEmeraldDark)
                    Text("Role: ${state.session.role} · Facility: ${state.session.facilityId}", fontSize = 13.sp, color = BrandEmeraldDark)
                    Text("Session expires: ${state.session.expiresAt}", fontSize = 11.sp, color = BrandMuted)
                }
            }
            OutlinedButton(
                onClick = vm::logout,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Sign out", color = BrandInk)
            }
        } else {
            Text("Sign in to synchronize with hospital. Offline assessment and local saving work without connectivity.", color = BrandMuted, fontSize = 13.sp)
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandEmerald, unfocusedBorderColor = BrandBorder)
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BrandEmerald, unfocusedBorderColor = BrandBorder)
            )
            Button(
                onClick = { vm.login(username, password); password = "" },
                enabled = !state.busy && username.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandEmerald)
            ) {
                Text("Sign in securely", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun EmptyNote(title: String, body: String) {
    Surface(
        color = BrandCard,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, BrandBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = BrandInk)
            Text(body, style = MaterialTheme.typography.bodySmall, color = BrandMuted)
        }
    }
}
