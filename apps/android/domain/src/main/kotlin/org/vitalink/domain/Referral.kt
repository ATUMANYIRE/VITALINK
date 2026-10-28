package org.vitalink.domain

import kotlin.math.min

enum class ReferralState { DRAFT, PENDING_SYNC, SENT, SERVER_RECEIVED, ACKNOWLEDGED, CLOSED }

object ReferralTransitions {
    private val transitions = mapOf(
        ReferralState.DRAFT to setOf(ReferralState.PENDING_SYNC),
        ReferralState.PENDING_SYNC to setOf(ReferralState.SENT),
        ReferralState.SENT to setOf(ReferralState.PENDING_SYNC, ReferralState.SERVER_RECEIVED, ReferralState.ACKNOWLEDGED, ReferralState.CLOSED),
        ReferralState.SERVER_RECEIVED to setOf(ReferralState.ACKNOWLEDGED, ReferralState.CLOSED),
        ReferralState.ACKNOWLEDGED to setOf(ReferralState.CLOSED),
        ReferralState.CLOSED to emptySet(),
    )
    fun requireAllowed(from: ReferralState, to: ReferralState) {
        require(from == to || to in transitions.getValue(from)) { "Invalid referral transition: $from to $to" }
    }
}

data class ServerReceipt(
    val id: String, val facilityId: String, val assessment: Assessment,
    val state: ReferralState, val urgency: Urgency, val ruleTriggers: List<String>,
    val receivedAt: String, val acknowledgedAt: String?, val acknowledgedBy: String?,
) {
    fun validate(expectedId: String, expectedFacility: String, expectedAssessment: Assessment) {
        require(id == expectedId && facilityId == expectedFacility) { "Response identity mismatch" }
        require(assessment == expectedAssessment) { "Response snapshot mismatch" }
        require(state in setOf(ReferralState.SERVER_RECEIVED, ReferralState.ACKNOWLEDGED, ReferralState.CLOSED)) { "Unconfirmed delivery" }
        require(ruleTriggers.distinct().size == ruleTriggers.size) { "Duplicate safety trigger" }
        val findings = ClinicalRules.assess(assessment)
        require(urgency == findings.urgency && ruleTriggers.toSet() == findings.triggers.toSet()) { "Response safety mismatch" }
        java.time.Instant.parse(receivedAt)
        if (state == ReferralState.ACKNOWLEDGED || state == ReferralState.CLOSED) {
            require(!acknowledgedBy.isNullOrBlank() && !acknowledgedAt.isNullOrBlank()) { "Missing acknowledgment evidence" }
            require(java.time.Instant.parse(acknowledgedAt) >= java.time.Instant.parse(receivedAt)) { "Invalid acknowledgment time" }
        } else {
            require(acknowledgedAt == null && acknowledgedBy == null) { "Contradictory acknowledgment" }
        }
    }
}

object RetryPolicy {
    const val LEASE_MILLIS = 120_000L
    const val MAX_DELAY_MILLIS = 3_600_000L
    /** Equal jitter: 50–100% of capped exponential delay. */
    fun delayMillis(attempt: Int, randomUnit: Double): Long {
        require(attempt >= 1 && randomUnit in 0.0..1.0)
        val cap = min(MAX_DELAY_MILLIS, 30_000L * (1L shl min(attempt - 1, 7)))
        return (cap / 2 + cap / 2 * randomUnit).toLong()
    }
    fun isRetryable(httpCode: Int) = httpCode == 408 || httpCode == 429 || httpCode in 500..599
    fun leaseExpired(state: ReferralState, leaseUntil: Long?, now: Long) =
        state == ReferralState.SENT && (leaseUntil == null || leaseUntil <= now)
}

object HandoffSummary {
    fun render(id: String, assessment: Assessment, state: ReferralState): String {
        val f = ClinicalRules.assess(assessment)
        return """
            VITALINK RESEARCH — SYNTHETIC RECORD
            Referral: $id
            Patient: ${assessment.patientId}
            Measured: ${assessment.measuredAt} (${assessment.source})
            BP: ${assessment.systolic ?: "missing"}/${assessment.diastolic ?: "missing"} mmHg
            Gestation: ${assessment.gestationalWeeks ?: "missing"} weeks
            Symptoms: ${assessment.symptoms.joinToString { it.name }.ifBlank { "none reported" }}
            Findings: ${f.urgency}; ${f.triggers.joinToString().ifBlank { "no configured trigger" }}
            Rules: ${assessment.ruleVersion}
            Delivery state: $state
            ${if (state in setOf(ReferralState.DRAFT, ReferralState.PENDING_SYNC, ReferralState.SENT)) "NOT CONFIRMED DELIVERED. Contact the receiving clinician directly using local escalation procedures." else "Server delivery is separate from clinical acknowledgment."}
            ${f.message}
            Research prototype; no diagnosis or medication advice.
        """.trimIndent()
    }
}
