package org.vitalink.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.vitalink.api.Contracts.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class ClinicalRulesTest {
    private Assessment a(Integer systolic,Integer diastolic,List<Symptom> symptoms) {
        return new Assessment(UUID.randomUUID(),"SYN-TEST",Instant.parse("2026-01-01T00:00:00Z"),systolic,diastolic,32,symptoms,Source.MANUAL,RULE_VERSION);
    }
    @ParameterizedTest @CsvSource({"160,80","120,110","160,109","159,110","160,110","300,200","161,109","159,111"})
    void eitherSeverePressureAlwaysTriggersUrgentAssessment(int systolic,int diastolic) {
        assertEquals(Urgency.URGENT,ClinicalRules.evaluate(a(systolic,diastolic,List.of())).urgency());
    }
    @ParameterizedTest @CsvSource({"140,80","120,90","159,109"})
    void elevatedPressureRequiresReview(int systolic,int diastolic) {
        assertEquals(Urgency.REVIEW_REQUIRED,ClinicalRules.evaluate(a(systolic,diastolic,List.of())).urgency());
    }
    @ParameterizedTest @EnumSource(Symptom.class)
    void concerningSymptomsRemainUrgentWithNormalPressure(Symptom symptom) {
        var result=ClinicalRules.evaluate(a(120,80,List.of(symptom)));
        assertEquals(Urgency.URGENT,result.urgency());
        assertTrue(result.ruleTriggers().contains("SYMPTOM_"+symptom.name()));
    }
    @Test void missingPressureIsNotNegativeFinding() {
        var result=ClinicalRules.evaluate(a(null,null,List.of()));
        assertEquals(Urgency.REVIEW_REQUIRED,result.urgency());
        assertEquals(List.of("MISSING_SYSTOLIC","MISSING_DIASTOLIC"),result.ruleTriggers());
    }
    @Test void severePressureWithOtherMissingMeasurementRemainsUrgent() {
        assertEquals(Urgency.URGENT,ClinicalRules.evaluate(a(160,null,List.of())).urgency());
        assertEquals(Urgency.URGENT,ClinicalRules.evaluate(a(null,110,List.of())).urgency());
    }
    @Test void invalidOtherValueDoesNotEraseSafetyEvaluation() {
        assertEquals(Urgency.URGENT,ClinicalRules.evaluate(a(160,-1,List.of())).urgency());
        assertEquals(Urgency.URGENT,ClinicalRules.evaluate(a(-1,80,List.of(Symptom.SEIZURE))).urgency());
    }
    @Test void belowThresholdsMeansOnlyNoRuleTrigger() {
        assertEquals(Urgency.NO_TRIGGER,ClinicalRules.evaluate(a(139,89,List.of())).urgency());
    }
    @Test void reversedPressuresCannotBePersisted() {
        assertThrows(ApiProblem.class,()->ClinicalRules.validate(a(90,100,List.of()),Clock.systemUTC()));
    }
    @Test void duplicateSymptomsCannotBePersisted() {
        assertThrows(ApiProblem.class,()->ClinicalRules.validate(a(120,80,List.of(Symptom.SEIZURE,Symptom.SEIZURE)),Clock.systemUTC()));
    }
    @Test void futureMeasurementCannotBePersisted() {
        var a=new Assessment(UUID.randomUUID(),"SYN-TEST",Instant.parse("2027-01-01T00:00:00Z"),120,80,32,List.of(),Source.MANUAL,RULE_VERSION);
        assertThrows(ApiProblem.class,()->ClinicalRules.validate(a,Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC)));
    }
    @Test void snapshotSymptomsCannotMutateAfterConstruction() {
        var symptoms=new ArrayList<Symptom>(); symptoms.add(Symptom.SEIZURE);
        var a=a(120,80,symptoms); symptoms.clear();
        assertEquals(List.of(Symptom.SEIZURE),a.symptoms());
        assertThrows(UnsupportedOperationException.class,()->a.symptoms().clear());
    }
}
