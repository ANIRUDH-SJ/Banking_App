# Kafka event transport

Kafka is an optional transport for the existing transactional outbox and idempotent inbox. It does
not replace Oracle transactions or synchronous APIs used to validate and move money. HTTP remains
the default transport so a developer can start the backend without a Kafka broker.

## Local startup

Install Apache Kafka and set `KAFKA_HOME` to its installation directory. On
Windows the default is `C:\kafka`. Start Kafka, service discovery, every backend
service and the API gateway with one command:

```bash
node scripts/run-all-services.mjs --kafka
```

The launcher validates Java 17, starts or reuses the local KRaft broker, formats
only an empty uninitialized data directory, creates and verifies the single
application topic, builds fresh service JARs, and waits for every component's
health endpoint. Ctrl+C stops every process started by the launcher. Set
`KAFKA_BOOTSTRAP_SERVERS` when the broker is not at `127.0.0.1:9092`; remote
brokers must already be running. Without `--kafka`, the existing authenticated
HTTP event transport is used.

To run the live broker integration test, set `KAFKA_INTEGRATION_TEST=true` and
`EVENT_TRANSPORT=kafka` for the test process, with a broker reachable through
`KAFKA_BOOTSTRAP_SERVERS`. Ordinary Maven test runs skip this test.

## Topics

| Topic | Producers | Consumers | Partition key |
| --- | --- | --- | --- |
| `banking.events.v1` | All business services | Accounts ledger, notification and audit reporting | User, customer or entity ID |

All event families share this one application topic. Each consumer service has its own Kafka consumer
group and accepts only its configured event type; records for the other event families are acknowledged
and ignored by that group. A consumer retries three times by default. An exhausted record is stored in
that service's `event_consumer_failure` Oracle table instead of creating another Kafka topic. The topic
is created idempotently at startup. Local defaults are three partitions and replication factor one;
production should use at least three brokers and set `KAFKA_REPLICATION_FACTOR=3`.

## Reliability model

1. A business transaction writes its domain changes and serialized event to `event_outbox` in the
   same Oracle transaction.
2. The dispatcher publishes the event to Kafka and only then marks the outbox row delivered.
3. The consumer writes the event ID to `event_inbox` and applies its data change in one transaction.
4. Redelivery is safe because the inbox primary key deduplicates source service and event ID.
5. Consumer failures retry and are then stored in the consuming service's Oracle failure table for
   investigation and controlled replay.

This provides at-least-once delivery with idempotent processing. Kafka's producer idempotence is also
enabled, but business correctness does not depend on Kafka claiming end-to-end exactly-once delivery.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `EVENT_TRANSPORT` | `http` | Set to `kafka` to use the broker |
| `KAFKA_HOME` | `C:\kafka` on Windows | Local Apache Kafka installation used by the unified launcher |
| `KAFKA_CONFIG` | `$KAFKA_HOME/config/kraft/server.properties` | Local KRaft broker configuration |
| `KAFKA_BOOTSTRAP_SERVERS` | `127.0.0.1:9092` | Comma-separated broker addresses |
| `KAFKA_EVENT_TOPIC` | `banking.events.v1` | The single application event topic |
| `KAFKA_TOPIC_PARTITIONS` | `3` | Partition count used when creating topics |
| `KAFKA_REPLICATION_FACTOR` | `1` | Topic replication factor |
| `KAFKA_SEND_TIMEOUT` | `10s` | Maximum dispatcher wait for broker acknowledgement |
| `KAFKA_RETRY_ATTEMPTS` | `3` | Consumer attempts before Oracle failure storage |
| `KAFKA_RETRY_INTERVAL_MS` | `1000` | Delay between consumer attempts |

Production broker authentication and TLS should be supplied through standard Spring Kafka
properties or the deployment secret manager. Never put OTP codes, passwords, access tokens, full
account numbers, SMTP credentials, or private keys in event payloads.
