# Member 2: Accounts and Transfers

## Draft scope

This workstream owns the customer-facing accounts and transfer experience:

- account list, account details, balances, and account status;
- transaction history, filters, pagination, and statement CSV export;
- beneficiary list, creation, activation, and removal;
- transfer form, review, OTP confirmation, receipt, and transfer status.

## Dependencies owned by Member 1

This work will use the shared application shell, navigation, route guards, API client, session handling, authentication state, validation messages, and OTP helper once they are available. It will not implement duplicate versions of those shared features.

## Planned frontend services

- `AccountService`
- `TransactionService`
- `BeneficiaryService`
- `FundTransferService`

## Planned delivery order

1. Account list and account-detail screens.
2. Transaction history and statement export.
3. Beneficiary management.
4. Transfer form and review.
5. OTP confirmation, receipt, error, and status states.
6. Responsive, accessibility, and API-error tests.

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

Route names and shared-service import paths will be finalised with Member 1 before implementation begins.
