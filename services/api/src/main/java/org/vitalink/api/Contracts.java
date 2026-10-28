package org.vitalink.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class Contracts {
    private Contracts() {}
    public static final String RULE_VERSION = "vitalink-rules-1.0.0";
    public enum Role { HEALTH_WORKER, DOCTOR, ADMIN }
    public enum Status { SERVER_RECEIVED, ACKNOWLEDGED, CLOSED }
    public enum Urgency { URGENT, REVIEW_REQUIRED, NO_TRIGGER }
    public enum Source { MANUAL, DEVICE }
    public enum Symptom { SEVERE_HEADACHE, VISUAL_DISTURBANCE, UPPER_ABDOMINAL_PAIN,
        BREATHLESSNESS, SEIZURE, HEAVY_BLEEDING, REDUCED_FETAL_MOVEMENT }
    public record Assessment(
        @NotNull UUID id,
        @NotNull @Pattern(regexp="SYN-[A-Za-z0-9-]{1,40}") String patientId,
        @NotNull Instant measuredAt,
        @Min(50) @Max(300) Integer systolic,
        @Min(30) @Max(200) Integer diastolic,
        @Min(0) @Max(45) Integer gestationalWeeks,
        @NotNull @Size(max=7) List<@NotNull Symptom> symptoms,
        @NotNull Source source,
        @NotNull @Pattern(regexp="vitalink-rules-1\\.0\\.0") String ruleVersion
    ) {
        public Assessment {
            if (symptoms != null) symptoms = List.copyOf(symptoms);
        }
    }
    public record Submission(@NotNull UUID id,
        @NotBlank @Size(max=80) @Pattern(regexp="[A-Za-z0-9-]+") String facilityId,
        @NotNull @Valid Assessment assessment) {}
    public record Referral(UUID id, String facilityId, Assessment assessment, Status status,
        Urgency urgency, List<String> ruleTriggers, Instant receivedAt,
        Instant acknowledgedAt, String acknowledgedBy) {}
    public record History(Status status, Instant at, String actor) {}
    public record Page(List<Referral> items, int page, int size, long totalElements, int totalPages) {}
    public record Metrics(long total, long urgent, long awaitingAcknowledgment, long acknowledged, long closed) {}
    public record User(String username, Role role, String facilityId) {}
    public record Login(@NotBlank @Pattern(regexp="[A-Za-z0-9_.-]{1,64}") String username,
        @NotNull @Size(min=8,max=72) String password) {}
    public record Session(String token, Instant expiresAt, User user) {}
    public record Findings(Urgency urgency, List<String> ruleTriggers) {}
    public record Ingested(Referral referral, boolean created) {}
}
