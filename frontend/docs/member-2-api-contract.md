# Member 2 API contract

Member 2 screens use Member 1's authenticated `ApiClientService`. The services in
`src/js/services/` do not own tokens, session storage, login, or common error handling.

## Accounts and statements

| Screen action | Method and endpoint | Important response fields |
| --- | --- | --- |
| List accounts | `GET /api/v1/accounts` | `accountId`, `accountNumber`, `accountType`, `currencyCode`, `accountStatus`, `currentBalance`, `availableBalance` |
| View account | `GET /api/v1/accounts/{accountId}` | Same account summary fields |
| List transactions | `GET /api/v1/accounts/{accountId}/transactions?page=0&size=20` | Paged `content`; transaction `reference`, `type`, `status`, `amount`, `currencyCode`, `narration`, `postedAt` |
| Filter statement | `GET /api/v1/accounts/{accountId}/statement?from=YYYY-MM-DD&to=YYYY-MM-DD&type=TRANSFER&status=COMPLETED` | Paged transaction response |
| Export CSV | `GET /api/v1/accounts/{accountId}/statement.csv` | CSV attachment |

The server is the authority for account ownership, balance, statement data, and status.

## Beneficiaries

| Screen action | Method and endpoint | Request body |
| --- | --- | --- |
| List | `GET /api/v1/beneficiaries` | — |
| Create | `POST /api/v1/beneficiaries` | `nickname`, `beneficiaryName`, `accountNumber`, `ifscCode`, `bankName` |
| Request activation OTP | `POST /api/v1/beneficiaries/{beneficiaryId}/activation-challenges` | — |
| Activate | `POST /api/v1/beneficiaries/{beneficiaryId}/activate` | `otpChallengeId`, `otpCode` |
| Remove | `DELETE /api/v1/beneficiaries/{beneficiaryId}` | — |

The creation form applies the same client-side format rules as the server: account number
must have 10–20 digits and IFSC must match `AAAA0AAAAAA`.

## Fund transfers

| Screen action | Method and endpoint | Request body |
| --- | --- | --- |
| Request OTP | `POST /api/v1/transfers/otp-challenges` | `sourceAccountId`, `beneficiaryId`, `amount` |
| Confirm transfer | `POST /api/v1/transfers` | `sourceAccountId`, `beneficiaryId`, `amount`, `narration`, `idempotencyKey`, `otpChallengeId`, `otpCode` |
| History | `GET /api/v1/transfers` | — |

The transfer flow must use Member 1's shared OTP utility. The server response decides the
final receipt status and transaction reference; the UI must not treat a local preview as a
completed transfer.
