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

Transfers and bill payments can be authorized with the PIN of the active debit card linked to the paying
account instead of an OTP: send `cardPin` (`cardId`, `pinKeyId`, `encryptedPin`) and omit
`otpChallengeId`/`otpCode`. Exactly one method is accepted. See [card PINs](card-api.md#pins). A wrong OTP
or PIN returns `422` with a field error (`otpCode` or `pin`); a locked PIN returns `423`. Neither returns
`401`, which clients treat as an expired session.

`PATCH /api/v1/beneficiaries/{id}` with `{"nickname":"Mom"}` renames an owned beneficiary without changing its
account, IFSC or activation. Payment history rows include `beneficiaryNickname` and `billerName`.

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

## External transfer adapter

When the beneficiary account is not held by a local branch, the ledger routes the transfer through
`ExternalTransferAdapter`. The first-release implementation uses the `mock` provider and records its
provider reference in `external_transfer_dispatch` before the local transaction commits. The adapter
contract requires `operationId` to be used as the provider idempotency key. A repeated dispatch with
the same operation and instructions must return the original provider receipt; reuse with different
instructions must be rejected.

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `EXTERNAL_TRANSFER_PROVIDER` | `mock` | Selects the configured adapter implementation. |
| `EXTERNAL_TRANSFER_MOCK_FAILURES` | `0` | Makes the mock return HTTP 503 this many times per operation before succeeding. |

HTTP 5xx adapter failures leave the payment authorized and eligible for the existing recovery worker.
The ledger transaction rolls back the source debit, transaction row, and dispatch row until an adapter
attempt succeeds. A destination using a known local IFSC but an unknown local account is rejected and
is never sent to an external adapter.
