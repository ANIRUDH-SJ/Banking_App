# Beneficiaries, transfers, and bill payments

All routes require a bearer JWT. Service deployments apply the payment schema through Flyway. The
older single-schema deployment uses `database/legacy/14_payment_feature_tables.sql` and its
follow-up hardening scripts.

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

Transfer and bill-payment requests require a client-supplied idempotency key. Repeating a terminal
request with the same user and key returns the original receipt. Payment debits lock the source
account, then create and complete a transaction lifecycle record and its debit ledger entry in the
same database transaction.

Bill payments persist the debit before collecting through the configured biller adapter. Provider
acceptance completes the workflow. A definitive provider rejection creates an idempotent reversal,
credits the source account, and returns a `REVERSED` receipt. Timeouts and temporary provider failures
remain recoverable and are retried with the original operation key.

The default `mock` adapter accepts payments idempotently. Local failure and rejection paths can be
exercised with `BILLER_PAYMENT_MOCK_FAILURES` and `BILLER_PAYMENT_MOCK_REJECT_PREFIX`; production
deployments should select a real adapter with `BILLER_PAYMENT_PROVIDER`.
