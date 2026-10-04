ALTER TABLE payment_operation ADD (
    provider_reference VARCHAR2(100 CHAR),
    provider_status VARCHAR2(20 CHAR),
    reversal_transaction_id NUMBER(19),
    reversal_transaction_reference VARCHAR2(50 CHAR)
);

ALTER TABLE payment_operation DROP CONSTRAINT ck_payment_state;
ALTER TABLE payment_operation ADD CONSTRAINT ck_payment_state CHECK (
    state IN ('AWAITING_OTP','AUTHORIZED','DEBITED','COMPLETED','FAILED','REVERSED')
);

ALTER TABLE payment_operation DROP CONSTRAINT ck_payment_receipt;
UPDATE payment_operation
SET provider_reference = 'LEGACY-' || operation_key,
    provider_status = 'ACCEPTED'
WHERE kind = 'BILL_PAYMENT' AND state = 'COMPLETED';

ALTER TABLE payment_operation ADD CONSTRAINT ck_payment_receipt CHECK (
    (state NOT IN ('DEBITED','COMPLETED','REVERSED')
        OR (transaction_id IS NOT NULL AND transaction_reference IS NOT NULL))
    AND (state <> 'REVERSED'
        OR (reversal_transaction_id IS NOT NULL
            AND reversal_transaction_reference IS NOT NULL))
    AND (kind <> 'BILL_PAYMENT' OR state NOT IN ('COMPLETED','REVERSED')
        OR (provider_reference IS NOT NULL AND provider_status IS NOT NULL))
);

ALTER TABLE payment_operation ADD CONSTRAINT uq_payment_reversal_txn
    UNIQUE (reversal_transaction_id);
ALTER TABLE payment_operation ADD CONSTRAINT uq_payment_reversal_ref
    UNIQUE (reversal_transaction_reference);
