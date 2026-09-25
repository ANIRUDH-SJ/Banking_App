package com.netbanking.database;

import static org.assertj.core.api.Assertions.assertThat;

import com.netbanking.biller.domain.Biller;
import com.netbanking.loan.domain.LoanPayment;
import com.netbanking.otp.domain.OtpVerification;
import com.netbanking.payment.domain.BillPayment;
import com.netbanking.payment.domain.FundTransfer;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;

class OracleMappingContractTest {
    @Test
    void characterAndFingerprintColumnsMatchTheirOracleDefinitions() throws Exception {
        Column active = Biller.class.getDeclaredField("isActive").getAnnotation(Column.class);
        assertThat(Biller.class.getDeclaredField("isActive").getType()).isEqualTo(Character.class);
        assertThat(active.name()).isEqualTo("is_active");
        assertThat(active.length()).isEqualTo(1);

        assertColumn(FundTransfer.class, "requestFingerprint", "request_fingerprint", 64);
        assertColumn(BillPayment.class, "requestFingerprint", "request_fingerprint", 64);
        assertColumn(LoanPayment.class, "requestFingerprint", "request_fingerprint", 64);
        assertColumn(OtpVerification.class, "intentDigest", "intent_digest", 64);
    }

    private static void assertColumn(Class<?> entity, String field, String name, int length)
            throws Exception {
        Column column = entity.getDeclaredField(field).getAnnotation(Column.class);
        assertThat(column.name()).isEqualTo(name);
        assertThat(column.length()).isEqualTo(length);
    }
}
