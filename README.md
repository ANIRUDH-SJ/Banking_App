# ORACLE INTERNATIONAL BANK (OIB)

An internet-banking demonstration with an Oracle JET frontend, Java 17 / Spring Boot 3.5 microservices, Eureka discovery, and service-owned Oracle schemas. It is built for local development and learning, **not for real-money banking**.

## What the application does

| Area | Customer or administrator capabilities | Owning service |
| --- | --- | --- |
| Identity | Registration, password and Microsoft Authenticator sign-in, password recovery, profile, login lockout | `identity-service` |
| Accounts | Account details, balances, masked/revealed account numbers, foreign-currency wallets, transactions and statements | `accounts-ledger-service` |
| Payments | Beneficiaries, transfers, bill payments and history; OTP or linked debit-card PIN authorization | `payments-service` |
| Cards | Activate/block/unblock, short-lived number reveal, PIN setup/change, credit-card transactions and eligible purchase-to-EMI conversion | `products-service` |
| Loans | View loan terms, repay an existing loan from an owned account, inspect repayment history | `products-service` |
| FD and RD | Quote and open deposits, collect due RD instalments, maturity payout, OTP-confirmed early closure | `products-service` |
| Forex | View demonstration rates, open currency wallets, obtain a short-lived quote, OTP-confirm a conversion and view history | `payments-service` and `accounts-ledger-service` |
| Notices | In-app notifications and configurable email/SMS delivery | `notification-service` |
| Administration | Search users, accounts, transactions, loans and audit events; change permitted user/account statuses | Identity, accounts, products and audit services |

Administrators have separate, role-protected routes. The admin dashboard is for oversight, not loan origination or deposit/forex administration.

## How a request moves through the system

```text
Oracle JET screen -> frontend API client -> API Gateway (:8080)
    -> owning Spring Boot service -> its own Oracle schema
    -> authenticated internal service call when needed
       -> accounts-ledger-service for money movement
```

The gateway routes public `/api/v1/**` requests to the owning service. Customer JWTs, ADMIN role checks and ownership checks protect access; internal calls use service credentials. For example, an FD opening starts with a quote in products-service, then asks accounts-ledger-service to debit the funding account and record a statement transaction. An FX conversion asks the ledger to debit one wallet and credit another in one ledger transaction. Client idempotency keys and stable ledger operation IDs make retries safe after uncertain responses.

Each business service owns its entities, repositories, Flyway migrations and private Oracle schema. The shared `libraries/service-runtime` module contains security, HTTP contracts, discovery and event infrastructure, not another service's business tables. Eureka resolves service addresses; this local setup expects one healthy instance per service and is not a high-availability deployment.

Business events use a transactional outbox and idempotent inbox. Authenticated HTTP delivery is the default. Kafka is optional and uses the single `banking.events.v1` topic; it does **not** replace synchronous ledger calls or Oracle transactions. See [Kafka event transport](docs/kafka-events.md).

## Repository layout

```text
frontend/                          Oracle JET screens, view models, API services and tests
platform/api-gateway/              Public API routing and authentication rate limits
platform/service-registry/         Eureka registry
services/identity-service/         Users, profiles, authentication and OTP
services/accounts-ledger-service/  Accounts, balances, ledger and statements
services/payments-service/         Payments, beneficiaries, billers and forex
services/products-service/         Cards, loans, fixed and recurring deposits
services/notification-service/     In-app notices and email/SMS adapters
services/audit-reporting-service/  Audit search
libraries/service-runtime/         Shared service infrastructure and contracts
database/bootstrap/                One-time creation of six Oracle schema users
database/legacy/                   Archived monolith SQL; do not run for this architecture
scripts/                           Local configuration, builds, startup and checks
pom.xml                            Maven reactor
```

## Run locally (without containers)

### 1. Prerequisites

- Java 17 and Node.js 20.12 or newer. The Maven wrapper is included; a separate Maven install is unnecessary.
- A local Oracle database with `FREEPDB1` reachable at `127.0.0.1:1521`, plus PDB administrator access for one-time schema setup. Adjust each local `DB_URL` if your listener differs.
- Optional: a local Apache Kafka installation for Kafka-mode events. Ordinary HTTP event mode needs no broker.

From the repository root, run each script in `database/bootstrap/` once against `FREEPDB1` as a PDB administrator using SQLcl or SQL*Plus. They create `NB_IDENTITY`, `NB_ACCOUNTS`, `NB_PAYMENTS`, `NB_PRODUCTS`, `NB_NOTIFICATIONS` and `NB_AUDIT`. Choose private local passwords. Do **not** run scripts from `database/legacy/` on these schemas.

### 2. Configure private local credentials

