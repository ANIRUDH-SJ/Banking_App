# Teammate setup

Each teammate runs one Oracle Database 26ai instance locally. Inside `FREEPDB1`, six schemas separate service ownership. Share migration files through Git; keep passwords, RSA private keys, TOTP encryption keys and database volumes private.

## Prerequisites

- Java 17 and `JAVA_HOME`; the repository includes the Maven wrapper.
- Node.js compatible with the installed Oracle JET CLI (Node 22 LTS is a practical team baseline), npm and Git.
- A local Oracle Database 26ai installation with `FREEPDB1` reachable on port 1521.
- SQLcl, SQL*Plus or SQL Developer to run bootstrap scripts.
- Enough memory for Oracle plus eight Java processes. The startup helper limits each Java heap to 256 MB; adjust locally if needed.

Oracle may run natively or in your existing local container. The Java services do not require Docker Compose. There is no hosted or team-shared database.

## Create schemas once

Connect to **FREEPDB1**, not `CDB$ROOT`, as a PDB administrator. Run each script with SQLcl/SQL*Plus, or SQL Developer's **Run Script / F5**:

```text
database/bootstrap/nb_identity.sql
database/bootstrap/nb_accounts.sql
database/bootstrap/nb_payments.sql
database/bootstrap/nb_products.sql
database/bootstrap/nb_notifications.sql
database/bootstrap/nb_audit.sql
```

Each script prompts for that schema's password. Choose different private passwords, without double quotes or ampersands for SQL*Plus substitution. No cross-schema grants or synonyms are created. These scripts intentionally fail if the user already exists; do not drop an existing schema to retry them.

## Configure and build

From the repository root:

```sh
node scripts/init-local.mjs
```

Edit the generated `.local/<service>.json` files and replace the six `DB_PASSWORD` placeholders with the matching schema passwords. If Oracle uses a different address, update `DB_URL` in each business service file.

The generator creates RSA signing keys, one outgoing credential per service, hashes of incoming caller credentials, Eureka credentials, and an AES key for TOTP encryption. Only identity receives the signing private key and AES key. It refuses to overwrite `.local` because replacing keys would invalidate enrolled authenticators. Unix file modes are restricted; on Windows keep the directory accessible only to your user.

```sh
./mvnw verify
node scripts/verify-architecture.mjs
node scripts/verify-oracle.mjs
```

In PowerShell, use `./mvnw.cmd verify` for the first command. Oracle verification applies migrations to the configured service schemas and starts each service's application context with scheduling and discovery disabled. Run this before considering a local database setup validated. Flyway records checksums; never edit a migration after teammates have applied it. Add a new version instead.

The normal unit/integration suite uses H2 and skips the six Oracle-only checks unless explicitly enabled. It does not need `.local` files or an Oracle instance.

## Start applications

Run each command in a separate terminal from the repository root, in this order:

```sh
node scripts/run-service.mjs service-registry
node scripts/run-service.mjs identity-service
node scripts/run-service.mjs accounts-ledger-service
node scripts/run-service.mjs audit-reporting-service
node scripts/run-service.mjs notification-service
node scripts/run-service.mjs payments-service
node scripts/run-service.mjs products-service
node scripts/run-service.mjs api-gateway
```

Open `http://localhost:8761` and sign in using `.local/service-registry.json`. Allow time for registration and discovery caches to update. There should be one instance of every business service plus the gateway.

Each service runs its own Flyway migrations before Hibernate validates its tables. No application connects as `SYS`, `SYSTEM`, or the old `NET_BANKING_APP` user. Services bind to loopback by default.

For an individual service build, use `./mvnw -pl :payments-service -am verify`. Only the shared library and that application are required at build time. Its required peers must still be running for operations that call them.

## Frontend

```sh
cd frontend
npm ci
npx ojet restore
npx ojet serve
```

Use `http://localhost:8080/api/v1` as the API base URL for future frontend services. Keep browser requests on the gateway; do not put Eureka credentials or internal service tokens in JavaScript. The current JET application remains a scaffold; API integration and banking screens are separate frontend work.

## Local authentication and data

Registration creates an application user. Customer profiles, account holdings, card and loan fixtures still require explicit provisioning. See [database setup and migration](database-migration.md) for ownership and fixture rules; an empty customer account list is not evidence of a discovery failure.

Microsoft Authenticator enrollment and login remain under `/api/v1/auth`. The generated identity configuration selects the `local` profile for its existing development OTP delivery adapter. That adapter prints payment OTPs to the local console: do not collect these logs or use this adapter with real users. A real delivery adapter is required outside local development.

The sample biller catalogue is installed by Flyway. There are no seeded customer passwords, cards with real PANs, or pre-funded real accounts.

## Configuration notes

`.env.example` lists the environment contract; Spring Boot does not load `.env` automatically. The Node startup helper explicitly loads the private JSON file and passes its values to the Java child process. The generated files take precedence over inherited environment values.

For deployment, supply configuration through your secret manager/environment and launch each packaged jar directly. Use HTTPS, isolate database and internal ports, give each runtime account only its required data permissions, and apply migrations with a separate schema owner. Configure `spring.flyway.schemas` and Hibernate's default schema if the runtime account is not the owner. Do not enable Flyway baselining against the old combined schema.
