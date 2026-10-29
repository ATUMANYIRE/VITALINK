package org.vitalink.app.ui

import org.vitalink.domain.ReferralState
import org.vitalink.domain.Symptom
import org.vitalink.domain.Urgency
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Presentation of existing states only. These functions never advance workflow state. */
fun referralLabel(state: ReferralState): String = when (state) {
    ReferralState.DRAFT -> "Draft"
    ReferralState.PENDING_SYNC -> "Pending sync"
    ReferralState.SENT -> "Sending attempt"
    ReferralState.SERVER_RECEIVED -> "Server received"
    ReferralState.ACKNOWLEDGED -> "Acknowledged"
    ReferralState.CLOSED -> "Closed"
}

fun isDeliveryConfirmed(state: ReferralState) = state in setOf(
    ReferralState.SERVER_RECEIVED, ReferralState.ACKNOWLEDGED, ReferralState.CLOSED)

fun hasClinicalAcknowledgment(state: ReferralState, at: String?, actor: String?): Boolean =
    state in setOf(ReferralState.ACKNOWLEDGED, ReferralState.CLOSED) &&
        !actor.isNullOrBlank() && !at.isNullOrBlank() && runCatching { Instant.parse(at) }.isSuccess

fun deliveryExplanation(state: ReferralState): String = when (state) {
    ReferralState.DRAFT -> "Not saved or delivered. Finish and save the assessment."
    ReferralState.PENDING_SYNC -> "Saved on this device. Delivery to the receiving service is not confirmed."
    ReferralState.SENT -> "A transmission attempt is in progress. Delivery is not yet confirmed."
    ReferralState.SERVER_RECEIVED -> "The server received this referral. A clinician has not yet acknowledged it."
    ReferralState.ACKNOWLEDGED -> "A clinician explicitly acknowledged the referral. This does not establish that care occurred."
    ReferralState.CLOSED -> "Referral coordination is closed. This does not establish treatment or recovery."
}

fun urgencyLabel(urgency: Urgency) = when (urgency) {
    Urgency.URGENT -> "Urgent assessment"
    Urgency.REVIEW_REQUIRED -> "Clinical review"
    Urgency.NO_TRIGGER -> "No configured trigger"
}

fun symptomLabel(symptom: Symptom): String = when (symptom) {
    Symptom.SEVERE_HEADACHE -> "Severe headache"
    Symptom.VISUAL_DISTURBANCE -> "Visual disturbance"
    Symptom.UPPER_ABDOMINAL_PAIN -> "Upper abdominal pain"
    Symptom.BREATHLESSNESS -> "Breathlessness"
    Symptom.SEIZURE -> "Seizure"
    Symptom.HEAVY_BLEEDING -> "Heavy bleeding"
    Symptom.REDUCED_FETAL_MOVEMENT -> "Reduced fetal movement"
}

fun triggerLabel(trigger: String): String = when (trigger) {
    "SEVERE_SYSTOLIC" -> "Systolic BP at or above 160 mmHg"
    "SEVERE_DIASTOLIC" -> "Diastolic BP at or above 110 mmHg"
    "ELEVATED_SYSTOLIC" -> "Systolic BP at or above 140 mmHg"
    "ELEVATED_DIASTOLIC" -> "Diastolic BP at or above 90 mmHg"
    "MISSING_SYSTOLIC" -> "Systolic BP not recorded"
    "MISSING_DIASTOLIC" -> "Diastolic BP not recorded"
    "INVALID_SYSTOLIC" -> "Systolic BP needs correction"
    "INVALID_DIASTOLIC" -> "Diastolic BP needs correction"
    "INVALID_BP_ORDER" -> "Blood pressure values need verification"
    else -> if (trigger.startsWith("SYMPTOM_")) {
        runCatching { symptomLabel(Symptom.valueOf(trigger.removePrefix("SYMPTOM_"))) }.getOrDefault("Reported warning sign")
    } else "Clinical input requires review"
}

fun recordedTime(raw: String): String = runCatching {
    DateTimeFormatter.ofPattern("d MMM yyyy · HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(raw))
}.getOrDefault("Timestamp unavailable")

fun readableSyncError(code: String?): String = when {
    code == null -> "No transmission error recorded."
    code == "AUTH_REQUIRED" || code == "HTTP_401" -> "Sign in again before retrying delivery."
    code == "NETWORK_FAILURE" -> "Connection interrupted. The same referral will be retried."
    code == "INTERRUPTED_ATTEMPT" -> "An interrupted attempt was recovered. Delivery remains unconfirmed."
    code == "HTTP_409" -> "The server reported a snapshot conflict. Request technical review before retrying."
    code == "INVALID_RESPONSE" -> "The server response could not be verified. Request technical review."
    code == "HTTP_403" -> "The account or facility cannot submit this referral. Check the authorized account."
    else -> "Delivery was not confirmed. Review the account, connection and referral before retrying."
}
