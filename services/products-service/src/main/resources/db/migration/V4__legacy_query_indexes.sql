CREATE INDEX ix_loan_payment_source
    ON loan_payment (source_account_id, paid_at);
