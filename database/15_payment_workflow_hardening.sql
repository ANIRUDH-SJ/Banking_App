-- Security and consistency changes for beneficiary and payment workflows.
-- Run after 14_payment_feature_tables.sql as NET_BANKING_APP.
SET ECHO ON
SET FEEDBACK ON
WHENEVER SQLERROR EXIT SQL.SQLCODE

ALTER TABLE otp_verification ADD (
    intent_digest VARCHAR2(64 CHAR),
    version NUMBER(19) DEFAULT 0 NOT NULL
);

ALTER TABLE fund_transfer ADD (
    request_fingerprint VARCHAR2(64 CHAR)
);

ALTER TABLE bill_payment ADD (
    request_fingerprint VARCHAR2(64 CHAR)
);

ALTER TABLE biller ADD (
    reference_pattern VARCHAR2(200 CHAR)
);

UPDATE biller SET reference_pattern = '^[0-9]{6,20}$' WHERE biller_code = 'ELECTRICITY';
UPDATE biller SET reference_pattern = '^[0-9]{10}$' WHERE biller_code = 'MOBILE';
UPDATE biller SET reference_pattern = '^[A-Za-z0-9][A-Za-z0-9-]{4,29}$' WHERE biller_code = 'WATER';

ALTER TABLE otp_verification DROP CONSTRAINT ck_otp_purpose;
ALTER TABLE otp_verification ADD CONSTRAINT ck_otp_purpose CHECK (
    purpose IN ('LOGIN', 'FUND_TRANSFER', 'BILL_PAYMENT', 'BENEFICIARY_ACTIVATION', 'PASSWORD_RESET')
);

CREATE INDEX ix_otp_payment_intent
    ON otp_verification (user_id, purpose, otp_status, created_at DESC);

COMMIT;
PROMPT Payment workflow security changes completed successfully.
WHENEVER SQLERROR CONTINUE NONE
