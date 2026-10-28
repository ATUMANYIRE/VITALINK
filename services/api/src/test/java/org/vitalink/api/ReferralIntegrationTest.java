package org.vitalink.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.vitalink.api.Contracts.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Real HTTP, real authentication, real PostgreSQL. No mocked integration components. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="DATABASE_URL",matches="jdbc:postgresql:.*")
class ReferralIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired AuthService auth;
    private final Map<String,Session> sessions=new HashMap<>();
    private final List<UUID> created=Collections.synchronizedList(new ArrayList<>());
    private List<BootstrapUsers.Seed> seeds;

    @BeforeAll void login() throws Exception {
        seeds=json.readValue(System.getenv("VITALINK_BOOTSTRAP_USERS"),new TypeReference<List<BootstrapUsers.Seed>>(){});
        for(var seed:seeds) sessions.put(seed.username(),auth.login(new Login(seed.username(),seed.password()),"integration-"+seed.username()));
    }
    @AfterAll void cleanup() {
        for(UUID id:created) {
            var assessment=jdbc.query("SELECT assessment_id FROM referral WHERE id=?",(rs,n)->rs.getObject(1,UUID.class),id);
            jdbc.update("DELETE FROM referral WHERE id=?",id);
            for(UUID a:assessment) jdbc.update("DELETE FROM assessment WHERE id=? AND NOT EXISTS(SELECT 1 FROM referral WHERE assessment_id=?)",a,a);
        }
        jdbc.update("DELETE FROM patient p WHERE id LIKE 'SYN-TEST-%' AND NOT EXISTS(SELECT 1 FROM assessment a WHERE a.patient_id=p.id AND a.facility_id=p.facility_id)");
        for(var session:sessions.values()) jdbc.update("DELETE FROM auth_session WHERE token_hash=?",AuthService.digest(session.token()));
    }
    private Submission submission(Integer systolic,Integer diastolic,List<Symptom> symptoms) {
        UUID id=UUID.randomUUID(); created.add(id);
        return new Submission(id,"demo-facility",new Assessment(UUID.randomUUID(),"SYN-TEST-"+id.toString().substring(0,8),
            Instant.now().minusSeconds(1),systolic,diastolic,32,symptoms,Source.MANUAL,RULE_VERSION));
    }
    private ResponseEntity<JsonNode> request(HttpMethod method,String path,Object body,String account,String key) {
        var headers=new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        if(account!=null) headers.setBearerAuth(sessions.get(account).token());
        if(key!=null) headers.set("Idempotency-Key",key);
        return http.exchange("/api/v1"+path,method,new HttpEntity<>(body,headers),JsonNode.class);
    }
    private ResponseEntity<JsonNode> ingest(Submission body,String user) {
        return request(HttpMethod.POST,"/sync/referrals",body,user,body.id().toString());
    }
    private JsonNode assertReceived(Submission s) {
        var response=ingest(s,"worker"); assertEquals(201,response.getStatusCode().value(),()->String.valueOf(response.getBody()));
        assertEquals("SERVER_RECEIVED",response.getBody().get("status").asText());
        return response.getBody();
    }
    @Test void severeReferralIsReceivedButNeverAutomaticallyAcknowledged() {
        var s=submission(160,80,List.of()); var received=assertReceived(s);
        assertEquals("URGENT",received.get("urgency").asText());
        assertTrue(received.get("acknowledgedAt").isNull()); assertTrue(received.get("acknowledgedBy").isNull());
        assertEquals(s.id().toString(),received.get("id").asText());
        assertEquals("no-store",request(HttpMethod.GET,"/referrals/"+s.id(),null,"doctor",null).getHeaders().getFirst("Cache-Control"));
    }
    @Test void fullReceiptAcknowledgmentClosureHistoryLifecycle() {
        var s=submission(120,80,List.of(Symptom.SEVERE_HEADACHE)); assertReceived(s);
        var ack=request(HttpMethod.POST,"/referrals/"+s.id()+"/acknowledge",null,"doctor",null);
        assertEquals(200,ack.getStatusCode().value()); assertEquals("ACKNOWLEDGED",ack.getBody().get("status").asText());
        assertEquals("doctor",ack.getBody().get("acknowledgedBy").asText()); assertFalse(ack.getBody().get("acknowledgedAt").isNull());
        var replay=ingest(s,"worker"); assertEquals(200,replay.getStatusCode().value()); assertEquals("ACKNOWLEDGED",replay.getBody().get("status").asText());
        request(HttpMethod.POST,"/referrals/"+s.id()+"/acknowledge",null,"doctor",null);
        var closed=request(HttpMethod.POST,"/referrals/"+s.id()+"/close",null,"doctor",null);
        assertEquals("CLOSED",closed.getBody().get("status").asText());
        assertEquals(200,request(HttpMethod.POST,"/referrals/"+s.id()+"/close",null,"doctor",null).getStatusCode().value());
        var history=request(HttpMethod.GET,"/referrals/"+s.id()+"/history",null,"worker",null).getBody();
        assertEquals(3,history.size()); assertEquals("SERVER_RECEIVED",history.get(0).get("status").asText());
        assertEquals("ACKNOWLEDGED",history.get(1).get("status").asText()); assertEquals("CLOSED",history.get(2).get("status").asText());
    }
    @Test void identicalReplayAfterLostResponseDoesNotDuplicate() {
        var s=submission(150,95,List.of()); assertReceived(s);
        var second=ingest(s,"worker"); assertEquals(200,second.getStatusCode().value());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM referral WHERE id=?",Integer.class,s.id()));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM referral_history WHERE referral_id=?",Integer.class,s.id()));
    }
    @Test void concurrentDuplicateRequestsCommitExactlyOneSnapshotAndReceipt() throws Exception {
        var s=submission(170,100,List.of());
        try(var pool=Executors.newFixedThreadPool(8)) {
            var calls=new ArrayList<Callable<Integer>>(); for(int i=0;i<8;i++) calls.add(()->ingest(s,"worker").getStatusCode().value());
            var results=pool.invokeAll(calls); int first=0;
            for(var result:results) { int code=result.get(); assertTrue(code==200 || code==201,"Unexpected HTTP "+code); if(code==201) first++; }
            assertEquals(1,first);
        }
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM referral_history WHERE referral_id=?",Integer.class,s.id()));
    }
    @Test void conflictingIdempotencySnapshotRejected() {
        var s=submission(160,90,List.of()); assertReceived(s);
        var a=s.assessment(); var changed=new Submission(s.id(),s.facilityId(),new Assessment(a.id(),a.patientId(),a.measuredAt(),161,90,32,List.of(),Source.MANUAL,RULE_VERSION));
        assertEquals(409,ingest(changed,"worker").getStatusCode().value());
        assertEquals(160,request(HttpMethod.GET,"/referrals/"+s.id(),null,"worker",null).getBody().get("assessment").get("systolic").asInt());
    }
    @Test void reusedAssessmentIdentifierCannotChangeImmutableSnapshot() {
        var s=submission(160,90,List.of()); assertReceived(s);
        var a=s.assessment(); var second=submission(161,90,List.of());
        var changed=new Submission(second.id(),second.facilityId(),new Assessment(a.id(),a.patientId(),a.measuredAt(),161,90,32,List.of(),Source.MANUAL,RULE_VERSION));
        assertEquals(409,ingest(changed,"worker").getStatusCode().value());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM referral WHERE id=?",Integer.class,second.id()));
    }
    @Test void databaseProtectsSnapshotsAgainstMutation() {
        var s=submission(160,90,List.of()); assertReceived(s);
        assertThrows(org.springframework.dao.DataAccessException.class,()->jdbc.update("UPDATE assessment SET snapshot='{}'::jsonb WHERE id=?",s.assessment().id()));
    }
    @Test void workerCannotAcknowledgeAndDoctorCannotSubmit() {
        var s=submission(120,80,List.of()); assertReceived(s);
        assertEquals(403,request(HttpMethod.POST,"/referrals/"+s.id()+"/acknowledge",null,"worker",null).getStatusCode().value());
        assertEquals(403,ingest(submission(120,80,List.of()),"doctor").getStatusCode().value());
    }
    @Test void doctorCannotReadAcknowledgeOrInspectOtherFacilityObject() {
        var s=submission(160,90,List.of()); assertReceived(s);
        for(String suffix:List.of("","/history")) assertEquals(404,request(HttpMethod.GET,"/referrals/"+s.id()+suffix,null,"outsider",null).getStatusCode().value());
        assertEquals(404,request(HttpMethod.POST,"/referrals/"+s.id()+"/acknowledge",null,"outsider",null).getStatusCode().value());
    }
    @Test void administratorDoesNotGainCrossFacilityAccess() {
        var s=submission(160,90,List.of()); assertReceived(s);
        assertEquals(404,request(HttpMethod.GET,"/referrals/"+s.id(),null,"outsider-admin",null).getStatusCode().value());
        assertEquals(403,ingest(s,"outsider-admin").getStatusCode().value());
    }
    @Test void missingAuthAndMalformedTokensAreRejected() {
        assertEquals(401,request(HttpMethod.GET,"/referrals",null,null,null).getStatusCode().value());
        var headers=new HttpHeaders(); headers.setBearerAuth("invalid");
        var response=http.exchange("/api/v1/referrals",HttpMethod.GET,new HttpEntity<>(headers),JsonNode.class);
        assertEquals(401,response.getStatusCode().value()); assertEquals("UNAUTHENTICATED",response.getBody().get("code").asText());
    }
    @Test void invalidStateTransitionIsConflict() {
        var s=submission(120,80,List.of()); assertReceived(s);
        assertEquals(409,request(HttpMethod.POST,"/referrals/"+s.id()+"/close",null,"doctor",null).getStatusCode().value());
    }
    @Test void symptomsAndMissingMeasurementsAreNotSuppressed() {
        assertEquals("URGENT",assertReceived(submission(120,80,List.of(Symptom.VISUAL_DISTURBANCE))).get("urgency").asText());
        assertEquals("REVIEW_REQUIRED",assertReceived(submission(null,null,List.of())).get("urgency").asText());
        assertEquals("URGENT",assertReceived(submission(null,110,List.of())).get("urgency").asText());
    }
    @Test void invalidInputAndUnknownFieldsAreRejectedWithoutDisclosure() {
        var s=submission(0,80,List.of());
        var invalid=ingest(s,"worker"); assertEquals(400,invalid.getStatusCode().value());
        assertFalse(invalid.getBody().toString().contains("SELECT")); assertFalse(invalid.getBody().toString().contains("stackTrace"));
        var node=json.valueToTree(submission(120,80,List.of())); ((com.fasterxml.jackson.databind.node.ObjectNode)node).put("urgency","URGENT");
        assertEquals(400,request(HttpMethod.POST,"/sync/referrals",node,"worker",node.get("id").asText()).getStatusCode().value());
    }
    @Test void reversedPressuresDuplicateSymptomsAndFutureTimeAreRejected() {
        assertEquals(400,ingest(submission(80,120,List.of()),"worker").getStatusCode().value());
        assertEquals(400,ingest(submission(120,80,List.of(Symptom.SEIZURE,Symptom.SEIZURE)),"worker").getStatusCode().value());
        var s=submission(120,80,List.of()); var a=s.assessment();
        assertEquals(400,ingest(new Submission(s.id(),s.facilityId(),new Assessment(a.id(),a.patientId(),Instant.now().plusSeconds(3600),120,80,32,List.of(),Source.MANUAL,RULE_VERSION)),"worker").getStatusCode().value());
    }
    @Test void idempotencyHeaderIsRequiredAndMustMatch() {
        var s=submission(120,80,List.of());
        assertEquals(400,request(HttpMethod.POST,"/sync/referrals",s,"worker",null).getStatusCode().value());
        assertEquals(400,request(HttpMethod.POST,"/sync/referrals",s,"worker",UUID.randomUUID().toString()).getStatusCode().value());
    }
    @Test void numericMeasurementsCannotBeSilentlyCoercedOrRounded() {
        var s=submission(120,80,List.of());
        var node=(com.fasterxml.jackson.databind.node.ObjectNode)json.valueToTree(s);
        ((com.fasterxml.jackson.databind.node.ObjectNode)node.get("assessment")).put("systolic",159.9);
        assertEquals(400,request(HttpMethod.POST,"/sync/referrals",node,"worker",s.id().toString()).getStatusCode().value());
        ((com.fasterxml.jackson.databind.node.ObjectNode)node.get("assessment")).put("systolic","160");
        assertEquals(400,request(HttpMethod.POST,"/sync/referrals",node,"worker",s.id().toString()).getStatusCode().value());
    }
    @Test void largeRequestBodiesAreRejected() {
        var headers=new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON); headers.setBearerAuth(sessions.get("worker").token());
        var response=http.exchange("/api/v1/sync/referrals",HttpMethod.POST,new HttpEntity<>("{\"padding\":\""+"a".repeat(66000)+"\"}",headers),JsonNode.class);
        assertEquals(413,response.getStatusCode().value());
    }
    @Test void openApiSpecificationIsServed() {
        var specification=http.getForEntity("/api/v1/openapi.yaml",String.class);
        assertEquals(200,specification.getStatusCode().value()); assertTrue(specification.getBody().contains("3.0.3"));
    }
    @Test void filteredPaginationAndMetricsUseAuthorizedStoredRecords() {
        var s=submission(160,90,List.of()); assertReceived(s);
        var page=request(HttpMethod.GET,"/referrals?size=1&urgency=URGENT&patientId="+s.assessment().patientId(),null,"doctor",null).getBody();
        assertEquals(1,page.get("totalElements").asInt()); assertEquals(1,page.get("items").size());
        assertEquals(400,request(HttpMethod.GET,"/referrals?size=101",null,"doctor",null).getStatusCode().value());
        assertEquals(0,request(HttpMethod.GET,"/referrals?patientId="+s.assessment().patientId(),null,"outsider",null).getBody().get("totalElements").asInt());
        var metrics=request(HttpMethod.GET,"/metrics",null,"doctor",null).getBody();
        assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM referral WHERE facility_id='demo-facility'",Long.class),metrics.get("total").asLong());
    }
    @Test void measurementHistoryIsScoped() {
        var s=submission(160,90,List.of()); assertReceived(s);
        assertEquals(1,request(HttpMethod.GET,"/patients/"+s.assessment().patientId()+"/assessments",null,"doctor",null).getBody().size());
        assertEquals(0,request(HttpMethod.GET,"/patients/"+s.assessment().patientId()+"/assessments",null,"outsider",null).getBody().size());
    }
    @Test void sessionsAreHashedExpireAndAreRevocable() {
        var seed=seeds.stream().filter(s->s.username().equals("worker")).findFirst().orElseThrow();
        var session=auth.login(new Login(seed.username(),seed.password()),"session-tests");
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM auth_session WHERE token_hash=?",Integer.class,session.token()));
        assertNotNull(auth.authenticate(session.token()));
        jdbc.update("UPDATE auth_session SET expires_at=CURRENT_TIMESTAMP-INTERVAL '1 second' WHERE token_hash=?",AuthService.digest(session.token()));
        assertThrows(ApiProblem.class,()->auth.authenticate(session.token()));
        var logout=auth.login(new Login(seed.username(),seed.password()),"session-tests");
        auth.logout(logout.token(),logout.user()); assertThrows(ApiProblem.class,()->auth.authenticate(logout.token()));
    }
    @Test void loginEndpointHashesPasswordsAndRejectsWrongCredentials() {
        var seed=seeds.stream().filter(s->s.username().equals("doctor")).findFirst().orElseThrow();
        var bad=request(HttpMethod.POST,"/auth/login",new Login(seed.username(),"wrong-password-value"),null,null);
        assertEquals(401,bad.getStatusCode().value());
        var good=request(HttpMethod.POST,"/auth/login",new Login(seed.username(),seed.password()),null,null);
        assertEquals(200,good.getStatusCode().value()); assertEquals("DOCTOR",good.getBody().get("user").get("role").asText());
        String stored=jdbc.queryForObject("SELECT password_hash FROM app_user WHERE username='doctor'",String.class);
        assertNotEquals(seed.password(),stored); assertTrue(stored.startsWith("$2a$12$"));
    }
    @Test void migrationsValidateAndRepeatedMigrationIsNoOp() {
        flyway.validate(); assertEquals(0,flyway.migrate().migrationsExecuted);
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=true",Integer.class));
    }
    @Test void healthAndReadinessAreAvailableWithoutInfrastructureDetails() {
        var health=request(HttpMethod.GET,"/health",null,null,null); assertEquals(200,health.getStatusCode().value());
        var readiness=http.getForEntity("/actuator/health/readiness",JsonNode.class);
        assertEquals(200,readiness.getStatusCode().value()); assertFalse(readiness.getBody().has("components"));
    }
}
