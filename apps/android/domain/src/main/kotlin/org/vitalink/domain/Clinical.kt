package org.vitalink.domain

import java.time.Instant
import java.util.UUID

enum class Symptom {
    SEVERE_HEADACHE, VISUAL_DISTURBANCE, UPPER_ABDOMINAL_PAIN, BREATHLESSNESS,
    SEIZURE, HEAVY_BLEEDING, REDUCED_FETAL_MOVEMENT
}

enum class Urgency { URGENT, REVIEW_REQUIRED, NO_TRIGGER }

data class Assessment(
    val id: String,
    val patientId: String,
    val measuredAt: String,
    val systolic: Int?,
    val diastolic: Int?,
    val gestationalWeeks: Int?,
    val symptoms: Set<Symptom>,
    val source: String = "MANUAL",
    val ruleVersion: String = ClinicalRules.VERSION,
)

data class Findings(val urgency: Urgency, val triggers: List<String>, val missing: List<String>) {
    val message: String get() = when (urgency) {
        Urgency.URGENT -> "Urgent clinical assessment needed now. Follow the local emergency escalation pathway; contact the receiving clinician or emergency service directly. Do not wait for synchronization."
        Urgency.REVIEW_REQUIRED -> "Clinical review is needed. Check missing measurements and follow local referral guidance."
        Urgency.NO_TRIGGER -> "No configured rule triggered. This does not establish health or exclude complications. Seek clinical review for any concern."
    }
}

/** Research rules, not diagnosis. Review and version changes with a clinician. */
object ClinicalRules {
    const val VERSION = "vitalink-rules-1.0.0"

    fun assess(systolic: Int?, diastolic: Int?, symptoms: Set<Symptom>): Findings {
        val urgent = buildList {
            if (systolic != null && systolic >= 160) add("SEVERE_SYSTOLIC")
            if (diastolic != null && diastolic >= 110) add("SEVERE_DIASTOLIC")
            symptoms.sortedBy { it.name }.forEach { add("SYMPTOM_${it.name}") }
        }
        val missing = buildList {
            if (systolic == null) add("MISSING_SYSTOLIC")
            if (diastolic == null) add("MISSING_DIASTOLIC")
        }
        val review = buildList {
            if (systolic != null && systolic !in 50..300) add("INVALID_SYSTOLIC")
            if (diastolic != null && diastolic !in 30..200) add("INVALID_DIASTOLIC")
            if (systolic != null && diastolic != null && systolic <= diastolic) add("INVALID_BP_ORDER")
            if (systolic != null && systolic in 140..159) add("ELEVATED_SYSTOLIC")
            if (diastolic != null && diastolic in 90..109) add("ELEVATED_DIASTOLIC")
            addAll(missing)
        }
        val urgency = when { urgent.isNotEmpty() -> Urgency.URGENT; review.isNotEmpty() -> Urgency.REVIEW_REQUIRED; else -> Urgency.NO_TRIGGER }
        return Findings(urgency, urgent + review, missing)
    }

    fun assess(assessment: Assessment) = assess(assessment.systolic, assessment.diastolic, assessment.symptoms)
}

object AssessmentValidation {
    fun errors(a: Assessment, now: Instant = Instant.now()): List<String> = buildList {
        if (!isUuid(a.id)) add("Assessment identifier must be a UUID.")
        if (!Regex("SYN-[A-Za-z0-9-]{1,40}").matches(a.patientId)) add("Use a synthetic patient identifier such as SYN-001.")
        if (a.systolic != null && a.systolic !in 50..300) add("Systolic BP must be 50–300 mmHg.")
        if (a.diastolic != null && a.diastolic !in 30..200) add("Diastolic BP must be 30–200 mmHg.")
        if (a.systolic != null && a.diastolic != null && a.systolic <= a.diastolic) add("Systolic BP must exceed diastolic BP. Verify the measurement.")
        if (a.gestationalWeeks != null && a.gestationalWeeks !in 0..45) add("Gestational age must be 0–45 weeks.")
        if (a.source !in setOf("MANUAL", "DEVICE")) add("Unsupported measurement source.")
        if (a.ruleVersion != ClinicalRules.VERSION) add("Unsupported clinical rule version.")
        val measured = runCatching { Instant.parse(a.measuredAt) }.getOrNull()
        if (measured == null || measured > now.plusSeconds(300)) add("Measurement time is invalid or more than five minutes in the future.")
    }

    private fun isUuid(value: String): Boolean = runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false)
}

enum class InferenceState { UNAVAILABLE, ABSTAINED }
data class ResearchInference(
    val state: InferenceState,
    val modelVersion: String? = null,
    val probability: Double? = null,
    val explanation: String = "Rules-only mode. No validated predictive model is available. Clinical rules remain authoritative.",
)
fun interface EdgeInference { fun infer(assessment: Assessment): ResearchInference }
class RulesOnlyInference : EdgeInference {
    override fun infer(assessment: Assessment) = ResearchInference(
        if (assessment.systolic == null || assessment.diastolic == null) InferenceState.ABSTAINED else InferenceState.UNAVAILABLE
    )
}
