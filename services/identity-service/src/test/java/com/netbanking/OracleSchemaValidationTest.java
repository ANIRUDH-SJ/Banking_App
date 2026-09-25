package com.netbanking;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {"eureka.client.enabled=false", "app.scheduling.enabled=false"})
@EnabledIfEnvironmentVariable(named = "ORACLE_SCHEMA_VALIDATION", matches = "true")
class OracleSchemaValidationTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void migratesAndValidatesThePrivateOracleSchema() {
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM user_tables WHERE table_name ="
                                        + " 'EVENT_OUTBOX'",
                                Integer.class))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT COUNT(*) FROM user_tab_privs_recd WHERE owner IN"
                                    + " ('NB_IDENTITY','NB_ACCOUNTS','NB_PAYMENTS','NB_PRODUCTS','NB_NOTIFICATIONS','NB_AUDIT')"
                                    + " AND owner <> USER",
                                Integer.class))
                .isZero();
    }
}
