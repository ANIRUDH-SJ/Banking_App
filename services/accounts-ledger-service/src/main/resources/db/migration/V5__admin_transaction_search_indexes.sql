CREATE INDEX ix_bank_txn_status_time
    ON bank_transaction(transaction_status, initiated_at DESC);

CREATE INDEX ix_bank_txn_type_time
    ON bank_transaction(transaction_type, initiated_at DESC);

CREATE INDEX ix_bank_txn_user_time
    ON bank_transaction(initiated_by_user_id, initiated_at DESC);
