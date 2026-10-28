package org.vitalink.domain

import org.junit.Assert.*
import org.junit.Test

class ReferralTest {
    private val a = Assessment("611147a5-4073-491d-9227-3731f1f1a102", "SYN-001", "2026-10-28T05:00:00Z", 160, 90, 32, emptySet())
    private val receipt = ServerReceipt("611147a5-4073-491d-9227-3731f1f1a101", "demo-facility", a, ReferralState.SERVER_RECEIVED, Urgency.URGENT, listOf("SEVERE_SYSTOLIC", "ELEVATED_DIASTOLIC"), "2026-10-28T05:01:00Z", null, null)
    private fun valid(r: ServerReceipt = receipt) = r.validate(receipt.id, receipt.facilityId, a)

    @Test fun validReceiptConfirmsServerDelivery() = valid()
    @Test(expected = IllegalArgumentException::class) fun wrongIdentifierCannotConfirmDelivery() = valid(receipt.copy(id = "different"))
    @Test(expected = IllegalArgumentException::class) fun wrongFacilityCannotConfirmDelivery() = valid(receipt.copy(facilityId = "other"))
    @Test(expected = IllegalArgumentException::class) fun changedSnapshotCannotConfirmDelivery() = valid(receipt.copy(assessment = a.copy(systolic = 120)))
    @Test(expected = IllegalArgumentException::class) fun lowServerUrgencyCannotSuppressRules() = valid(receipt.copy(urgency = Urgency.NO_TRIGGER))
    @Test(expected = IllegalArgumentException::class) fun duplicateSafetyTriggersRejectMalformedReceipt() = valid(receipt.copy(ruleTriggers = receipt.ruleTriggers + "SEVERE_SYSTOLIC"))
    @Test(expected = IllegalArgumentException::class) fun inFlightIsNotDelivery() = valid(receipt.copy(state = ReferralState.SENT))
    @Test(expected = IllegalArgumentException::class) fun acknowledgmentRequiresClinicianEvidence() = valid(receipt.copy(state = ReferralState.ACKNOWLEDGED))
    @Test fun explicitClinicianAcknowledgmentAccepted() = valid(receipt.copy(state = ReferralState.ACKNOWLEDGED, acknowledgedAt = "2026-10-28T05:02:00Z", acknowledgedBy = "doctor"))
    @Test(expected = IllegalArgumentException::class) fun acknowledgmentBeforeReceiptRejected() = valid(receipt.copy(state = ReferralState.ACKNOWLEDGED, acknowledgedAt = "2026-10-28T05:00:00Z", acknowledgedBy = "doctor"))
    @Test(expected = IllegalArgumentException::class) fun contradictoryReceiptRejected() = valid(receipt.copy(acknowledgedBy = "doctor"))
    @Test fun normalLifecycleTransitions() { ReferralState.entries.zipWithNext().forEach { (from, to) -> ReferralTransitions.requireAllowed(from, to) } }
    @Test fun replayedSameStateAllowed() { ReferralState.entries.forEach { ReferralTransitions.requireAllowed(it, it) } }
    @Test(expected = IllegalArgumentException::class) fun localQueueCannotAcknowledge() = ReferralTransitions.requireAllowed(ReferralState.PENDING_SYNC, ReferralState.ACKNOWLEDGED)
    @Test(expected = IllegalArgumentException::class) fun closedCannotReopen() = ReferralTransitions.requireAllowed(ReferralState.CLOSED, ReferralState.PENDING_SYNC)
    @Test(expected = IllegalArgumentException::class) fun deliveredCannotRegress() = ReferralTransitions.requireAllowed(ReferralState.SERVER_RECEIVED, ReferralState.SENT)
    @Test fun lostResponseReplayMayReturnAcknowledged() = ReferralTransitions.requireAllowed(ReferralState.SENT, ReferralState.ACKNOWLEDGED)
    @Test fun expiredLeaseRecovered() { assertTrue(RetryPolicy.leaseExpired(ReferralState.SENT, 100, 101)); assertTrue(RetryPolicy.leaseExpired(ReferralState.SENT, null, 101)) }
    @Test fun activeLeaseNotRecovered() = assertFalse(RetryPolicy.leaseExpired(ReferralState.SENT, 102, 101))
    @Test fun deliveredNotRecoveredAsRetry() = assertFalse(RetryPolicy.leaseExpired(ReferralState.SERVER_RECEIVED, 100, 101))
    @Test fun retriesUseBoundedJitterAndExponentialDelay() { assertEquals(15_000L, RetryPolicy.delayMillis(1, 0.0)); assertEquals(30_000L, RetryPolicy.delayMillis(1, 1.0)); assertEquals(60_000L, RetryPolicy.delayMillis(2, 1.0)); (1..100).forEach { assertTrue(RetryPolicy.delayMillis(it, 1.0) <= RetryPolicy.MAX_DELAY_MILLIS) } }
    @Test fun authenticationValidationConflictErrorsAreTerminal() { listOf(400, 401, 403, 404, 409, 422).forEach { assertFalse(RetryPolicy.isRetryable(it)) } }
    @Test fun transientErrorsRetry() { listOf(408, 429, 500, 502, 503, 504).forEach { assertTrue(RetryPolicy.isRetryable(it)) } }
    @Test fun offlineHandoffNeverClaimsDelivery() { val s = HandoffSummary.render(receipt.id, a, ReferralState.PENDING_SYNC); assertTrue(s.contains("NOT CONFIRMED DELIVERED")); assertTrue(s.contains("Do not wait")); assertTrue(s.contains(receipt.id)) }
}
