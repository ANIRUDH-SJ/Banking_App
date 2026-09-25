# Loan information

All loan routes require a bearer JWT and scope results through the authenticated user's customer record.

| Method and path | Result |
| --- | --- |
| `GET /api/v1/loans` | Lists the customer's loans. |
| `GET /api/v1/loans/{loanId}` | Returns one owned loan. |
| `POST /api/v1/loans/{loanId}/payments` | Pays an active loan from an owned active account. |
| `GET /api/v1/loans/{loanId}/payments?page=0&size=20` | Returns paged repayment history. |

Responses include the original principal, current outstanding principal, rate, term, EMI, next due date,
maturity date, currency, and status. A loan owned by another customer is returned as not found.

The products service owns the loan schema and applies its Flyway migrations automatically.

## Payments

Payment requests include `sourceAccountId`, a positive `amount` with at most four fractional digits, and an
8–64 character `idempotencyKey`. Repeating a completed request with the same loan and key returns the
original payment without debiting the account again.

Products reserves principal under a loan row lock before requesting a debit from accounts-ledger.
Pending reservations count against the outstanding principal. Accounts-ledger validates source ownership,
status, currency and available funds, then commits the debit and a deduplicated receipt locally. Products
applies the repayment and queues audit/notification events after receiving that receipt.

A timeout may mean the debit completed. Keep the same idempotency key when retrying. The recovery worker
resumes the durable reservation and retrieves the same ledger receipt, then completes repayment history
without another debit. Only definite ledger rejection releases the reservation. These are separate local
transactions with forward recovery; there is no transaction spanning both schemas.

A full payoff changes the loan to `PAID_OFF` and clears its next due date. See the
[consistency guide](microservices-architecture.md) for recovery and operational limitations.
