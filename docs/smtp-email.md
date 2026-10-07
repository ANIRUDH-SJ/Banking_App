# SMTP email delivery

The notification service supports two email providers:

- `log` is the default. It records a masked recipient and subject without contacting an external server.
- `smtp` sends plain-text messages through Spring Mail and the configured SMTP server.

The durable notification dispatcher treats a Spring Mail exception as a failed delivery. Its normal retry policy then schedules another attempt; the SMTP adapter does not hide provider errors.
Selecting `smtp` with authentication enabled but without a username or password now prevents the
notification service from starting, rather than making every delivery fail later.

## Enable SMTP

Provide these values only to the notification service:

```sh
export NOTIFICATION_EMAIL_PROVIDER=smtp
export NOTIFICATION_EMAIL_FROM=no-reply@bank.example
export SMTP_HOST=smtp.example.com
export SMTP_PORT=587
export SMTP_USERNAME=bank-smtp-user
export SMTP_PASSWORD='replace-with-a-secret'
export SMTP_AUTH=true
export SMTP_STARTTLS_ENABLE=true
export SMTP_STARTTLS_REQUIRED=true
```

Port 587 with required STARTTLS is the secure default. For a provider that requires implicit TLS on port 465, set `SMTP_PORT=465`, `SMTP_SSL_ENABLE=true`, `SMTP_STARTTLS_ENABLE=false`, and `SMTP_STARTTLS_REQUIRED=false`.

Server identity checking is enabled by default. Do not disable `SMTP_SSL_CHECK_SERVER_IDENTITY` in production. SMTP connect, read, and write operations each time out after five seconds by default; override `SMTP_CONNECTION_TIMEOUT_MS`, `SMTP_READ_TIMEOUT_MS`, or `SMTP_WRITE_TIMEOUT_MS` when the provider requires different limits.

Set `SMTP_TEST_CONNECTION=true` when the service should verify SMTP connectivity during startup. Leave it disabled when temporary provider outages must be handled by the delivery retry queue instead of preventing application startup.

## Local mail server

To exercise the real adapter with a local SMTP capture server on port 1025:

```sh
export NOTIFICATION_EMAIL_PROVIDER=smtp
export NOTIFICATION_EMAIL_FROM=no-reply@localhost
export SMTP_HOST=localhost
export SMTP_PORT=1025
export SMTP_AUTH=false
export SMTP_STARTTLS_ENABLE=false
export SMTP_STARTTLS_REQUIRED=false
```

Use the default `log` provider when no SMTP server is running. Never commit SMTP credentials or place them in a shared service configuration file.
For local runs, put SMTP values in the ignored `.local/notification-service.json` file or supply
them as environment variables. Values in that local JSON file override environment variables.
The sender address must be authorized by the SMTP provider; a successful startup alone does not
prove inbox delivery. Send a test password-reset challenge and check the notification-service
delivery logs and the destination inbox (including spam) after configuring real credentials.

## Health and Eureka

The default `log` provider does not need an SMTP server. The mail health indicator is disabled by default, so an unavailable local SMTP server does not make Eureka report the notification service as DOWN.

When using the `smtp` provider with a configured server and credentials, set `NOTIFICATION_MAIL_HEALTH_ENABLED=true` to enable the mail health check. An SMTP outage can then mark the service DOWN; delivery failures are still handled by the notification retry flow.
