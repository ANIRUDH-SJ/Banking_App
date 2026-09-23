package com.netbanking.loan.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class LoanPaymentAuditWriter {

    private final JdbcTemplate jdbcTemplate;

    public LoanPaymentAuditWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordCompleted(Long userId, Long loanId, String transactionReference) {
        jdbcTemplate.update("""
                INSERT INTO audit_event
                    (user_id, event_type, entity_type, entity_id, outcome, event_details)
                VALUES (?, 'LOAN_PAYMENT_COMPLETED', 'LOAN', ?, 'SUCCESS', ?)
                """, userId, String.valueOf(loanId), "transactionReference=" + transactionReference);
    }
}
