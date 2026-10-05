# Fixed and recurring deposits

The products service owns FD/RD quotes, contracts and installment schedules. The
accounts-ledger service owns INR balance movements and statement entries. Both
services must be running; products service cannot post a balance directly.

## Customer flow

1. `POST /api/v1/deposits/quotes` with `sourceAccountId`, `kind` (`FD` or `RD`),
   `amount` and `termMonths` (6, 12, 24 or 36). Amount is the full FD principal
   or each RD installment. An active, customer-owned INR account is required.
2. Within 10 minutes, `POST /api/v1/deposits` with the returned `quoteId` and a
   client-generated `idempotencyKey`. Reuse the same key when retrying an
   uncertain response. The first funding debit is recorded as a bank statement
   transaction. A pending opening is recoverable after a service interruption.
3. `GET /api/v1/deposits` and `GET /api/v1/deposits/{depositId}` show the
   customer's contracts, contribution total, schedule and payout status.
4. RD installments are automatically debited when due, or may be attempted with
   `POST /api/v1/deposits/{depositId}/installments` once due. Insufficient funds
   leave the installment outstanding for retry. A matured RD is not paid out
   until all installments have been collected.
5. At maturity, the scheduler credits the source account. The payout and each
   installment have stable ledger operation IDs, so a retry cannot move money
   twice. `payoutReference` is the bank statement transaction reference.

FD minimum: INR 1,000; RD installment minimum: INR 100; maximum per quote:
INR 10,000,000. Amounts have at most two decimal places.

## Pricing and operational limits

`app.deposits.fd-rate-percent` defaults to `6.50` and
`app.deposits.rd-rate-percent` defaults to `6.00`. These are **configurable demo
rates**, not a live bank rate sheet. The quote snapshots the annual rate.
Interest is calculated as simple daily interest (actual days / 365) on each
contribution through maturity; delayed RD installments earn less than the
on-time estimate. The estimate is not a guarantee of payout.

This implementation has no premature closure, tax withholding, nominee,
regulatory disclosures, deposit insurance representation, or production
reconciliation. Those require bank policy and legal review before real-money
use. Run the Oracle migration and full service integration checks in the
deployment environment before enabling this product.
