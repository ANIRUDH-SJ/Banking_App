-- Bind loan payment idempotency keys to their original request details.
-- Run after 15_payment_workflow_hardening.sql as NET_BANKING_APP.
SET ECHO ON
SET FEEDBACK ON
WHENEVER SQLERROR EXIT SQL.SQLCODE

ALTER TABLE loan_payment ADD (
    request_fingerprint VARCHAR2(64 CHAR)
);

PROMPT Loan payment idempotency changes completed successfully.
WHENEVER SQLERROR CONTINUE NONE
