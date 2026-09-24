# Beneficiaries, transfers, and bill payments

All routes require a bearer JWT. Run `database/14_payment_feature_tables.sql` after the existing
beneficiary, transaction, TOTP, transaction-status-history, and notification scripts.

| Method and path | Result |
| --- | --- |
| `POST /api/v1/beneficiaries` | Creates a pending beneficiary. |
| `GET /api/v1/beneficiaries` | Lists the current user's beneficiaries. |
| `POST /api/v1/beneficiaries/{beneficiaryId}/activate` | Activates a beneficiary. |
| `DELETE /api/v1/beneficiaries/{beneficiaryId}` | Disables a beneficiary. |
| `GET /api/v1/billers` | Lists active billers and their amount limits. |
| `POST /api/v1/transfers/otp-challenges` | Sends a transfer OTP for an owned source account. |
| `POST /api/v1/transfers` | Performs an OTP-authorized beneficiary transfer. |
| `POST /api/v1/bill-payments/otp-challenges` | Sends a bill-payment OTP for an owned source account. |
| `POST /api/v1/bill-payments` | Performs an OTP-authorized bill payment. |

Transfer and bill-payment requests require a client-supplied idempotency key. Repeating a completed
request with the same user and key returns the original receipt. Payment debits lock the source account,
then create and complete a transaction lifecycle record and its debit ledger entry in the same database
transaction.
