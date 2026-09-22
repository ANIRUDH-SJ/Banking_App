# Transaction history and statements

This is the first read-only slice of backend services 10 (`TransactionService`) and 11 (`StatementService`).
It reads the existing `bank_transaction` and `account_transaction_entry` tables from
`database/06_transaction_tables.sql`. Ledger entries identify the account-specific debit or credit and
its balance after posting. All routes require a bearer JWT, and the service checks active account ownership
before querying any entries.

## Routes

| Method and path | Result |
| --- | --- |
| `GET /api/v1/accounts/{accountId}/transactions?page=0&size=20` | Newest ledger entries for the account, paged; size 1–100. |
| `GET /api/v1/accounts/{accountId}/transactions/{entryId}` | One entry, scoped to the account. |
| `GET /api/v1/accounts/{accountId}/statement?from=2026-08-01&to=2026-08-31&type=TRANSFER&status=COMPLETED&page=0&size=20` | Filtered, paged ledger entries. All filters are optional. Dates include the whole `to` day. |
| `GET /api/v1/accounts/{accountId}/statement.csv?from=2026-08-01&to=2026-08-31` | CSV attachment with the same optional filters. Maximum 10,000 rows; narrow the filters if exceeded. |

`type` accepts `TRANSFER`, `DEPOSIT`, `WITHDRAWAL`, or `REVERSAL`. `status` accepts `PENDING`,
`PROCESSING`, `COMPLETED`, `FAILED`, or `REVERSED`. Results are ordered by `postedAt` and then `entryId`,
newest first. These routes report posted ledger entries only; a pending transaction without an entry
does not appear in a statement.

Example response item:

```json
{
  "entryId": 99,
  "transactionId": 1,
  "reference": "TXN-123",
  "type": "TRANSFER",
  "status": "COMPLETED",
  "entryType": "DEBIT",
  "amount": 12.50,
  "currencyCode": "INR",
  "balanceAfter": 87.50,
  "narration": "Transfer",
  "postedAt": "2026-08-01T12:30:00"
}
```

Invalid page sizes and inverted date ranges return `400`; an entry absent from the specified account
returns `404`; an account the user does not hold returns `403`. Oversized CSV exports return `413`.

## Next integration step

Posting financial transactions, assigning references, and recording lifecycle changes must be integrated
with transfer and bill-payment services so balance updates, transaction records, ledger entries, idempotency,
and audit events share one database transaction. No public write endpoint is exposed by this slice.
