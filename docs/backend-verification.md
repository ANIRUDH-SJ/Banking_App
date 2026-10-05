# Backend verification after startup fixes

On Linux, run `./mvnw verify`. On Windows PowerShell, run `.\mvnw.cmd verify`. The pull-request workflow runs Maven on both platforms.

The five missing Oracle tables in the testing report require applying the existing Flyway migrations to the five service-owned schemas. Coordinate with the shared database owners before running migrations or changing credentials.

1. Run `node scripts/init-local.mjs` and fill the ignored `.local/<service>.json` files with approved connection details. Keep passwords out of Git.
2. Confirm each `DB_USERNAME` points to its private schema: `NB_IDENTITY`, `NB_ACCOUNTS`, `NB_PAYMENTS`, `NB_PRODUCTS`, `NB_NOTIFICATIONS`, or `NB_AUDIT`.
3. From the repository root, run `node scripts/verify-oracle.mjs`. It checks all six schema targets before applying Flyway migrations and running `OracleSchemaValidationTest`. Do not run it against the shared database without owner approval.
4. Review each schema's `flyway_schema_history`. Re-run with `SPRING_FLYWAY_ENABLED=false` to validate without applying migrations. In PowerShell, set `$env:SPRING_FLYWAY_ENABLED = "false"` for that session. Investigate migration errors instead of manually creating tables.

After schema validation and healthy Eureka registration, exercise login, account lookup, transfer, bill payment, and the resulting notification through the gateway. The local log email provider does not deliver to an inbox. Real SMTP and SMS tests require approved provider credentials and test recipients; see [SMTP email](smtp-email.md) and [OTP delivery](otp-delivery.md).
