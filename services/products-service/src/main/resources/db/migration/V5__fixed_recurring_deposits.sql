CREATE TABLE deposit_quote (
    quote_id VARCHAR2(36) PRIMARY KEY,
    user_id NUMBER(19) NOT NULL,
    source_account_id NUMBER(19) NOT NULL,
    kind VARCHAR2(2) NOT NULL,
    amount NUMBER(19,2) NOT NULL,
    term_months NUMBER(3) NOT NULL,
    annual_rate NUMBER(7,4) NOT NULL,
    estimated_maturity NUMBER(19,2) NOT NULL,
    expires_at TIMESTAMP(6) NOT NULL,
    CONSTRAINT ck_deposit_quote_kind CHECK (kind IN ('FD','RD')),
    CONSTRAINT ck_deposit_quote_amount CHECK (amount > 0)
);

CREATE TABLE deposit_contract (
    deposit_id VARCHAR2(36) PRIMARY KEY,
    quote_id VARCHAR2(36) NOT NULL UNIQUE,
    user_id NUMBER(19) NOT NULL,
    source_account_id NUMBER(19) NOT NULL,
    idempotency_key VARCHAR2(100) NOT NULL,
    kind VARCHAR2(2) NOT NULL,
    amount NUMBER(19,2) NOT NULL,
    term_months NUMBER(3) NOT NULL,
    annual_rate NUMBER(7,4) NOT NULL,
    estimated_maturity NUMBER(19,2) NOT NULL,
    status VARCHAR2(20) NOT NULL,
    opened_at TIMESTAMP(6),
    maturity_at TIMESTAMP(6),
    installments_paid NUMBER(3) DEFAULT 0 NOT NULL,
    next_due_at TIMESTAMP(6),
    payout_amount NUMBER(19,2),
    payout_reference VARCHAR2(64),
    created_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT uq_deposit_request UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_deposit_quote FOREIGN KEY (quote_id) REFERENCES deposit_quote(quote_id),
    CONSTRAINT ck_deposit_contract_kind CHECK (kind IN ('FD','RD')),
    CONSTRAINT ck_deposit_contract_status CHECK (status IN ('PENDING','ACTIVE','PAYOUT_PENDING','MATURED'))
);

CREATE TABLE deposit_installment (
    deposit_id VARCHAR2(36) NOT NULL,
    installment_number NUMBER(3) NOT NULL,
    amount NUMBER(19,2) NOT NULL,
    due_at TIMESTAMP(6) NOT NULL,
    paid_at TIMESTAMP(6) NOT NULL,
    ledger_reference VARCHAR2(64) NOT NULL,
    CONSTRAINT pk_deposit_installment PRIMARY KEY (deposit_id, installment_number),
    CONSTRAINT fk_deposit_installment FOREIGN KEY (deposit_id) REFERENCES deposit_contract(deposit_id)
);

CREATE INDEX ix_deposit_owner ON deposit_contract(user_id, created_at);
CREATE INDEX ix_deposit_due ON deposit_contract(status, next_due_at);
