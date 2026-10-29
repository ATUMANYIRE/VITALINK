package org.vitalink.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import org.vitalink.app.BuildConfig
import org.vitalink.app.UiState
import org.vitalink.app.ui.components.*
import org.vitalink.app.ui.theme.VitalinkColors as C
import org.vitalink.domain.ClinicalRules

@Composable
fun AccountScreen(state: UiState, onSignIn: () -> Unit, onSignOut: () -> Unit,
                  onPrivacy: () -> Unit, onLicenses: () -> Unit) {
    PageHeading("Your care workspace.", "Account, facility and device preferences.", "ACCOUNT")
    val session = state.session
    OutlinePanel {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(AppIcons.Account, tint = C.Forest)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(session?.username ?: "Working without sign-in", style = MaterialTheme.typography.titleLarge)
                Text(if (session == null) "Offline assessments remain available" else "Authorized health-worker account",
                    style = MaterialTheme.typography.bodyMedium, color = C.Muted)
            }
        }
        if (session != null) {
            DetailLine("Facility", session.facilityId)
            DetailLine("Role", when (session.role) {
                "ADMIN" -> "Administrator"
                "DOCTOR" -> "Clinician"
                "HEALTH_WORKER" -> "Health worker"
                else -> "Authorized account"
            })
            TextButton(onSignOut, Modifier.heightIn(min = 48.dp), enabled = !state.busy) {
                AppIcon(AppIcons.Logout); Spacer(Modifier.width(8.dp)); Text("Sign out")
            }
        } else {
            Text("Sign in to synchronize referrals with your authorized facility. A local referral does not notify a clinician until server receipt is confirmed.",
                style = MaterialTheme.typography.bodyMedium, color = C.Muted)
            PrimaryAction("Sign in", onSignIn, enabled = !state.busy, icon = AppIcons.Lock)
        }
    }
    SectionHeading("Device and privacy")
    AccountLink("Privacy and local records", "Retention, deletion and handoff safeguards", AppIcons.Lock, onPrivacy)
    AccountLink("Fonts and icon licenses", "Bundled resources available offline", AppIcons.Info, onLicenses)
    SectionHeading("Clinical decision support")
    Text("Rules-only research mode", style = MaterialTheme.typography.titleMedium, color = C.Forest)
    Text("No validated predictive model is installed. Clinical warning signs and blood-pressure rules remain authoritative.",
        style = MaterialTheme.typography.bodyLarge, color = C.Muted)
    DetailLine("Clinical rule version", ClinicalRules.VERSION)
    OutlinePanel {
        Text(if (state.online) "Network available" else "Offline mode", style = MaterialTheme.typography.titleSmall)
        Text("Network status is not proof of server connectivity. Each referral records its own delivery and acknowledgment state.",
            style = MaterialTheme.typography.bodyMedium, color = C.Muted)
        if (BuildConfig.DEBUG) DetailLine("Development service address", BuildConfig.API_BASE_URL)
    }
    ResearchFootnote()
}

@Composable
private fun AccountLink(title: String, detail: String, icon: Int, onClick: () -> Unit) {
    Surface(onClick, color = C.White, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(vertical = 18.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AppIcon(icon, tint = C.Forest)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = C.Muted)
            }
            AppIcon(AppIcons.Chevron, tint = C.Muted)
        }
    }
    HorizontalDivider(color = C.Border)
}

@Composable
fun SignInScreen(state: UiState, onLogin: (String, String) -> Unit, onOffline: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    fun submit() {
        attempted = true
        if (username.isNotBlank() && password.isNotBlank()) {
            focus.clearFocus()
            onLogin(username.trim(), password)
            password = ""
        }
    }
    BrandMark(Modifier.size(76.dp))
    PageHeading("Connect your facility.", "Sign in with the health-worker account authorized for your receiving facility.")
    OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("Username") },
        singleLine = true, isError = attempted && username.isBlank(),
        supportingText = { if (attempted && username.isBlank()) Text("Enter your username.") },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), shape = RoundedCornerShape(16.dp))
    OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Password") },
        singleLine = true, isError = attempted && password.isBlank(),
        supportingText = { if (attempted && password.isBlank()) Text("Enter your password.") },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { IconButton(onClick = { visible = !visible }, modifier = Modifier.size(48.dp)) {
            AppIcon(if (visible) AppIcons.Hidden else AppIcons.Visible, if (visible) "Hide password" else "Show password")
        } }, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (!state.busy) submit() }), shape = RoundedCornerShape(16.dp))
    PrimaryAction(if (state.busy) "Signing in…" else "Sign in to facility", { submit() }, enabled = !state.busy, icon = AppIcons.Lock)
    SecondaryAction("Continue working offline", onOffline, enabled = !state.busy)
    Text("Offline assessment does not require sign-in. Synchronization requires a valid account and a verified server response.",
        style = MaterialTheme.typography.bodyMedium, color = C.Muted)
    ResearchFootnote()
}

@Composable
fun PrivacyScreen(busy: Boolean, onDeleteAll: () -> Unit) {
    PageHeading("Care for the record, too.", "Understand what stays on this device.", "PRIVACY")
    OutlinePanel {
        AppIcon(AppIcons.Lock, tint = C.Forest)
        Text("Encrypted clinical payloads", style = MaterialTheme.typography.titleMedium)
        Text("Assessment observations, referral snapshots and account tokens are encrypted using Android Keystore-backed protection. Opaque identifiers and delivery metadata remain visible for scheduling.",
            style = MaterialTheme.typography.bodyLarge, color = C.Muted)
        Text("Device backup is disabled. Key loss cannot be repaired by silently deleting committed records.",
            style = MaterialTheme.typography.bodyMedium, color = C.Muted)
    }
    SectionHeading("Local retention")
    Text("Closed referrals and unlinked assessments are eligible for local removal after 30 days. Unsent referrals are not automatically purged.",
        style = MaterialTheme.typography.bodyLarge)
    SectionHeading("Before deleting")
    Text("Deleting an unsent referral stops its delivery from this device. Use local clinician handoff procedures first. Removing a device copy does not delete server records or guarantee secure erasure of flash storage.",
        style = MaterialTheme.typography.bodyLarge, color = C.Muted)
    SecondaryAction("Delete local records and sign out", onDeleteAll, enabled = !busy, destructive = true)
    ResearchFootnote()
}

@Composable
fun LicensesScreen() {
    val context = LocalContext.current
    var selected by remember { mutableStateOf<String?>(null) }
    PageHeading("Made to work offline.", "Bundled typography and vector icon acknowledgments.", "OPEN-SOURCE RESOURCES")
    val resources = listOf(
        Triple("DM Sans", "SIL Open Font License 1.1 · Google Fonts distribution", "dm-sans-OFL.txt"),
        Triple("Fraunces", "SIL Open Font License 1.1 · Google Fonts distribution", "fraunces-OFL.txt"),
        Triple("Material Symbols Rounded", "Apache License 2.0 · Google Material Design Icons", "material-symbols-APACHE.txt"),
    )
    resources.forEach { (name, description, file) ->
        OutlinePanel {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = C.Muted)
            TextButton(onClick = { selected = if (selected == file) null else file }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (selected == file) "Close license" else "Read license")
            }
            if (selected == file) {
                val text = remember(file) { context.assets.open("licenses/$file").bufferedReader().use { it.readText() } }
                SelectionContainer { Text(text, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
    Text("The Protective Embrace mark uses the approved VITALINK geometry. Reference artwork and photographs are not bundled in the application.",
        style = MaterialTheme.typography.bodyMedium, color = C.Muted)
}
