# Kafka event transport

Kafka is an optional transport for the existing transactional outbox and idempotent inbox. It does
not replace Oracle transactions or synchronous APIs used to validate and move money. HTTP remains
the default transport so a developer can start the backend without a Kafka broker.

## Local startup

Start an installed Apache Kafka broker or use a managed Kafka cluster. Set
`KAFKA_BOOTSTRAP_SERVERS` to its reachable host and port (the default is `127.0.0.1:9092`).
Then start the backend with Kafka enabled:

```bash
node scripts/run-all-services.mjs --kafka
```

The launcher checks the first configured bootstrap server and reports a clear error when it is not
reachable. Without `--kafka`, the existing authenticated HTTP event transport is used.

To run the live broker integration test, set `KAFKA_INTEGRATION_TEST=true` and
`EVENT_TRANSPORT=kafka` for the test process, with a broker reachable through
`KAFKA_BOOTSTRAP_SERVERS`. Ordinary Maven test runs skip this test.

## Topics

| Topic | Producer | Consumer | Partition key |
| --- | --- | --- | --- |
| `banking.customer-events.v1` | Identity | Accounts ledger | Customer or user ID |
| `banking.notification-commands.v1` | Business services | Notification | User ID |
| `banking.audit-events.v1` | Business services | Audit reporting | User or entity ID |

Each topic has a matching `.dlt` topic. A consumer retries three times by default before publishing
the original record and failure headers to the dead-letter topic. Topics are created idempotently at
startup. Local defaults are three partitions and replication factor one; production should use at
least three brokers and set `KAFKA_REPLICATION_FACTOR=3`.

## Reliability model

1. A business transaction writes its domain changes and serialized event to `event_outbox` in the
   same Oracle transaction.
2. The dispatcher publishes the event to Kafka and only then marks the outbox row delivered.
3. The consumer writes the event ID to `event_inbox` and applies its data change in one transaction.
4. Redelivery is safe because the inbox primary key deduplicates source service and event ID.
5. Consumer failures retry and then move to the topic-specific dead-letter topic for investigation.

This provides at-least-once delivery with idempotent processing. Kafka's producer idempotence is also
enabled, but business correctness does not depend on Kafka claiming end-to-end exactly-once delivery.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `EVENT_TRANSPORT` | `http` | Set to `kafka` to use the broker |
| `KAFKA_BOOTSTRAP_SERVERS` | `127.0.0.1:9092` | Comma-separated broker addresses |
| `KAFKA_TOPIC_PARTITIONS` | `3` | Partition count used when creating topics |
| `KAFKA_REPLICATION_FACTOR` | `1` | Topic replication factor |
| `KAFKA_SEND_TIMEOUT` | `10s` | Maximum dispatcher wait for broker acknowledgement |
| `KAFKA_RETRY_ATTEMPTS` | `3` | Consumer attempts before dead-letter publication |
| `KAFKA_RETRY_INTERVAL_MS` | `1000` | Delay between consumer attempts |

Production broker authentication and TLS should be supplied through standard Spring Kafka
properties or the deployment secret manager. Never put OTP codes, passwords, access tokens, full
account numbers, SMTP credentials, or private keys in event payloads.
