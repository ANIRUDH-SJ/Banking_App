CREATE TABLE deposit_closure_quote (
    closure_quote_id VARCHAR2(36) PRIMARY KEY,
    deposit_id VARCHAR2(36) NOT NULL,
    user_id NUMBER(19) NOT NULL,
    installments_paid NUMBER(3) NOT NULL,
    principal NUMBER(19,2) NOT NULL,
    interest NUMBER(19,2) NOT NULL,
    effective_rate NUMBER(7,4) NOT NULL,
    payout_amount NUMBER(19,2) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT fk_deposit_closure_quote FOREIGN KEY (deposit_id) REFERENCES deposit_contract(deposit_id),
    CONSTRAINT ck_deposit_closure_amount CHECK (principal > 0 AND interest >= 0 AND payout_amount >= principal)
);

ALTER TABLE deposit_contract ADD (
    closure_quote_id VARCHAR2(36),
    closure_request_key VARCHAR2(100),
    closed_at TIMESTAMP(6)
);
ALTER TABLE deposit_contract DROP CONSTRAINT ck_deposit_contract_status;
ALTER TABLE deposit_contract ADD CONSTRAINT ck_deposit_contract_status
    CHECK (status IN ('PENDING','ACTIVE','PAYOUT_PENDING','MATURED','CLOSURE_PENDING','CLOSED'));

CREATE INDEX ix_deposit_closure_recovery ON deposit_contract(status, created_at);
