ALTER TABLE payment_operation ADD (next_attempt_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL);
CREATE INDEX ix_workflow_due ON payment_operation(state,next_attempt_at);
