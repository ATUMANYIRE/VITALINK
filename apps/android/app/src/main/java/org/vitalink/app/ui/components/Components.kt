package org.vitalink.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.vitalink.app.R
import org.vitalink.app.ui.theme.VitalinkColors as C
import org.vitalink.domain.Findings
import org.vitalink.domain.Urgency

object AppIcons {
    val Home = R.drawable.ic_home
    val Records = R.drawable.ic_folder_open
    val Assessment = R.drawable.ic_assignment
    val Account = R.drawable.ic_person
    val Back = R.drawable.ic_arrow_back
    val Forward = R.drawable.ic_arrow_forward
    val Chevron = R.drawable.ic_chevron_right
    val Close = R.drawable.ic_close
    val Confirmed = R.drawable.ic_check_circle
    val Warning = R.drawable.ic_warning
    val Offline = R.drawable.ic_wifi_off
    val Sync = R.drawable.ic_sync
    val Lock = R.drawable.ic_lock
    val Delete = R.drawable.ic_delete
    val Logout = R.drawable.ic_logout
    val Search = R.drawable.ic_search
    val Visible = R.drawable.ic_visibility
    val Hidden = R.drawable.ic_visibility_off
    val Info = R.drawable.ic_info
    val Received = R.drawable.ic_cloud_done
    val Add = R.drawable.ic_add
    val Time = R.drawable.ic_schedule
    val Referral = R.drawable.ic_description
}

@Composable
fun AppIcon(@DrawableRes source: Int, description: String? = null, modifier: Modifier = Modifier,
            tint: Color = LocalContentColor.current) {
    Icon(painterResource(source), description, modifier.size(24.dp), tint = tint)
}

@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.protective_embrace), "VITALINK Protective Embrace", modifier)
}

@Composable
fun PrimaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                  enabled: Boolean = true, @DrawableRes icon: Int? = AppIcons.Forward) {
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 58.dp), enabled = enabled,
        shape = RoundedCornerShape(18.dp), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 17.dp),
        colors = ButtonDefaults.buttonColors(containerColor = C.Forest, contentColor = C.White)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current)
        if (icon != null) { Spacer(Modifier.width(12.dp)); AppIcon(icon) }
    }
}

@Composable
fun SecondaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                    enabled: Boolean = true, destructive: Boolean = false) {
    OutlinedButton(onClick, modifier.fillMaxWidth().heightIn(min = 56.dp), enabled = enabled,
        shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, if (destructive) C.Urgent else C.Border),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 15.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (destructive) C.Urgent else C.Forest)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = LocalContentColor.current)
    }
}

@Composable
fun PageHeading(title: String, subtitle: String? = null, eyebrow: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        eyebrow?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = C.Muted, letterSpacing = .8.sp) }
        Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = C.Muted) }
    }
}

@Composable
fun SectionHeading(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleMedium)
        if (action != null) TextButton(onAction, Modifier.heightIn(min = 48.dp)) {
            Text(action, style = MaterialTheme.typography.labelMedium, color = C.Forest)
        }
    }
}

@Composable
fun OutlinePanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = C.White,
        border = BorderStroke(1.dp, C.Border), shadowElevation = 0.dp, tonalElevation = 0.dp) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
fun ClinicalAlert(findings: Findings, modifier: Modifier = Modifier, includeNoTrigger: Boolean = false, compact: Boolean = false) {
    if (findings.urgency == Urgency.NO_TRIGGER && !includeNoTrigger) return
    val urgent = findings.urgency == Urgency.URGENT
    val color = when (findings.urgency) { Urgency.URGENT -> C.Urgent; Urgency.REVIEW_REQUIRED -> C.Review; else -> C.Forest }
    val surface = when (findings.urgency) { Urgency.URGENT -> C.UrgentSurface; Urgency.REVIEW_REQUIRED -> C.ReviewSurface; else -> C.White }
    Surface(modifier.fillMaxWidth().semantics { liveRegion = if (urgent) LiveRegionMode.Assertive else LiveRegionMode.Polite },
        color = surface, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, color.copy(alpha = .25f))) {
        Row(Modifier.padding(18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(if (urgent) AppIcons.Warning else AppIcons.Info, tint = color)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(when (findings.urgency) {
                    Urgency.URGENT -> "Urgent clinical assessment"
                    Urgency.REVIEW_REQUIRED -> "Clinical review needed"
                    Urgency.NO_TRIGGER -> "No configured rule triggered"
                }, style = MaterialTheme.typography.titleSmall, color = color)
                Text(if (compact && urgent) "Escalate now under local procedures. Do not wait for synchronization." else findings.message,
                    style = MaterialTheme.typography.bodyMedium, color = color)
            }
        }
    }
}

@Composable
fun Notice(message: String, onDismiss: (() -> Unit)? = null) {
    OutlinePanel(Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppIcon(AppIcons.Info, tint = C.Forest)
            Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        }
        if (onDismiss != null) TextButton(onDismiss, Modifier.align(Alignment.End).heightIn(min = 48.dp)) { Text("Dismiss") }
    }
}

@Composable
fun ValidationErrors(errors: List<String>) {
    if (errors.isEmpty()) return
    Surface(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        color = C.UrgentSurface, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Check these details", style = MaterialTheme.typography.titleSmall, color = C.Urgent)
            errors.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = C.Urgent) }
        }
    }
}

@Composable
fun EmptyState(title: String, detail: String, @DrawableRes icon: Int = AppIcons.Records) {
    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AppIcon(icon, modifier = Modifier.size(32.dp), tint = C.Forest)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(detail, style = MaterialTheme.typography.bodyLarge, color = C.Muted)
    }
}

@Composable
fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = C.Muted)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun ResearchFootnote() {
    Text("Research prototype · synthetic records only. No diagnosis or medication advice.",
        style = MaterialTheme.typography.bodySmall, color = C.Muted)
}
