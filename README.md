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
| payments-service | 8083 | `NB_PAYMENTS` | Beneficiaries, billers, transfers and bill payments |
| products-service | 8084 | `NB_PRODUCTS` | Cards, loans and repayments |
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

## Existing documentation

- [Service discovery and local startup](docs/service-discovery.md)
- [Microsoft Authenticator setup](docs/authenticator-setup.md)
- [Card controls](docs/card-api.md)
- [Loan information and payments](docs/loan-api.md)
- [Beneficiaries, transfers and bill payments](docs/payment-api.md)
- [Transaction history and statements](docs/transaction-statement-api.md)

The Oracle JET application remains a scaffold. Customer screens, production SMTP/SMS providers, real settlement integrations and production deployment infrastructure require separate work.
