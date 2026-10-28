package org.vitalink.app

import org.junit.Assert.*
import org.junit.Test
import org.vitalink.domain.*

class DraftValidationTest {
    @Test fun malformedInputCannotBeSavedAsMissingNormalMeasurement() {
        val draft = Draft("SYN-001", "not-a-number", "80", "32")
        val assessment = draft.assessment()
        assertTrue(draft.errors(assessment).contains("Systolic BP must be a whole number."))
        assertEquals(Urgency.REVIEW_REQUIRED, ClinicalRules.assess(assessment).urgency)
    }
    @Test fun severeBpStillEscalatesWhenOtherFieldIsMalformed() {
        val draft = Draft("SYN-001", "160", "invalid", "32")
        assertTrue(draft.errors(draft.assessment()).isNotEmpty())
        assertEquals(Urgency.URGENT, ClinicalRules.assess(draft.assessment()).urgency)
    }
    @Test fun blankFieldsRemainExplicitlyMissing() {
        val draft = Draft("SYN-001")
        val assessment = draft.assessment()
        assertTrue(draft.errors(assessment).isEmpty())
        assertNull(assessment.systolic); assertNull(assessment.diastolic); assertNull(assessment.gestationalWeeks)
        assertEquals(Urgency.REVIEW_REQUIRED, ClinicalRules.assess(assessment).urgency)
    }
}
