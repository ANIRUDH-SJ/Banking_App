CREATE INDEX ix_login_audit_user_time
    ON login_audit (user_id, occurred_at);
