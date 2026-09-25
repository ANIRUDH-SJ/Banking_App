# Oracle ownership and migration

## Fresh local setup

Use one Oracle Database 26ai instance per teammate, with six private users/schemas in `FREEPDB1`. The scripts under `database/bootstrap/` create those users with quota and object-creation privileges. Each service applies `src/main/resources/db/migration/` to its own schema through Flyway, then Hibernate validates mappings. Each schema has its own Flyway history and version sequence.

Reference roles and demo billers are versioned data. Customer-specific data differs per teammate. Do not introduce manual table changes: write a new Flyway migration in the owning service and commit it so every teammate receives the same structure.

The bootstrap users are schema owners for local development. Deployment should separate migration privileges from runtime data privileges. No user should be granted access to another service's schema.

## Business table ownership

| Schema | Tables |
|---|---|
| `NB_IDENTITY` | `role`, `app_user`, `user_role`, `customer`, `customer_address`, `login_audit`, `otp_verification`, `otp_authorization`, `user_totp` |
| `NB_ACCOUNTS` | `bank`, `branch`, `bank_account`, `account_holder`, `bank_transaction`, `account_transaction_entry`, `bank_transaction_status_history`, `ledger_operation` |
| `NB_PAYMENTS` | `beneficiary`, `biller`, `payment_operation` |
| `NB_PRODUCTS` | `bank_card`, `loan_account`, `loan_payment`, `loan_repayment_operation` |
| `NB_NOTIFICATIONS` | `notification`, `notification_delivery` |
| `NB_AUDIT` | `audit_event` |

Each schema also owns its own `event_outbox`, `event_inbox` and Flyway history. These technical tables do not permit business data access across boundaries.

## Existing NET_BANKING_APP data

The repository migration **does not automatically move or delete existing data**. `database/legacy/` preserves the old SQL as a reference. Do not run Flyway baselining or new migrations against `NET_BANKING_APP`; the new applications expect private schemas with different workflow storage.

For disposable developer data, keep the old schema intact, bootstrap fresh schemas, register new users, and create explicit local fixtures. For any data that must be retained, perform a rehearsed offline cutover:

1. Back up the old PDB/schema and test restoration. Freeze writes, stop old applications, and record account balances, loan balances, transaction counts, pending OTPs and payment operations.
2. Create empty destination schemas and run/validate all Flyway migrations.
3. Export business rows by the ownership table above. Preserve primary IDs and logical references; never rely on matching names. Import each service's rows through its own migration connection. Handle pre-seeded roles/billers explicitly to avoid ID collisions.
4. Advance every identity generator past the imported maximum. Verify logical references using a separate offline reconciliation process; do not solve missing references by granting services access to other schemas.
5. Convert old `fund_transfer`/`bill_payment` rows into `payment_operation` records. This requires reconstructing the immutable request details, canonical fingerprint, amount/currency, source/destination snapshot and completed receipt from old transaction records. Import completed operations with their original customer request keys so a historical retry cannot debit again. Missing legacy request details require manual reconciliation; never guess them.
6. Convert completed `loan_payment` idempotency records into `loan_repayment_operation` with the original request key, fingerprint, command snapshot and `COMPLETED` state. The original repayment history must remain consistent with loan principal.
7. Do not replay old pending OTP challenges or create fresh debit operations to represent historical transactions. Stop and resolve ambiguous/in-flight payments before cutover. Imported completed workflows must return their stored receipts without posting new commands.
8. Preserve the original TOTP encryption key if importing enrolled `user_totp` secrets. Otherwise require re-enrollment through an approved account recovery process. New RSA token signing invalidates old HMAC access tokens, so users must log in again.
9. Reconcile each balance, ledger entry and loan repayment against the frozen source. Check counts, references, idempotency replays and authorization boundaries. Start new applications only after reconciliation succeeds.
10. Roll back by stopping new writers and restoring/reconciling from the approved backup. Do not run old and new applications as simultaneous writers.

An automated historical-data converter is **not included** because export contents and reconciliation requirements are not available in this repository. This is a fresh-schema application migration, not a claim that existing financial data has already been cut over.

## Local fixtures

Registration creates a user but does not automatically provision a customer profile, funded bank account, card or loan. For local testing, obtain the registered user ID, insert a fictional customer in `NB_IDENTITY`, and use that customer ID when adding account holdings, beneficiaries, cards or loans through each owning schema's connection. Use fictional data only; never copy real customer data or store PAN/CVV.

The fixture IDs must line up across services even though there are no cross-schema foreign keys. Test a customer with two local accounts, a second customer for access-denial tests, a pending/active beneficiary, a loan, and a masked card. Keep user passwords in the registration request only; password hashing is handled by identity.

## Verification

`node scripts/verify-oracle.mjs` uses the private `.local` configuration to run each service's opt-in Oracle check. Successful startup means Flyway completed and Hibernate validated that service's mapped tables. The test also checks that it has no direct grants on other business schemas. It does not replace the financial reconciliation required for imported data.

The ordinary test suite uses H2 for rollback, locking, deduplication and recovery scenarios. Oracle DDL, triggers, driver metadata and locking behaviour still require this live Oracle check. Flyway's [Oracle database support documentation](https://documentation.red-gate.com/flyway/reference/database-driver-reference/oracle-database) describes its Oracle module and SQL support.
