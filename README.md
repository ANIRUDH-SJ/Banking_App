# Internet Banking

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
├── libraries/service-runtime/    # HTTP contracts, security, discovery, durable events
├── database/
│   ├── bootstrap/               # Create six private Oracle schemas
│   └── legacy/                  # Archived monolith SQL; never run on new schemas
├── scripts/                     # Local configuration, startup and verification
├── docs/
└── pom.xml                      # Maven reactor; Java 17, Spring Boot 3.5
```

Each business service owns its entities, repositories, database migrations and schema. The shared library contains no business entities or repositories. Services exchange authenticated HTTP requests and durable events; none imports another service's implementation.

## Applications and data ownership

| Application | Local port | Oracle schema | Owns |
|---|---:|---|---|
| service-registry | 8761 | — | Eureka registration and discovery |
| api-gateway | 8080 | — | Explicit public API routes |
| identity-service | 8081 | `NB_IDENTITY` | Users, roles, customer profiles, OTP, Microsoft Authenticator |
| accounts-ledger-service | 8082 | `NB_ACCOUNTS` | Banks, branches, account ownership, balances, transactions, statements |
| payments-service | 8083 | `NB_PAYMENTS` | Beneficiaries, billers, transfer and bill workflows |
| products-service | 8084 | `NB_PRODUCTS` | Cards, loans, repayment workflows |
| notification-service | 8085 | `NB_NOTIFICATIONS` | In-app notifications and delivery records |
| audit-reporting-service | 8086 | `NB_AUDIT` | Audit events and administrator audit search |

**Six business services, eight backend applications, one frontend.** All six schemas live in the same local `FREEPDB1`. Every teammate applies the same Flyway migrations; passwords and data remain local.

Eureka supplies service addresses. Callers require exactly one registered instance and send direct HTTP requests. There is no load-balancing client or `lb://` routing. This deliberately supports one instance per service; it does not provide high availability.

## Start and verify

See [teammate setup](docs/TEAMMATE_QUICK_START.md) for Oracle schema creation and startup.

```sh
./mvnw verify
node scripts/verify-architecture.mjs
node scripts/init-local.mjs
# Set DB_PASSWORD in each .local/<service>.json before starting services.
node scripts/run-service.mjs service-registry
```

Run each remaining application in a separate terminal. On Windows, use `mvnw.cmd verify`. The Node helpers work on Windows, Linux and macOS.

The normal Java suite uses H2 for transactional tests. **It does not establish Oracle compatibility.** With a local Oracle instance and fresh private schemas configured, run `node scripts/verify-oracle.mjs` to apply Flyway migrations and validate every service's Hibernate mappings against Oracle.

## Documentation

- [Architecture, consistency and internal contracts](docs/microservices-architecture.md)
- [Local startup and configuration](docs/TEAMMATE_QUICK_START.md)
- [Eureka discovery and troubleshooting](docs/service-discovery.md)
- [Existing data migration and cutover](docs/database-migration.md)
- [Frontend work division](docs/frontend-work-division.md)
- [Microsoft Authenticator](docs/authenticator-setup.md)
- [Transfers and bill payments](docs/payment-api.md)
- [Transactions and statements](docs/transaction-statement-api.md)
- [Cards](docs/card-api.md) and [loans](docs/loan-api.md)

The existing Oracle JET scaffold is preserved. This migration does not complete unfinished customer screens, administration features, SMTP/SMS providers, or real settlement integrations. Bill payments remain simulated, and transfers to accounts outside this bank are rejected before any debit. Review the deployment limitations in the architecture guide before using real customer data.
