package org.vitalink.api;

import static org.vitalink.api.Contracts.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder(12);
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash = passwords.encode("unusable-"+UUID.randomUUID());
    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private record Attempts(int count, Instant until) {}

    public AuthService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    public synchronized void checkRate(String username,String remote) {
        var now=Instant.now();
        attempts.entrySet().removeIf(e -> e.getValue().until().isBefore(now));
        if (attempts.size()>10000) throw new ApiProblem(429,"RATE_LIMITED","Too many attempts; try again later.");
        for (String key : List.of("u:"+username,"ip:"+remote)) {
            int limit=key.startsWith("u:") ? 5 : 30;
            var current=attempts.getOrDefault(key,new Attempts(0,now.plusSeconds(60)));
            if(current.count()>=limit) throw new ApiProblem(429,"RATE_LIMITED","Too many attempts; try again later.");
            attempts.put(key,new Attempts(current.count()+1,current.until()));
        }
    }
    @Transactional(noRollbackFor=ApiProblem.class)
    public Session login(Login login,String remote) {
        checkRate(login.username(),remote);
        var users=jdbc.query("SELECT username,password_hash,role,facility_id FROM app_user WHERE username=? AND enabled=true",
            (rs,n)->new Account(new User(rs.getString("username"),Role.valueOf(rs.getString("role")),rs.getString("facility_id")),rs.getString("password_hash")),login.username());
        var account=users.isEmpty()?null:users.getFirst();
        boolean valid;
        try { valid=passwords.matches(login.password(),account==null?dummyHash:account.hash()); }
        catch(IllegalArgumentException ignored) { valid=false; }
        if(!valid || account==null) {
            jdbc.update("INSERT INTO audit_event(actor,action) VALUES(NULL,'LOGIN_FAILED')");
            throw ApiProblem.unauthorized();
        }
        byte[] bytes=new byte[32]; random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant expiry=Instant.now().plus(Duration.ofHours(8));
        jdbc.update("DELETE FROM auth_session WHERE expires_at < CURRENT_TIMESTAMP");
        jdbc.update("INSERT INTO auth_session(token_hash,username,expires_at) VALUES(?,?,?)",digest(token),account.user().username(),java.sql.Timestamp.from(expiry));
        audit(account.user(),"LOGIN_SUCCEEDED",null);
        return new Session(token,expiry,account.user());
    }
    public User authenticate(String token) {
        if(token==null || !token.matches("[A-Za-z0-9_-]{43}")) throw ApiProblem.unauthorized();
        var users=jdbc.query("SELECT u.username,u.role,u.facility_id FROM auth_session s JOIN app_user u ON u.username=s.username WHERE s.token_hash=? AND s.expires_at>CURRENT_TIMESTAMP AND u.enabled=true",
            (rs,n)->new User(rs.getString("username"),Role.valueOf(rs.getString("role")),rs.getString("facility_id")),digest(token));
        if(users.isEmpty()) throw ApiProblem.unauthorized();
        return users.getFirst();
    }
    @Transactional
    public void logout(String token,User user) {
        jdbc.update("DELETE FROM auth_session WHERE token_hash=?",digest(token));
        audit(user,"LOGOUT",null);
    }
    public void audit(User user,String action,UUID id) {
        jdbc.update("INSERT INTO audit_event(actor,action,resource_id,facility_id) VALUES(?,?,?,?)",user.username(),action,id,user.facilityId());
    }
    public String encodePassword(String password) { return passwords.encode(password); }
    public static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable",e); }
    }
    private record Account(User user,String hash) {}
}
