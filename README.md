# ORACLE INTERNATIONAL BANK (OIB)

Oracle JET frontend and independently runnable Spring Boot services, with Eureka service discovery and one local Oracle Database 26ai instance per developer.

## Repository

```text
Banking_App/
├── frontend/                     # Oracle JET application
├── platform/
│   ├── service-registry/         # Eureka
│   └── api-gateway/              # Public API routing
├── services/
│   ├── identity-service/
│   ├── accounts-ledger-service/
│   ├── payments-service/
│   ├── products-service/
│   ├── notification-service/
│   └── audit-reporting-service/
├── libraries/service-runtime/    # HTTP contracts, security, discovery and events
├── database/
│   ├── bootstrap/               # Create six private Oracle schemas
│   └── legacy/                  # Archived monolith SQL; never run on new schemas
├── scripts/                      # Local configuration, startup and verification
└── pom.xml                       # Maven reactor; Java 17, Spring Boot 3.5
```

Each business service owns its entities, repositories, Flyway migrations and private schema. The shared library contains no business entities or repositories. Services exchange authenticated HTTP requests and durable events; none imports another service's implementation or queries another schema.

## Applications

| Application | Local port | Oracle schema | Responsibility |
|---|---:|---|---|
| service-registry | 8761 | — | Eureka registration and discovery |
| api-gateway | 8080 | — | Explicit public API routes |
| identity-service | 8081 | `NB_IDENTITY` | Users, roles, customer profiles, OTP and Microsoft Authenticator |
| accounts-ledger-service | 8082 | `NB_ACCOUNTS` | Accounts, balances, transactions and statements |
| payments-service | 8083 | `NB_PAYMENTS` | Beneficiaries, billers, transfers, bill payments and forex |
| products-service | 8084 | `NB_PRODUCTS` | Cards, loans, repayments and fixed and recurring deposits |
| notification-service | 8085 | `NB_NOTIFICATIONS` | In-app notifications and delivery records |
| audit-reporting-service | 8086 | `NB_AUDIT` | Audit events and administrator audit search |

All six schemas live in the same local `FREEPDB1`. Each teammate applies the same committed migrations while passwords and data remain local. Cross-service identifiers are logical references; there are no cross-schema foreign keys, grants or synonyms.

Eureka supplies service addresses. Callers require exactly one registered instance and send direct HTTP requests. There is no load-balancing client or `lb://` routing. This supports one local instance per service and does not provide high availability.

## Build and verify

```sh
./mvnw verify
node scripts/verify-architecture.mjs
node scripts/init-local.mjs
# Replace each DB_PASSWORD placeholder in .local/<service>.json.
node scripts/verify-oracle.mjs
```

Create the schema users first by running every script under `database/bootstrap/` while connected to `FREEPDB1` as a PDB administrator. On Windows, use `mvnw.cmd verify`. The Node helpers work on Windows, Linux and macOS.

The normal Java suite uses H2 for transactional tests. It does not establish Oracle compatibility. `verify-oracle.mjs` applies Flyway migrations and validates every service's Hibernate mappings against a live local Oracle instance.

Start the local backend with `node scripts/run-all-services.mjs` after local
configuration is complete. The launcher runs a clean Maven package build before
starting any service, so a checked-out source change cannot silently run an old
`target/*.jar`. If the build fails, nothing starts. The build skips test execution;
run `./mvnw verify` (or `mvnw.cmd verify` on Windows) separately for the full suite.
Direct `node scripts/run-service.mjs <service-name>` launches also perform a
fresh build; the all-services launcher builds only once for its child services.
The all-services launcher refuses to rebuild while any backend port is already
occupied, waits for each service health check, and stops its own Java processes
if one fails. Stop an existing run with Ctrl+C before starting it again; do not
run a second copy against the same JARs.

For the complete Kafka-backed stack on Windows, use one command:

```powershell
node scripts/run-all-services.mjs --kafka
```

The same launcher requires Java 17, starts or reuses Kafka from `KAFKA_HOME`
(`C:\kafka` by default on Windows), safely formats only an empty KRaft data
directory, creates and verifies the single `banking.events.v1` application
topic, then starts Eureka, all six business services and the API gateway in
dependency order. Ctrl+C stops every process that the launcher started.

## Local .env configuration

With Node 20.12 or newer, run `node scripts/export-local-env.mjs` once after
configuring `.local` to create a Git-ignored `.env` with your existing credentials.
The startup scripts load this file automatically. Shared entries apply to all
services; entries such as `NOTIFICATION_SERVICE__SMTP_PASSWORD` apply only to
that service, overriding its `.local` JSON. Never use a shared `DB_USERNAME` or
`SERVICE_TOKEN`: each service has its own schema and token. JWT private and TOTP
keys stay scoped to identity-service. Keep this file out of frontend assets.
`configure-resend.mjs` updates both local configuration files when `.env` exists.

For real OTP delivery, sign in with a customer whose registered email can receive
mail. Seed addresses ending in `.test` are dummy addresses and cannot receive
SMTP email. The `onboarding@resend.dev` sender is intended for testing delivery to
the Resend account owner's inbox. Other recipients require a verified sender domain.
The transfer limit defaults to INR 100000 and can be configured locally with
`PAYMENTS_SERVICE__TRANSFER_MAX_AMOUNT`; an available balance above the limit does
not increase the per-transfer limit. SMS defaults to the development log provider.

## Existing documentation

- [Service discovery and local startup](docs/service-discovery.md)
- [Microsoft Authenticator setup](docs/authenticator-setup.md)
- [Password reset](docs/password-reset.md)
- [OTP delivery](docs/otp-delivery.md)
- [Card controls](docs/card-api.md)
- [Loan information and payments](docs/loan-api.md)
- [Beneficiaries, transfers and bill payments](docs/payment-api.md)
- [Demonstration forex conversions](docs/forex-api.md)
- [Fixed and recurring deposits](docs/deposits.md)
- [Transaction history and statements](docs/transaction-statement-api.md)
- [Gateway authentication rate limits](docs/gateway-rate-limits.md)
- [SMTP email delivery](docs/smtp-email.md)
- [Login audit trail](docs/login-audit.md)
- [Kafka event transport](docs/kafka-events.md)

The Oracle JET application includes the customer-access shell: registration, sign-in, Microsoft Authenticator, session expiry, profile, home, and notices. The shared API client, session, and one-time-code contract is in [frontend/docs/member-1-foundation-contract.md](frontend/docs/member-1-foundation-contract.md). Account, payment, and administration screens plug into those routes. A production SMS provider, real settlement integrations, and production deployment infrastructure require separate work.
