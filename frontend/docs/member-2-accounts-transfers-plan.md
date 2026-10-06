# Member 2: Accounts and Transfers

## Scope and current state

This workstream owns the customer-facing accounts and transfer experience:

- account list, balances, and account status;
- transaction history, date/search filters, and statement CSV export;
- beneficiary list, creation, activation, and removal;
- transfer form, review, OTP confirmation, receipt, and transfer status.

## Dependencies owned by Member 1

These screens use PR #60's shared shell, navigation, route guards, API client, session, formatting, and OTP helper. The branch deliberately does not change `appController.js` or `app.css`; its `member2.css` inherits the shared bank tokens. Merge PR #60 first.

## Frontend services

- `registry.accounts` and `registry.transactions` from PR #60
- `BeneficiaryService`
- `FundTransferService`

## Implemented here

1. Live account balances and statuses; no sample account data.
2. The first 50 statement entries with date/search filters and full CSV export.
3. Beneficiary creation, OTP activation, and disablement through the backend.
4. Transfer review, backend OTP challenge, idempotent submission, and server receipt.
5. Shared theme, loading/empty/error states, and focused flow tests.

This remains a draft. Account detail, statement pagination, transfer history, browser-level responsive/accessibility checks, and live-backend end-to-end testing remain for the workspace owner.

## Backend endpoints to integrate

- `GET /api/v1/accounts`
- `GET /api/v1/accounts/{accountId}`
- `GET /api/v1/accounts/{accountId}/transactions`
- `GET /api/v1/accounts/{accountId}/statement.csv`
- `GET, POST /api/v1/beneficiaries`
- `POST /api/v1/beneficiaries/{beneficiaryId}/activation-challenges`
- `POST /api/v1/beneficiaries/{beneficiaryId}/activate`
- `DELETE /api/v1/beneficiaries/{beneficiaryId}`
- `POST /api/v1/transfers/otp-challenges`
- `POST /api/v1/transfers`
- `GET /api/v1/transfers`

Routes and imports follow PR #60's published foundation contract.
