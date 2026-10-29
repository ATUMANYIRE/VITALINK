package org.vitalink.app.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.vitalink.app.AssessmentViewModel
import org.vitalink.app.ui.components.*
import org.vitalink.app.ui.screens.*
import org.vitalink.app.ui.theme.VitalinkColors as C
import org.vitalink.domain.*

private enum class Screen {
    HOME, INTAKE, VITALS, SYMPTOMS, REVIEW, RECORDS, ASSESSMENT_DETAIL,
    REFERRALS, REFERRAL_DETAIL, ACCOUNT, SIGN_IN, PRIVACY, LICENSES
}

@Composable
fun VitalinkApp(vm: AssessmentViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("presentation-preferences", Context.MODE_PRIVATE) }
    var entered by rememberSaveable { mutableStateOf(preferences.getBoolean("welcome-complete", false)) }
    var screenName by rememberSaveable { mutableStateOf(Screen.HOME.name) }
    val screen = runCatching { Screen.valueOf(screenName) }.getOrDefault(Screen.HOME)
    var selectedAssessmentId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedReferralId by rememberSaveable { mutableStateOf<String?>(null) }
    var assessmentReturn by rememberSaveable { mutableStateOf(Screen.RECORDS.name) }
    var deleteReferralId by remember { mutableStateOf<String?>(null) }
    var deleteAssessmentId by remember { mutableStateOf<String?>(null) }
    var deleteAll by remember { mutableStateOf(false) }
    var restart by remember { mutableStateOf(false) }
    var retryId by remember { mutableStateOf<String?>(null) }
    var handoffId by remember { mutableStateOf<String?>(null) }
    val draftAssessment = remember(state.draft) { state.draft.assessment() }
    val draftFindings = remember(state.draft) { ClinicalRules.assess(draftAssessment) }
    val existingUser = state.session != null || state.records.assessments.isNotEmpty() || state.records.referrals.isNotEmpty()
    val welcome = !entered && !existingUser && state.recordsLoaded && !state.busy && !state.storageError
    val loading = !state.recordsLoaded || (!entered && !existingUser && state.busy)
    val topLevel = screen in setOf(Screen.HOME, Screen.RECORDS, Screen.REFERRALS, Screen.ACCOUNT)
    val workflow = screen in setOf(Screen.INTAKE, Screen.VITALS, Screen.SYMPTOMS, Screen.REVIEW)
    val enabled = !state.busy && !state.storageError

    fun navigate(target: Screen) { screenName = target.name }
    fun openAssessment(id: String, from: Screen = Screen.RECORDS) {
        selectedAssessmentId = id; assessmentReturn = from.name; navigate(Screen.ASSESSMENT_DETAIL)
    }
    fun openReferral(id: String) { selectedReferralId = id; navigate(Screen.REFERRAL_DETAIL) }
    fun back() { navigate(when (screen) {
        Screen.INTAKE -> Screen.HOME
        Screen.VITALS -> Screen.INTAKE
        Screen.SYMPTOMS -> Screen.VITALS
        Screen.REVIEW -> Screen.SYMPTOMS
        Screen.ASSESSMENT_DETAIL -> runCatching { Screen.valueOf(assessmentReturn) }.getOrDefault(Screen.RECORDS)
        Screen.REFERRAL_DETAIL -> Screen.REFERRALS
        Screen.SIGN_IN, Screen.PRIVACY, Screen.LICENSES -> Screen.ACCOUNT
        else -> Screen.HOME
    }) }

    LaunchedEffect(existingUser) {
        if (existingUser && !entered) {
            entered = true
            preferences.edit().putBoolean("welcome-complete", true).apply()
        }
    }
    LaunchedEffect(state.savedAssessmentId, state.savedReferralId) {
        val referral = state.savedReferralId
        val assessment = state.savedAssessmentId
        if (referral != null) openReferral(referral)
        else if (assessment != null) openAssessment(assessment)
        if (referral != null || assessment != null) vm.consumeSaveNavigation()
    }
    LaunchedEffect(state.session?.username) { if (state.session != null && screen == Screen.SIGN_IN) navigate(Screen.ACCOUNT) }
    BackHandler(!welcome && !loading && screen != Screen.HOME) { back() }

    Scaffold(containerColor = C.White,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            if (!welcome && !loading) {
                Column(Modifier.statusBarsPadding()) {
                    AppHeader(if (topLevel) null else when (screen) {
                        Screen.INTAKE, Screen.VITALS, Screen.SYMPTOMS, Screen.REVIEW -> "Assessment"
                        Screen.ASSESSMENT_DETAIL -> "Saved assessment"
                        Screen.REFERRAL_DETAIL -> "Referral"
                        Screen.SIGN_IN -> "Sign in"
                        Screen.PRIVACY -> "Privacy"
                        Screen.LICENSES -> "Acknowledgments"
                        else -> "VITALINK"
                    }, state.online, ::back, if (workflow) ({ navigate(Screen.HOME) }) else null)
                    if (draftFindings.urgency == Urgency.URGENT && (workflow && screen != Screen.REVIEW || screen == Screen.HOME)) {
                        ClinicalAlert(draftFindings, Modifier.padding(horizontal = 16.dp, vertical = 8.dp), compact = true)
                    }
                    if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = C.Forest, trackColor = C.Border)
                }
            }
        },
        bottomBar = { if (!welcome && !loading && topLevel) BottomNavigation(screen, ::navigate) },
    ) { insets ->
        val scroll = rememberScrollState()
        LaunchedEffect(screenName, welcome, loading) { scroll.scrollTo(0) }
        Column(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).imePadding().verticalScroll(scroll),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                when {
                    loading -> Column(Modifier.fillMaxWidth().padding(vertical = 80.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        BrandMark(Modifier.size(96.dp))
                        CircularProgressIndicator(color = C.Forest)
                        Text("Opening your encrypted workspace…", style = MaterialTheme.typography.bodyMedium)
                    }
                    welcome -> WelcomeScreen {
                        entered = true
                        preferences.edit().putBoolean("welcome-complete", true).apply()
                        navigate(Screen.HOME)
                    }
                    else -> {
                        state.message?.let { Notice(it, vm::dismissMessage) }
                        if (state.storageError) Notice("Encrypted records are unavailable. Existing data has not been reset. Use the local clinical handoff pathway and request technical support.")
                        ValidationErrors(state.errors)
                        if (state.storageError && screen in setOf(Screen.RECORDS, Screen.REFERRALS,
                                Screen.ASSESSMENT_DETAIL, Screen.REFERRAL_DETAIL)) {
                            PageHeading("Records need attention.", "Their contents and delivery states cannot be verified on this device.")
                            Text("Existing encrypted records have not been deleted. Restore device access or request technical review. Use local clinical handoff procedures for urgent findings.",
                                style = MaterialTheme.typography.bodyLarge, color = C.Muted)
                            SecondaryAction("Open account and privacy", { navigate(Screen.ACCOUNT) })
                        } else when (screen) {
                            Screen.HOME -> HomeScreen(state, { navigate(Screen.INTAKE) }, { restart = true },
                                { navigate(Screen.REFERRALS) }, { navigate(Screen.RECORDS) }, vm::sync,
                                { openAssessment(it) }, ::openReferral)
                            Screen.INTAKE -> IntakeScreen(state, vm::edit) { navigate(Screen.VITALS) }
                            Screen.VITALS -> VitalsScreen(state, vm::edit) { navigate(Screen.SYMPTOMS) }
                            Screen.SYMPTOMS -> SymptomsScreen(state, vm::edit) { navigate(Screen.REVIEW) }
                            Screen.REVIEW -> {
                                AssessmentOutcome(draftAssessment, recorded = false)
                                val errors = state.draft.errors(draftAssessment)
                                ValidationErrors(errors)
                                PrimaryAction("Create offline referral", { vm.save(referral = true) }, enabled = enabled && errors.isEmpty(), icon = AppIcons.Referral)
                                SecondaryAction("Save assessment only", { vm.save(referral = false) }, enabled = enabled && errors.isEmpty())
                                TextButton({ navigate(Screen.INTAKE) }, Modifier.heightIn(min = 48.dp)) { Text("Edit assessment details") }
                                Text("Saved locally does not mean delivered. Use the immediate local escalation pathway for urgent findings.",
                                    style = MaterialTheme.typography.bodyMedium, color = C.Muted)
                                ResearchFootnote()
                            }
                            Screen.RECORDS -> RecordsScreen(state) { openAssessment(it) }
                            Screen.ASSESSMENT_DETAIL -> {
                                val saved = state.records.assessments.firstOrNull { it.assessment.id == selectedAssessmentId }
                                if (saved == null) MissingRecord(state.busy) { navigate(Screen.RECORDS) }
                                else {
                                    AssessmentOutcome(saved.assessment, recorded = true)
                                    val linked = state.records.referrals.filter { it.assessment.id == saved.assessment.id }
                                    if (linked.isEmpty()) {
                                        PrimaryAction("Create referral from this record", { vm.refer(saved.assessment) }, enabled = enabled, icon = AppIcons.Referral)
                                        TextButton({ deleteAssessmentId = saved.assessment.id }, enabled = !state.busy,
                                            modifier = Modifier.heightIn(min = 48.dp)) { Text("Delete device assessment", color = C.Urgent) }
                                    } else linked.forEach { referral ->
                                        SecondaryAction("View referral · ${referralLabel(referral.state)}", { openReferral(referral.id) })
                                    }
                                    ResearchFootnote()
                                }
                            }
                            Screen.REFERRALS -> ReferralsScreen(state, ::openReferral, vm::sync)
                            Screen.REFERRAL_DETAIL -> {
                                val referral = state.records.referrals.firstOrNull { it.id == selectedReferralId }
                                if (referral == null) MissingRecord(state.busy) { navigate(Screen.REFERRALS) }
                                else ReferralDetailScreen(referral, state.busy, vm::sync, { handoffId = referral.id },
                                    { retryId = referral.id }, { deleteReferralId = referral.id },
                                    { openAssessment(referral.assessment.id, Screen.REFERRAL_DETAIL) })
                            }
                            Screen.ACCOUNT -> AccountScreen(state, { navigate(Screen.SIGN_IN) }, vm::logout,
                                { navigate(Screen.PRIVACY) }, { navigate(Screen.LICENSES) })
                            Screen.SIGN_IN -> SignInScreen(state, vm::login) { navigate(Screen.HOME) }
                            Screen.PRIVACY -> PrivacyScreen(state.busy) { deleteAll = true }
                            Screen.LICENSES -> LicensesScreen()
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (restart) Confirmation("Start a different assessment?",
        "The unfinished form has not been saved. Starting again discards only these session entries; saved records remain unchanged.",
        "Start again", { vm.startNewAssessment(); restart = false; navigate(Screen.INTAKE) }, { restart = false })
    deleteReferralId?.let { id -> Confirmation("Delete this device referral?",
        "An unsent referral will not reach a clinician from this device. Use local handoff procedures first. Server records are unaffected.",
        "Delete device copy", { vm.deleteReferral(id); deleteReferralId = null; navigate(Screen.REFERRALS) }, { deleteReferralId = null }, true) }
    deleteAssessmentId?.let { id -> Confirmation("Delete this device assessment?",
        "This removes the unlinked local assessment. It does not remove any record already held by a receiving service.",
        "Delete assessment", { vm.deleteAssessment(id); deleteAssessmentId = null; navigate(Screen.RECORDS) }, { deleteAssessmentId = null }, true) }
    if (deleteAll) Confirmation("Delete all local records?",
        "This deletes local assessments, referrals and the device session. Unsent referrals will not be delivered. Arrange local clinical handoff first. Server records remain separately retained.",
        "Delete local records", { vm.deleteAll(); deleteAll = false }, { deleteAll = false }, true)
    retryId?.let { id -> Confirmation("Retry the unchanged referral?",
        "Confirm the connection, account, facility and server response have been reviewed. This retries the same immutable snapshot and identifier; it does not confirm delivery or acknowledgment.",
        "Retry referral", { vm.retryReferral(id); retryId = null }, { retryId = null }) }
    handoffId?.let { id ->
        val referral = state.records.referrals.firstOrNull { it.id == id }
        if (referral != null) AlertDialog(onDismissRequest = { handoffId = null }, containerColor = C.White,
            title = { Text("Offline handoff summary") },
            text = { Column(Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
                SelectionContainer { Text(HandoffSummary.render(referral.id, referral.assessment, referral.state),
                    style = MaterialTheme.typography.bodyMedium) }
            } }, confirmButton = { TextButton({ handoffId = null }, Modifier.heightIn(min = 48.dp)) { Text("Done") } })
    }
}

@Composable
private fun AppHeader(title: String?, online: Boolean, onBack: () -> Unit, onPause: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = if (title == null) 20.dp else 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (title != null) IconButton(onBack, Modifier.size(48.dp)) { AppIcon(AppIcons.Back, "Back", tint = C.Forest) }
        else BrandMark(Modifier.size(40.dp))
        Text(title ?: "VITALINK", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
            color = C.Forest, letterSpacing = if (title == null) 1.4.sp else 0.sp)
        if (onPause != null) IconButton(onPause, Modifier.size(48.dp)) { AppIcon(AppIcons.Close, "Pause assessment and return home") }
        else if (title == null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            AppIcon(if (online) AppIcons.Sync else AppIcons.Offline, modifier = Modifier.size(18.dp), tint = C.Muted)
            Text(if (online) "Online" else "Offline", style = MaterialTheme.typography.labelSmall, color = C.Muted)
        }
    }
    HorizontalDivider(color = C.Border)
}

@Composable
private fun BottomNavigation(screen: Screen, navigate: (Screen) -> Unit) {
    Surface(color = C.White) {
        Column {
            HorizontalDivider(color = C.Border)
            NavigationBar(containerColor = C.White, tonalElevation = 0.dp) {
                listOf(Triple(Screen.HOME, "Home", AppIcons.Home), Triple(Screen.RECORDS, "Records", AppIcons.Records),
                    Triple(Screen.REFERRALS, "Referrals", AppIcons.Referral), Triple(Screen.ACCOUNT, "Account", AppIcons.Account))
                    .forEach { (target, label, icon) ->
                        NavigationBarItem(selected = screen == target, onClick = { navigate(target) },
                            icon = { AppIcon(icon) }, label = { Text(label, style = MaterialTheme.typography.labelSmall, color = C.Forest) },
                            modifier = Modifier.heightIn(min = 56.dp),
                            colors = NavigationBarItemDefaults.colors(selectedIconColor = C.Forest, unselectedIconColor = C.Muted,
                                indicatorColor = C.PinkSurface, selectedTextColor = C.Forest, unselectedTextColor = C.Muted))
                    }
            }
        }
    }
}

@Composable
private fun MissingRecord(busy: Boolean, onBack: () -> Unit) {
    if (busy) CircularProgressIndicator(color = C.Forest)
    else {
        EmptyState("Record unavailable", "The local record may have been removed. Return to your records and check the latest available status.")
        SecondaryAction("Back to records", onBack)
    }
}

@Composable
private fun Confirmation(title: String, message: String, confirm: String, onConfirm: () -> Unit,
                         onDismiss: () -> Unit, destructive: Boolean = false) {
    AlertDialog(onDismissRequest = onDismiss, containerColor = C.White, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onConfirm, Modifier.heightIn(min = 48.dp)) {
            Text(confirm, color = if (destructive) C.Urgent else C.Forest)
        } }, dismissButton = { TextButton(onDismiss, Modifier.heightIn(min = 48.dp)) { Text("Cancel") } })
}
