package org.vitalink.api;

import static org.junit.jupiter.api.Assertions.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Independent clean-cluster migration gate; explicitly skipped when Docker cannot be reached. */
@Testcontainers(disabledWithoutDocker=true)
class PostgresContainerTest {
    @Container static final PostgreSQLContainer<?> postgres=new PostgreSQLContainer<>("postgres:17-alpine");
    @Test void migratesCleanPostgresAndValidatesChecksum() {
        var flyway=Flyway.configure().dataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword()).load();
        assertEquals(1,flyway.migrate().migrationsExecuted);
        flyway.validate(); assertEquals(0,flyway.migrate().migrationsExecuted);
    }
}
