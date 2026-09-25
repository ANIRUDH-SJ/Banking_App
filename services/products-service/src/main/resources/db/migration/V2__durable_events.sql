CREATE TABLE event_outbox (
    event_id VARCHAR2(36 CHAR) PRIMARY KEY,
    destination VARCHAR2(50 CHAR) NOT NULL,
    envelope CLOB NOT NULL,
    attempts NUMBER(10) DEFAULT 0 NOT NULL,
    next_attempt_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    delivered_at TIMESTAMP(6),
    created_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL
);
CREATE INDEX ix_outbox_pending ON event_outbox(delivered_at,next_attempt_at);
CREATE TABLE event_inbox (
    source_service VARCHAR2(50 CHAR) NOT NULL,
    event_id VARCHAR2(36 CHAR) NOT NULL,
    received_at TIMESTAMP(6) DEFAULT SYSTIMESTAMP NOT NULL,
    CONSTRAINT pk_event_inbox PRIMARY KEY(source_service,event_id)
);
