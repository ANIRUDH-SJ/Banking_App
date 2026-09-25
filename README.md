# Internet Net Banking Application 

Team project repository for the Internet Net Banking Application

Backend API documentation:

- [Service discovery and local startup](docs/service-discovery.md)
- [Microsoft Authenticator setup](docs/authenticator-setup.md)
- [Card controls](docs/card-api.md)
- [Loan information and payments](docs/loan-api.md)
- [Beneficiaries, transfers, and bill payments](docs/payment-api.md)
- [Transaction history and statements](docs/transaction-statement-api.md)

## Backend verification

Run the clean Java test suites from the repository root:

```bash
cd backend && ./mvnw clean test
cd ../service-registry && ./mvnw clean test
```

The backend suite includes a live Oracle schema check that activates when these environment variables are
present: `ORACLE_SCHEMA_VALIDATION_URL`, `ORACLE_SCHEMA_VALIDATION_USERNAME`, and
`ORACLE_SCHEMA_VALIDATION_PASSWORD`. Point them at a disposable schema after applying every script in
`database/`, then run the backend tests. The password is read only from the environment.
