# Beneficiaries, transfers, and bill payments

All routes require a bearer JWT. Apply the database scripts through
`database/16_loan_payment_idempotency.sql` in filename order.

| Method and path | Result |
| --- | --- |
| `POST /api/v1/beneficiaries` | Creates a pending beneficiary. |
| `GET /api/v1/beneficiaries` | Lists the current user's beneficiaries. |
| `POST /api/v1/beneficiaries/{beneficiaryId}/activation-challenges` | Sends an activation OTP after the cooling period. |
| `POST /api/v1/beneficiaries/{beneficiaryId}/activate` | Activates a beneficiary with its OTP challenge. |
| `DELETE /api/v1/beneficiaries/{beneficiaryId}` | Disables a beneficiary. |
| `GET /api/v1/billers` | Lists active billers and their amount limits. |
| `POST /api/v1/transfers/otp-challenges` | Sends a transfer OTP for an owned source account. |
| `POST /api/v1/transfers` | Performs an OTP-authorized beneficiary transfer. |
| `POST /api/v1/bill-payments/otp-challenges` | Sends a bill-payment OTP for an owned source account. |
| `POST /api/v1/bill-payments` | Performs an OTP-authorized bill payment. |

OTP challenge requests contain the complete payment intent. A transfer challenge includes the source
account, beneficiary, and amount. A bill-payment challenge includes the source account, biller, amount,
and bill reference. Changing any of those fields after requesting the OTP causes verification to fail.

Transfer, bill-payment, and loan-payment requests require a client-supplied idempotency key. Repeating an
exact completed request returns the original receipt. Reusing the key with changed details returns
`409 Conflict`. Payment debits lock the customer and source account, then create the transaction lifecycle
record and debit ledger entry in the same database transaction. Successful operations create an audit
event in that transaction and publish the customer notification only after commit.
