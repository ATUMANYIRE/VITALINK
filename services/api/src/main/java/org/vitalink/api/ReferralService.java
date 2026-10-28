package org.vitalink.api;

import static org.vitalink.api.Contracts.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReferralService {
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final AuthService auth;
    private static final String SELECT="SELECT r.*,a.snapshot FROM referral r JOIN assessment a ON a.id=r.assessment_id";
    public ReferralService(JdbcTemplate jdbc,ObjectMapper json,AuthService auth) { this.jdbc=jdbc; this.json=json; this.auth=auth; }

    @Transactional
    public Ingested ingest(Submission request,String idempotencyKey,User user) {
        requireRole(user,Role.HEALTH_WORKER,Role.ADMIN);
        if(!request.facilityId().equals(user.facilityId())) throw ApiProblem.forbidden();
        if(!request.id().toString().equals(idempotencyKey)) throw ApiProblem.badInput("Idempotency-Key must match the referral UUID.");
        ClinicalRules.validate(request.assessment(),Clock.systemUTC());
        String requestHash=AuthService.digest(serialize(request));
        lock(1,request.id());
        var existing=jdbc.query(SELECT+" WHERE r.id=?",this::referral,request.id());
        if(!existing.isEmpty()) {
            var current=existing.getFirst();
            if(!current.facilityId().equals(user.facilityId())) throw ApiProblem.notFound();
            String savedHash=jdbc.queryForObject("SELECT request_hash FROM referral WHERE id=?",String.class,request.id());
            if(!requestHash.equals(savedHash)) throw ApiProblem.conflict();
            return new Ingested(current,false);
        }
        Assessment a=request.assessment();
        lock(2,a.id());
        var snapshots=jdbc.query("SELECT facility_id,snapshot FROM assessment WHERE id=?",(rs,n)->Map.entry(rs.getString(1),parseAssessment(rs.getString(2))),a.id());
        if(!snapshots.isEmpty() && (!snapshots.getFirst().getKey().equals(user.facilityId()) || !snapshots.getFirst().getValue().equals(a))) throw ApiProblem.conflict();
        if(snapshots.isEmpty()) {
            jdbc.update("INSERT INTO patient(id,facility_id) VALUES(?,?) ON CONFLICT DO NOTHING",a.patientId(),user.facilityId());
            jdbc.update("INSERT INTO assessment(id,patient_id,facility_id,measured_at,snapshot) VALUES(?,?,?,?,CAST(? AS jsonb))",
                a.id(),a.patientId(),user.facilityId(),java.sql.Timestamp.from(a.measuredAt()),serialize(a));
        }
        var finding=ClinicalRules.evaluate(a);
        jdbc.update("INSERT INTO referral(id,facility_id,assessment_id,request_hash,status,urgency,rule_triggers) VALUES(?,?,?,?,'SERVER_RECEIVED',?,CAST(? AS jsonb))",
            request.id(),user.facilityId(),a.id(),requestHash,finding.urgency().name(),serialize(finding.ruleTriggers()));
        jdbc.update("INSERT INTO referral_history(referral_id,status,actor) VALUES(?,'SERVER_RECEIVED',?)",request.id(),user.username());
        auth.audit(user,"REFERRAL_RECEIVED",request.id());
        return new Ingested(get(request.id(),user),true);
    }

    public Referral get(UUID id,User user) {
        var rows=jdbc.query(SELECT+" WHERE r.id=? AND r.facility_id=?",this::referral,id,user.facilityId());
        if(rows.isEmpty()) throw ApiProblem.notFound();
        return rows.getFirst();
    }
    @Transactional(readOnly=true)
    public Page list(User user,int page,int size,Urgency urgency,Status status,String patientId) {
        if(page<0 || page>1000000 || size<1 || size>100) throw ApiProblem.badInput("Pagination values are outside the supported range.");
        if(patientId!=null && !patientId.matches("SYN-[A-Za-z0-9-]{1,40}")) throw ApiProblem.badInput("Patient identifier must be synthetic.");
        var values=new ArrayList<Object>(); values.add(user.facilityId());
        String where=" WHERE r.facility_id=?";
        if(urgency!=null) { where+=" AND r.urgency=?"; values.add(urgency.name()); }
        if(status!=null) { where+=" AND r.status=?"; values.add(status.name()); }
        if(patientId!=null) { where+=" AND a.patient_id=?"; values.add(patientId); }
        long count=jdbc.queryForObject("SELECT COUNT(*) FROM referral r JOIN assessment a ON a.id=r.assessment_id"+where,Long.class,values.toArray());
        values.add(size); values.add((long)page*size);
        var items=jdbc.query(SELECT+where+" ORDER BY CASE r.urgency WHEN 'URGENT' THEN 0 WHEN 'REVIEW_REQUIRED' THEN 1 ELSE 2 END,r.received_at,r.id LIMIT ? OFFSET ?",this::referral,values.toArray());
        return new Page(items,page,size,count,(int)((count+size-1)/size));
    }
    public List<Assessment> assessments(User user,String patientId) {
        if(!patientId.matches("SYN-[A-Za-z0-9-]{1,40}")) throw ApiProblem.badInput("Patient identifier must be synthetic.");
        return jdbc.query("SELECT snapshot FROM assessment WHERE facility_id=? AND patient_id=? ORDER BY measured_at DESC,id LIMIT 100",
            (rs,n)->parseAssessment(rs.getString(1)),user.facilityId(),patientId);
    }
    @Transactional(readOnly=true)
    public List<History> history(UUID id,User user) {
        get(id,user);
        return jdbc.query("SELECT status,at,actor FROM referral_history WHERE referral_id=? ORDER BY sequence",
            (rs,n)->new History(Status.valueOf(rs.getString(1)),rs.getTimestamp(2).toInstant(),rs.getString(3)),id);
    }
    @Transactional
    public Referral transition(UUID id,User user,Status target) {
        requireRole(user,Role.DOCTOR,Role.ADMIN);
        var current=jdbc.query(SELECT+" WHERE r.id=? AND r.facility_id=? FOR UPDATE OF r",this::referral,id,user.facilityId());
        if(current.isEmpty()) throw ApiProblem.notFound();
        var before=current.getFirst();
        if(before.status()==target || target==Status.ACKNOWLEDGED && before.status()==Status.CLOSED) return before;
        if(target==Status.ACKNOWLEDGED && before.status()==Status.SERVER_RECEIVED) {
            jdbc.update("UPDATE referral SET status='ACKNOWLEDGED',acknowledged_at=CURRENT_TIMESTAMP,acknowledged_by=? WHERE id=?",user.username(),id);
        } else if(target==Status.CLOSED && before.status()==Status.ACKNOWLEDGED) {
            jdbc.update("UPDATE referral SET status='CLOSED' WHERE id=?",id);
        } else throw ApiProblem.conflict();
        jdbc.update("INSERT INTO referral_history(referral_id,status,actor) VALUES(?,?,?)",id,target.name(),user.username());
        auth.audit(user,"REFERRAL_"+target.name(),id);
        return get(id,user);
    }
    public Metrics metrics(User user) {
        return jdbc.queryForObject("SELECT COUNT(*) AS total,COUNT(*) FILTER(WHERE urgency='URGENT') AS urgent,COUNT(*) FILTER(WHERE status='SERVER_RECEIVED') AS awaiting,COUNT(*) FILTER(WHERE status='ACKNOWLEDGED') AS acknowledged,COUNT(*) FILTER(WHERE status='CLOSED') AS closed FROM referral WHERE facility_id=?",
            (rs,n)->new Metrics(rs.getLong("total"),rs.getLong("urgent"),rs.getLong("awaiting"),rs.getLong("acknowledged"),rs.getLong("closed")),user.facilityId());
    }
    private void lock(int namespace,UUID id) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(?,?)",Object.class,namespace,id.hashCode());
    }
    private static void requireRole(User user,Role... permitted) {
        if(Arrays.stream(permitted).noneMatch(r->r==user.role())) throw ApiProblem.forbidden();
    }
    private Referral referral(ResultSet rs,int row) throws SQLException {
        try {
            var acknowledged=rs.getTimestamp("acknowledged_at");
            return new Referral(rs.getObject("id",UUID.class),rs.getString("facility_id"),parseAssessment(rs.getString("snapshot")),
                Status.valueOf(rs.getString("status")),Urgency.valueOf(rs.getString("urgency")),json.readValue(rs.getString("rule_triggers"),new TypeReference<List<String>>(){}),
                rs.getTimestamp("received_at").toInstant(),acknowledged==null?null:acknowledged.toInstant(),rs.getString("acknowledged_by"));
        } catch(JsonProcessingException e) { throw new SQLException("Stored referral format is invalid."); }
    }
    private Assessment parseAssessment(String value) {
        try { return json.readValue(value,Assessment.class); }
        catch(JsonProcessingException e) { throw new IllegalStateException("Stored assessment format is invalid."); }
    }
    private String serialize(Object value) {
        try { return json.writeValueAsString(value); }
        catch(JsonProcessingException e) { throw new IllegalStateException("Unable to encode snapshot."); }
    }
}
