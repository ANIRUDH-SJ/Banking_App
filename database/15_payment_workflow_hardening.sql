-- Payment workflow hardening. Run after 14_payment_feature_tables.sql as NET_BANKING_APP.
SET ECHO ON
SET FEEDBACK ON
WHENEVER SQLERROR EXIT SQL.SQLCODE

ALTER TABLE otp_verification ADD (
    intent_digest VARCHAR2(64 CHAR),
    version NUMBER(19) DEFAULT 0 NOT NULL
);

ALTER TABLE fund_transfer ADD (
    request_fingerprint VARCHAR2(64 CHAR) DEFAULT 'LEGACY' NOT NULL
);

ALTER TABLE bill_payment ADD (
    request_fingerprint VARCHAR2(64 CHAR) DEFAULT 'LEGACY' NOT NULL
);

ALTER TABLE otp_verification DROP CONSTRAINT ck_otp_purpose;
ALTER TABLE otp_verification ADD CONSTRAINT ck_otp_purpose CHECK (
    purpose IN ('LOGIN', 'FUND_TRANSFER', 'BILL_PAYMENT', 'BENEFICIARY_ACTIVATION', 'PASSWORD_RESET')
);

CREATE INDEX ix_otp_payment_intent ON otp_verification (user_id, purpose, otp_status, created_at DESC);

PROMPT Payment workflow hardening schema changes completed successfully.
WHENEVER SQLERROR CONTINUE NONE
