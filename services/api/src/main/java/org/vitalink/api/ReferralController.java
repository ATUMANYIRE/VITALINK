package org.vitalink.api;

import static org.vitalink.api.Contracts.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ReferralController {
    private final ReferralService service;
    public ReferralController(ReferralService service) { this.service=service; }
    @PostMapping("/sync/referrals")
    public ResponseEntity<Referral> ingest(@AuthenticationPrincipal User user,@RequestHeader("Idempotency-Key") String key,@Valid @RequestBody Submission body) {
        var result=service.ingest(body,key,user);
        return ResponseEntity.status(result.created()?201:200).cacheControl(CacheControl.noStore()).body(result.referral());
    }
    @GetMapping("/referrals")
    public Page list(@AuthenticationPrincipal User user,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
        @RequestParam(required=false) Urgency urgency,@RequestParam(required=false) Status status,@RequestParam(required=false) String patientId) {
        return service.list(user,page,size,urgency,status,patientId);
    }
    @GetMapping("/referrals/{id}") public Referral get(@AuthenticationPrincipal User user,@PathVariable UUID id) { return service.get(id,user); }
    @GetMapping("/referrals/{id}/history") public List<History> history(@AuthenticationPrincipal User user,@PathVariable UUID id) { return service.history(id,user); }
    @PostMapping("/referrals/{id}/acknowledge") public Referral acknowledge(@AuthenticationPrincipal User user,@PathVariable UUID id) { return service.transition(id,user,Status.ACKNOWLEDGED); }
    @PostMapping("/referrals/{id}/close") public Referral close(@AuthenticationPrincipal User user,@PathVariable UUID id) { return service.transition(id,user,Status.CLOSED); }
    @GetMapping("/patients/{patientId}/assessments") public List<Assessment> assessments(@AuthenticationPrincipal User user,@PathVariable String patientId) { return service.assessments(user,patientId); }
    @GetMapping("/metrics") public Metrics metrics(@AuthenticationPrincipal User user) { return service.metrics(user); }
    @GetMapping("/health") public Map<String,String> health() { return Map.of("status","UP","mode","RULES_ONLY_RESEARCH"); }
}
