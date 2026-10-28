package org.vitalink.api;

import static org.vitalink.api.Contracts.*;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;

/** Deterministic research safety rules. Changes require versioning and clinician review. */
public final class ClinicalRules {
    private ClinicalRules() {}
    public static Findings evaluate(Assessment assessment) {
        var triggers = new ArrayList<String>();
        boolean urgent = false;
        if (assessment.systolic() == null) triggers.add("MISSING_SYSTOLIC");
        else if (assessment.systolic() >= 160) { triggers.add("SEVERE_SYSTOLIC"); urgent = true; }
        else if (assessment.systolic() >= 140) triggers.add("ELEVATED_SYSTOLIC");
        if (assessment.diastolic() == null) triggers.add("MISSING_DIASTOLIC");
        else if (assessment.diastolic() >= 110) { triggers.add("SEVERE_DIASTOLIC"); urgent = true; }
        else if (assessment.diastolic() >= 90) triggers.add("ELEVATED_DIASTOLIC");
        if (assessment.symptoms() != null) for (var symptom : assessment.symptoms()) {
            if (symptom != null) { triggers.add("SYMPTOM_" + symptom.name()); urgent = true; }
        }
        return new Findings(urgent ? Urgency.URGENT : triggers.isEmpty() ? Urgency.NO_TRIGGER : Urgency.REVIEW_REQUIRED,
            java.util.List.copyOf(triggers));
    }
    public static void validate(Assessment a, Clock clock) {
        if (a.systolic() != null && a.diastolic() != null && a.systolic() <= a.diastolic())
            throw ApiProblem.badInput("Systolic pressure must exceed diastolic pressure.");
        if (a.symptoms() == null || new HashSet<>(a.symptoms()).size() != a.symptoms().size())
            throw ApiProblem.badInput("Symptoms must be unique.");
        if (a.measuredAt() == null || a.measuredAt().isAfter(clock.instant().plusSeconds(300)))
            throw ApiProblem.badInput("Measurement timestamp is missing or too far in the future.");
    }
}
