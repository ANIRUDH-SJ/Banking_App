package com.netbanking.database;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "ORACLE_SCHEMA_VALIDATION_URL", matches = ".+")
class OracleSchemaValidationTest {
    @Test
    void paymentMappingsMatchTheMigratedOracleSchema() throws Exception {
        String url = System.getenv("ORACLE_SCHEMA_VALIDATION_URL");
        String username = requiredEnvironment("ORACLE_SCHEMA_VALIDATION_USERNAME");
        String password = requiredEnvironment("ORACLE_SCHEMA_VALIDATION_PASSWORD");
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertColumn(connection, "BILLER", "IS_ACTIVE", "CHAR", 1, false);
            assertColumn(connection, "BILLER", "REFERENCE_PATTERN", "VARCHAR2", 200, true);
            assertColumn(connection, "OTP_VERIFICATION", "INTENT_DIGEST", "VARCHAR2", 64, true);
            assertColumn(connection, "OTP_VERIFICATION", "VERSION", "NUMBER", null, false);
            assertColumn(connection, "FUND_TRANSFER", "REQUEST_FINGERPRINT", "VARCHAR2", 64, true);
            assertColumn(connection, "BILL_PAYMENT", "REQUEST_FINGERPRINT", "VARCHAR2", 64, true);
            assertColumn(connection, "LOAN_PAYMENT", "REQUEST_FINGERPRINT", "VARCHAR2", 64, true);
        }
    }

    private static void assertColumn(Connection connection, String table, String column,
                                     String dataType, Integer characterLength, boolean nullable)
            throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT data_type, char_length, nullable
                  FROM user_tab_columns
                 WHERE table_name = ? AND column_name = ?
                """)) {
            statement.setString(1, table);
            statement.setString(2, column);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).as(table + "." + column + " exists").isTrue();
                assertThat(result.getString("data_type")).isEqualTo(dataType);
                if (characterLength != null) {
                    assertThat(result.getInt("char_length")).isEqualTo(characterLength);
                }
                assertThat(result.getString("nullable")).isEqualTo(nullable ? "Y" : "N");
            }
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured for Oracle schema validation.");
        }
        return value;
    }
}
