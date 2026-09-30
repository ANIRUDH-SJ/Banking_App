CREATE INDEX ix_payment_user_created
    ON payment_operation(user_id, created_at DESC, payment_id DESC);

CREATE INDEX ix_payment_user_filters
    ON payment_operation(user_id, kind, state, created_at DESC);
