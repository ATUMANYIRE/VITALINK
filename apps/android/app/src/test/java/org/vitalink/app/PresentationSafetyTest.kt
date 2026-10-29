package org.vitalink.app

import org.junit.Assert.*
import org.junit.Test
import org.vitalink.app.ui.*
import org.vitalink.domain.ReferralState

class PresentationSafetyTest {
    private val at = "2026-10-28T10:00:00Z"

    @Test fun draftPendingAndTransmissionNeverConfirmServerDelivery() {
        listOf(ReferralState.DRAFT, ReferralState.PENDING_SYNC, ReferralState.SENT).forEach {
            assertFalse("$it is not delivery confirmation", isDeliveryConfirmed(it))
            assertFalse(hasClinicalAcknowledgment(it, at, "doctor"))
        }
    }

    @Test fun serverReceiptIsSeparateFromClinicalAcknowledgment() {
        assertTrue(isDeliveryConfirmed(ReferralState.SERVER_RECEIVED))
        assertFalse(hasClinicalAcknowledgment(ReferralState.SERVER_RECEIVED, at, "doctor"))
    }

    @Test fun acknowledgmentRequiresAnActorAndParseableEvidenceTime() {
        assertTrue(hasClinicalAcknowledgment(ReferralState.ACKNOWLEDGED, at, "doctor"))
        for ((time, actor) in listOf(null to "doctor", at to null, "invalid" to "doctor", at to "")) {
            assertFalse(hasClinicalAcknowledgment(ReferralState.ACKNOWLEDGED, time, actor))
        }
    }

    @Test fun closedStateDoesNotInventMissingClinicalAcknowledgment() {
        assertTrue(isDeliveryConfirmed(ReferralState.CLOSED))
        assertFalse(hasClinicalAcknowledgment(ReferralState.CLOSED, null, null))
        assertTrue(hasClinicalAcknowledgment(ReferralState.CLOSED, at, "doctor"))
    }

    @Test fun unrecognizedFailureMetadataIsNeverEchoedToTheScreen() {
        val unsafeError = "private-patient-payload-or-token"
        assertFalse(readableSyncError(unsafeError).contains(unsafeError))
        assertTrue(readableSyncError(unsafeError).contains("not confirmed"))
    }

    @Test fun invalidTimeNeverBecomesAnInventedCurrentTimestamp() {
        assertEquals("Timestamp unavailable", recordedTime("not-a-timestamp"))
    }
}
