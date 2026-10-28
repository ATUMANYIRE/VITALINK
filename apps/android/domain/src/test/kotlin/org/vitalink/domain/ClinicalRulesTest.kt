package org.vitalink.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class ClinicalRulesTest {
    private val now = Instant.parse("2026-10-28T05:00:00Z")
    private fun a(s: Int? = 120, d: Int? = 80) = Assessment("611147a5-4073-491d-9227-3731f1f1a102", "SYN-001", now.toString(), s, d, 32, emptySet())

    @Test fun severeSystolicTriggersUrgentAssessment() = assertEquals(Urgency.URGENT, ClinicalRules.assess(160, 80, emptySet()).urgency)
    @Test fun severeDiastolicTriggersUrgentAssessment() = assertEquals(Urgency.URGENT, ClinicalRules.assess(120, 110, emptySet()).urgency)
    @Test fun exactSevereOrBoundaries159And160By109And110() {
        assertEquals(Urgency.REVIEW_REQUIRED, ClinicalRules.assess(159, 109, emptySet()).urgency)
        assertEquals(Urgency.URGENT, ClinicalRules.assess(160, 109, emptySet()).urgency)
        assertEquals(Urgency.URGENT, ClinicalRules.assess(159, 110, emptySet()).urgency)
        assertEquals(Urgency.URGENT, ClinicalRules.assess(160, 110, emptySet()).urgency)
    }
    @Test fun systolicBelowSevereRequiresReview() = assertEquals(Urgency.REVIEW_REQUIRED, ClinicalRules.assess(159, 80, emptySet()).urgency)
    @Test fun diastolicBelowSevereRequiresReview() = assertEquals(Urgency.REVIEW_REQUIRED, ClinicalRules.assess(130, 109, emptySet()).urgency)
    @Test fun elevatedSystolicBoundary() = assertEquals(listOf("ELEVATED_SYSTOLIC"), ClinicalRules.assess(140, 80, emptySet()).triggers)
    @Test fun elevatedDiastolicBoundary() = assertEquals(listOf("ELEVATED_DIASTOLIC"), ClinicalRules.assess(120, 90, emptySet()).triggers)
    @Test fun noTriggerNeverClaimsHealthy() { val f = ClinicalRules.assess(139, 89, emptySet()); assertEquals(Urgency.NO_TRIGGER, f.urgency); assertTrue(f.message.contains("does not establish health")) }
    @Test fun everyConcerningSymptomOverridesNormalBp() { Symptom.entries.forEach { assertEquals(it.name, Urgency.URGENT, ClinicalRules.assess(120, 80, setOf(it)).urgency) } }
    @Test fun missingBpIsReviewNotNormal() { assertEquals(Urgency.REVIEW_REQUIRED, ClinicalRules.assess(null, null, emptySet()).urgency) }
    @Test fun severeAvailableBpOverridesMissingOtherBp() { assertEquals(Urgency.URGENT, ClinicalRules.assess(160, null, emptySet()).urgency); assertEquals(Urgency.URGENT, ClinicalRules.assess(null, 110, emptySet()).urgency) }
    @Test fun concerningSymptomOverridesMissingBp() = assertEquals(Urgency.URGENT, ClinicalRules.assess(null, null, setOf(Symptom.SEIZURE)).urgency)
    @Test fun invalidOtherMeasurementDoesNotSuppressSevereBp() { val v = a(160, 20); assertTrue(AssessmentValidation.errors(v, now).isNotEmpty()); assertEquals(Urgency.URGENT, ClinicalRules.assess(v).urgency) }
    @Test fun invalidLowBpDoesNotReturnNoTrigger() = assertEquals(Urgency.REVIEW_REQUIRED, ClinicalRules.assess(20, 10, emptySet()).urgency)
    @Test fun invalidLargeBpStillWarnsBeforeCorrection() { assertTrue(AssessmentValidation.errors(a(999), now).isNotEmpty()); assertEquals(Urgency.URGENT, ClinicalRules.assess(a(999)).urgency) }
    @Test fun triggersAreDeterministic() { assertEquals(ClinicalRules.assess(170, 120, Symptom.entries.toSet()), ClinicalRules.assess(170, 120, Symptom.entries.reversed().toSet())) }
    @Test fun severeSystolicRetainsElevatedDiastolicTrigger() = assertEquals(setOf("SEVERE_SYSTOLIC", "ELEVATED_DIASTOLIC"), ClinicalRules.assess(160, 90, emptySet()).triggers.toSet())
    @Test fun severeSystolicRetainsMissingDiastolicTrigger() = assertEquals(setOf("SEVERE_SYSTOLIC", "MISSING_DIASTOLIC"), ClinicalRules.assess(160, null, emptySet()).triggers.toSet())
    @Test fun validAssessmentHasNoValidationErrors() = assertTrue(AssessmentValidation.errors(a(), now).isEmpty())
    @Test fun syntheticIdentifierRequired() = assertTrue(AssessmentValidation.errors(a().copy(patientId = "person-name"), now).isNotEmpty())
    @Test fun malformedUuidRejected() = assertTrue(AssessmentValidation.errors(a().copy(id = "1-1-1-1-1"), now).isNotEmpty())
    @Test fun invertedBpRejected() = assertTrue(AssessmentValidation.errors(a(100, 120), now).isNotEmpty())
    @Test fun equalBpRejected() = assertTrue(AssessmentValidation.errors(a(100, 100), now).isNotEmpty())
    @Test fun boundsAreInclusive() { assertTrue(AssessmentValidation.errors(a(50, 30), now).isEmpty()); assertTrue(AssessmentValidation.errors(a(300, 200), now).isEmpty()) }
    @Test fun outOfRangeBpRejected() { assertTrue(AssessmentValidation.errors(a(49, 29), now).size >= 2); assertTrue(AssessmentValidation.errors(a(301, 201), now).size >= 2) }
    @Test fun gestationBoundsValidated() { assertTrue(AssessmentValidation.errors(a().copy(gestationalWeeks = -1), now).isNotEmpty()); assertTrue(AssessmentValidation.errors(a().copy(gestationalWeeks = 46), now).isNotEmpty()) }
    @Test fun missingMeasurementsPreserved() { assertTrue(AssessmentValidation.errors(a(null, null).copy(gestationalWeeks = null), now).isEmpty()); assertEquals(2, ClinicalRules.assess(a(null, null)).missing.size) }
    @Test fun unknownRuleVersionRejected() = assertTrue(AssessmentValidation.errors(a().copy(ruleVersion = "future"), now).isNotEmpty())
    @Test fun unsupportedSourceRejected() = assertTrue(AssessmentValidation.errors(a().copy(source = "UNKNOWN"), now).isNotEmpty())
    @Test fun futureMeasurementRejected() = assertTrue(AssessmentValidation.errors(a().copy(measuredAt = now.plusSeconds(301).toString()), now).isNotEmpty())
    @Test fun toleratedClockSkewAccepted() = assertTrue(AssessmentValidation.errors(a().copy(measuredAt = now.plusSeconds(300).toString()), now).isEmpty())
    @Test fun invalidTimeRejected() = assertTrue(AssessmentValidation.errors(a().copy(measuredAt = "yesterday"), now).isNotEmpty())
    @Test fun unavailableModelHasNoProbability() { val i = RulesOnlyInference().infer(a()); assertEquals(InferenceState.UNAVAILABLE, i.state); assertNull(i.probability); assertNull(i.modelVersion) }
    @Test fun missingFeaturesCauseSafeAbstention() = assertEquals(InferenceState.ABSTAINED, RulesOnlyInference().infer(a(null, 80)).state)
    @Test fun urgentGuidanceNeverWaitsForNetwork() { assertTrue(ClinicalRules.assess(a(160)).message.contains("Do not wait for synchronization")) }
}
