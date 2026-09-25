# Beneficiaries, transfers, and bill payments

All routes require a bearer JWT and are reached through the gateway. Payments owns its schema and Flyway migrations.

| Method and path | Result |
| --- | --- |
| `POST /api/v1/beneficiaries` | Creates a pending beneficiary. |
| `GET /api/v1/beneficiaries` | Lists the current user's beneficiaries. |
| `POST /api/v1/beneficiaries/{beneficiaryId}/activation-challenges` | Sends an activation OTP for a pending beneficiary after the cooling period. |
| `POST /api/v1/beneficiaries/{beneficiaryId}/activate` | Activates a beneficiary with its OTP challenge. |
| `DELETE /api/v1/beneficiaries/{beneficiaryId}` | Disables a beneficiary. |
| `GET /api/v1/billers` | Lists active billers and their amount limits. |
| `POST /api/v1/transfers/otp-challenges` | Sends a transfer OTP for an owned source account. |
| `GET /api/v1/transfers` | Latest 100 customer transfer workflows, including pending states. |
| `GET /api/v1/bill-payments` | Latest 100 customer bill-payment workflows. |
| `POST /api/v1/transfers` | Performs an OTP-authorized beneficiary transfer. |
| `POST /api/v1/bill-payments/otp-challenges` | Sends a bill-payment OTP for an owned source account. |
| `POST /api/v1/bill-payments` | Performs an OTP-authorized bill payment. |

OTP challenge requests contain the complete payment intent. A transfer challenge includes the source
account, beneficiary, and amount. A bill-payment challenge includes the source account, biller, amount,
and bill reference. Changing any of those fields after requesting the OTP causes verification to fail.

Transfer, bill-payment, and loan-payment requests require a client-supplied idempotency key. Repeating an
exact completed request returns the original receipt. Reusing the key with changed details returns
`409 Conflict`. Payments persists intent and authorization state before sending an idempotent ledger command.
Accounts-ledger owns the account locks, balances, ledger entries and stable transaction receipt.
A successful workflow queues durable audit and notification events. Delivery is eventual and deduplicated.

Network timeouts return `503` and do not prove that a debit failed. Retry the exact request with the same
idempotency key; background recovery also resumes authorized operations. History can show
`AWAITING_OTP`, `AUTHORIZED`, `COMPLETED`, or `FAILED`. Pending rows have null transaction IDs/references.

Transfers accept only accounts held by this bank and validate the destination IFSC; external destinations
are rejected before debit. Bill payments remain simulated. See [service contracts and recovery](microservices-architecture.md).
