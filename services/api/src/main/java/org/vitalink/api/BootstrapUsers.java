package org.vitalink.api;

import static org.vitalink.api.Contracts.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit runtime bootstrap only. No users or passwords exist unless supplied by the operator. */
@Component
public class BootstrapUsers implements CommandLineRunner {
    private final JdbcTemplate jdbc; private final ObjectMapper json; private final AuthService auth; private final String config;
    public BootstrapUsers(JdbcTemplate jdbc,ObjectMapper json,AuthService auth,@Value("${vitalink.bootstrap-users:[]}") String config) {
        this.jdbc=jdbc; this.json=json; this.auth=auth; this.config=config;
    }
    public record Seed(String username,String password,Role role,String facilityId) {}
    @Override @Transactional public void run(String... ignored) throws Exception {
        List<Seed> seeds;
        try { seeds=json.readValue(config,new TypeReference<List<Seed>>(){}); }
        catch(Exception e) { throw new IllegalStateException("Invalid bootstrap user configuration."); }
        for(var seed:seeds) {
            if(seed.username()==null || !seed.username().matches("[A-Za-z0-9_.-]{1,64}") || seed.password()==null || seed.password().length()<16 || seed.password().length()>72 || seed.role()==null
                || seed.facilityId()==null || !seed.facilityId().matches("[A-Za-z0-9-]{1,80}"))
                throw new IllegalStateException("Invalid bootstrap user configuration.");
            jdbc.update("INSERT INTO facility(id) VALUES(?) ON CONFLICT DO NOTHING",seed.facilityId());
            if(jdbc.queryForObject("SELECT COUNT(*) FROM app_user WHERE username=?",Integer.class,seed.username())==0)
                jdbc.update("INSERT INTO app_user(username,password_hash,role,facility_id) VALUES(?,?,?,?)",seed.username(),auth.encodePassword(seed.password()),seed.role().name(),seed.facilityId());
        }
    }
}
