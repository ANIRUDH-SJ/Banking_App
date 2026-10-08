CREATE TABLE event_consumer_failure (
    topic_name       VARCHAR2(200 CHAR) NOT NULL,
    event_partition  NUMBER(10) NOT NULL,
    event_offset     NUMBER(19) NOT NULL,
    event_key        VARCHAR2(200 CHAR),
    payload          CLOB NOT NULL,
    failure_reason   VARCHAR2(1000 CHAR) NOT NULL,
    failed_at        TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP NOT NULL,
    resolved_at      TIMESTAMP(6),
    CONSTRAINT pk_event_consumer_failure
        PRIMARY KEY (topic_name, event_partition, event_offset)
);
