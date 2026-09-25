# Internet Banking

Oracle JET frontend and independently packaged Spring Boot services with Eureka service discovery.

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
├── database/                     # Existing Oracle schema scripts
├── scripts/                      # Local configuration, startup and verification
└── pom.xml                       # Maven reactor; Java 17, Spring Boot 3.5
```

Each business service owns its entities, repositories and API. The shared library contains no business entities or repositories. Services exchange authenticated HTTP requests and durable events; none imports another service's implementation.

## Applications

| Application | Local port | Responsibility |
|---|---:|---|
| service-registry | 8761 | Eureka registration and discovery |
| api-gateway | 8080 | Explicit public API routes |
| identity-service | 8081 | Users, roles, customer profiles, OTP and Microsoft Authenticator |
| accounts-ledger-service | 8082 | Accounts, balances, transactions and statements |
| payments-service | 8083 | Beneficiaries, billers, transfers and bill payments |
| products-service | 8084 | Cards, loans and repayments |
| notification-service | 8085 | In-app notifications and delivery records |
| audit-reporting-service | 8086 | Audit events and administrator audit search |

Eureka supplies service addresses. Callers require exactly one registered instance and send direct HTTP requests. There is no load-balancing client or `lb://` routing. This supports one local instance per service and does not provide high availability.

## Build and verify

```sh
./mvnw verify
node scripts/verify-architecture.mjs
node scripts/init-local.mjs
```

On Windows, use `mvnw.cmd verify`. The Node helpers work on Windows, Linux and macOS. Database schema provisioning and Oracle runtime configuration are delivered separately from the service architecture.

## Existing documentation

- [Service discovery and local startup](docs/service-discovery.md)
- [Microsoft Authenticator setup](docs/authenticator-setup.md)
- [Card controls](docs/card-api.md)
- [Loan information and payments](docs/loan-api.md)
- [Beneficiaries, transfers and bill payments](docs/payment-api.md)
- [Transaction history and statements](docs/transaction-statement-api.md)

The Oracle JET application remains a scaffold. Customer screens, production SMTP/SMS providers, real settlement integrations and production deployment infrastructure require separate work.
