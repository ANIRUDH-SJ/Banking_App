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

Apply `database/12_loan_tables.sql` after the customer schema.

## Payments

Payment requests include `sourceAccountId`, a positive `amount` with at most four fractional digits, and an
8–64 character `idempotencyKey`. Repeating a completed request with the same loan and key returns the
original payment without debiting the account again.

The payment transaction locks both the owned loan and source account. It rejects inactive loans, inactive
accounts, currency mismatches, insufficient funds, and amounts above the outstanding principal. A successful
payment commits the account debit, loan balance, payment history, banking transaction, ledger entry, status
history, and audit event together. A full payoff changes the loan to `PAID_OFF` and clears its next due date.

Apply `database/13_loan_payment_tables.sql` after the transaction status history and loan schemas.