```sh
node scripts/init-local.mjs
```

This creates ignored `.local/<application>.json` files with local service tokens and cryptographic keys. Replace `DB_PASSWORD` in each of the six business-service files with that schema's password. **Run `init-local.mjs` only once**: it deliberately refuses to overwrite an existing `.local` directory, whose keys and enrolled authenticators must be preserved. Never commit or share `.local`, `.env`, passwords, tokens or email API keys.

Optionally, after configuring `.local`, run `node scripts/export-local-env.mjs` to create an ignored `.env` for service-scoped overrides. For example, `NOTIFICATION_SERVICE__SMTP_PASSWORD` belongs only to notification-service; never set one shared database username, password or service token for every service. The startup scripts load `.env` automatically.

Validate migrations and Hibernate mappings against your local Oracle before starting:

```sh
node scripts/verify-oracle.mjs
```

### 3. Start the backend

```sh
node scripts/run-all-services.mjs
```

This performs a fresh Maven JAR build, starts Eureka, the six business services and the gateway, and waits for their health checks. It refuses to rebuild while a backend port is occupied. Stop the run with Ctrl+C before starting it again. On Windows the launcher uses `mvnw.cmd`; a direct one-service launch is `node scripts/run-service.mjs <service-name>`.

For optional Kafka mode, install Apache Kafka, set `KAFKA_HOME` to its installation directory, then use `node scripts/run-all-services.mjs --kafka`. The launcher starts or reuses a local KRaft broker and creates/verifies `banking.events.v1`. The normal command above remains the simpler default. Neither mode requires Docker, Podman or GitHub Actions.

Check the gateway at `http://localhost:8080/api/v1/health` and Eureka at `http://localhost:8761`. The service ports are:

| Application | Port | Oracle schema |
| --- | ---: | --- |
| service-registry | 8761 | — |
| api-gateway | 8080 | — |
| identity-service | 8081 | `NB_IDENTITY` |
| accounts-ledger-service | 8082 | `NB_ACCOUNTS` |
| payments-service | 8083 | `NB_PAYMENTS` |
| products-service | 8084 | `NB_PRODUCTS` |
| notification-service | 8085 | `NB_NOTIFICATIONS` |
| audit-reporting-service | 8086 | `NB_AUDIT` |

### 4. Start the frontend in another terminal

```sh
cd frontend
npm ci
npm run serve
```

Open `http://localhost:8000`. The Oracle JET development server proxies `/api/**` to the gateway on `127.0.0.1:8080`, so start the backend first. If the gateway uses another host or port, set `NET_BANKING_API_HOST` or `NET_BANKING_API_PORT` for the frontend process.

## Build and test

From the repository root:

```sh
./mvnw verify
node scripts/verify-architecture.mjs
```

On Windows use `mvnw.cmd verify`. From `frontend/` run:

```sh
npm ci
npm test
npx ojet build web
```

The normal Java suite uses H2 for transactional tests; it does not prove Oracle compatibility. Run `node scripts/verify-oracle.mjs` separately with a configured local Oracle. The backend launcher builds fresh JARs but skips test execution, so run `verify` when validating changes. No GitHub workflow is required to build or test locally.

## Email and other demonstration boundaries

Notification email defaults to `log`: it records delivery activity locally but **does not send to an inbox**. Configure SMTP in the private notification-service settings to send real email; [SMTP setup](docs/smtp-email.md) covers Resend and other SMTP providers. Seed customer addresses ending in `.test` cannot receive external mail. SMS also defaults to a local log adapter.

Forex rates are operator-configured demonstration rates, not a live market feed. FD/RD rates and premature-closure rules are demo policy. The default biller and external-transfer integrations are mocks, and demo card issuance is not a card-network integration. This repository does not provide production reconciliation, real external settlement, regulated product disclosures or deployment hardening. Do not use it for actual customer funds.

## Feature guides

- [Microsoft Authenticator setup](docs/authenticator-setup.md), [password reset](docs/password-reset.md) and [OTP delivery](docs/otp-delivery.md)
- [Account transactions and statements](docs/transaction-statement-api.md)
- [Beneficiaries, transfers and bill payments](docs/payment-api.md)
- [Cards, PINs and credit-card EMI](docs/card-api.md)
- [Fixed and recurring deposits, including early closure](docs/deposits.md)
- [Demonstration forex conversions](docs/forex-api.md)
- [Gateway authentication rate limits](docs/gateway-rate-limits.md) and [login audit trail](docs/login-audit.md)
- [Kafka event transport](docs/kafka-events.md) and [SMTP email delivery](docs/smtp-email.md)

Some older documents in `docs/` describe the pre-microservice prototype; use this README and the service-owned Flyway migrations for current local setup.
