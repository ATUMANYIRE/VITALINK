package org.vitalink.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;

class AuthServiceTest {
    private JdbcTemplate jdbc;
    private AuthService auth;
    @BeforeEach void setup() { jdbc=mock(JdbcTemplate.class); auth=new AuthService(jdbc); }
    @Test void malformedTokenNeverQueriesDatabase() {
        assertThrows(ApiProblem.class,()->auth.authenticate("not-a-valid-token"));
        verifyNoInteractions(jdbc);
    }
    @Test void missingTokenNeverQueriesDatabase() {
        assertThrows(ApiProblem.class,()->auth.authenticate(null));
        verifyNoInteractions(jdbc);
    }
    @Test void tokenDigestIsStableAndDoesNotContainToken() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",AuthService.digest("abc"));
    }
    @Test void repeatedAttemptsAreThrottled() {
        for(int i=0;i<5;i++) auth.checkRate("worker","127.0.0.1");
        var problem=assertThrows(ApiProblem.class,()->auth.checkRate("worker","127.0.0.1"));
        assertEquals(429,problem.status());
    }
    @Test void passwordEncodingIsSaltedAndNotPlaintext() {
        var first=auth.encodePassword("Synthetic-password-only");
        var second=auth.encodePassword("Synthetic-password-only");
        assertNotEquals(first,second); assertTrue(first.startsWith("$2a$12$"));
    }
}
