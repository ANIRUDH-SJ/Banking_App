CREATE INDEX ix_audit_type_time
    ON audit_event(event_type, occurred_at DESC);

CREATE INDEX ix_audit_outcome_time
    ON audit_event(outcome, occurred_at DESC);
