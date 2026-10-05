# Member 1 frontend contract

Member 1 owns the Oracle JET shell and customer access: registration, sign-in, Microsoft Authenticator setup and verification, password reset, session expiry, profile, dashboard assembly, and the notification center. Account, payment, card, loan, and administration screens stay with their owners and call the shared services below.

The browser calls the API gateway at `http://localhost:8080` unless `window.NET_BANKING_API_BASE` is set. Requests use `credentials: 'omit'`. Authenticated calls send `Authorization: Bearer <access token>`. Paths under `/api/v1/auth/` do not send a bearer token.

## Shared services

| Module | Responsibility |
|---|---|
| `services/api-client-service` | `get`, `post`, `put`, `patch`, `delete`. Parses `ApiErrorResponse` into `{ status, code, message, fieldErrors, correlationId }`. A `401` on a bearer request clears the session. |
| `services/session-service` | Stores the access session in `sessionStorage` under `nb.session.v1`. Reads `exp` from the access token. Warns during the final minute and clears the session at expiry. |
| `services/auth-service` | Registration, login, authenticator setup and confirmation, login verification, and password reset. |
| `services/auth-flow-state` | In-memory sign-in state only. Setup keeps the password until confirmation or the screen is left. Verification keeps the challenge id, not the password. |
| `services/otp-service` | Six-digit code check and the existing payment OTP challenge requests. |
| `services/route-guard-service` | Anonymous, pending-authenticator, signed-in, and `ADMIN` route decisions. Unknown routes require a session. |
| `services/validation-service` | Client checks aligned with the identity request constraints. The server remains the authority. |
| `services/profile-service` | `GET` and `PUT /api/v1/profile`. |
| `services/notification-service` | Paged notifications and mark-read. |
| `services/dashboard-slots` | Lets another module register a summary without this shell calling account, card, or loan APIs. |

Register an additional route with `routeGuard.registerRoute(path, rule)` before navigation. A rule is `{ access: 'authenticated' }`, `{ access: 'anonymous' }`, or `{ access: 'role', roles: ['ADMIN'] }`.

Register a dashboard summary with `dashboardSlots.registerSummary({ id, title, load })`. `load` returns a short string. A rejected `load` is shown as a failed summary and does not hide the rest of the dashboard.

## Routes

| Path | Access |
|---|---|
| `login`, `register`, `password-reset` | Signed-out customers. A signed-in customer is sent to `dashboard`. |
| `totp-setup` | Only while setup credentials are held in memory. |
| `totp-verify` | Only while a login challenge id is held in memory. |
| `dashboard`, `profile`, `notifications` | Any signed-in customer. |
| `admin` | Signed-in user whose roles include `ADMIN`. Other customers return to `dashboard`. |

Planned routes for the other owners are not registered yet: `accounts`, `transactions`, `beneficiaries`, `transfer`, `billers`, `bill-payments`, `cards`, and `loans`. Until those views exist, the guard treats an unknown path as signed-in only.

## Auth and profile

`POST /api/v1/auth/register` returns `201`:

```json
{ "userId": 1, "customerId": 1, "customerNumber": "CUST0000000000000001", "status": "ACTIVE" }
```

`POST /api/v1/auth/login` returns `TOTP_SETUP_REQUIRED` with a null `challengeId`, or `TOTP_REQUIRED` with a short-lived `challengeId`. The password and challenge are not written to storage.

`POST /api/v1/auth/totp/setup` returns `provisioningUri`, `qrCodeDataUri`, `manualEntryKey`, `issuer`, and `accountName`. The screen shows the image and the manual key. It does not render or store the provisioning URI.

`POST /api/v1/auth/totp/confirm` and `POST /api/v1/auth/password-reset/confirm` return `204`. Login verification returns:

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "userId": 1,
  "username": "asha",
  "roles": ["CUSTOMER"]
}
```

The stored session keeps those fields plus `expiresAt` from the token `exp` claim. Access tokens last 15 minutes. There is no refresh call, so sign-out and expiry both discard the session. Logout is local because the API is stateless.

`POST /api/v1/auth/password-reset/challenges` always returns `OTP_SENT_IF_ACCOUNT_EXISTS` and a challenge id. The screen uses the same explanation whether or not an account exists.

`GET /api/v1/profile` and `PUT /api/v1/profile`:

```json
{
  "customerId": 1,
  "customerNumber": "CUST0000000000000001",
  "firstName": "Asha",
  "lastName": "Sharma",
  "dateOfBirth": "1990-04-12",
  "mobileNumber": "+919876543210",
  "kycStatus": "PENDING",
  "active": true
}
```

The update body is `firstName`, `lastName`, and `mobileNumber`. Customer number, date of birth, and KYC status stay read-only.

## Notifications and OTP challenges

`GET /api/v1/notifications?page=0&size=20` returns `PagedResponse`: `content`, `page`, `size`, `totalElements`, `totalPages`, `first`, and `last`. Each item has `notificationId`, `userId`, `title`, `message`, `read`, and `createdAt`. `PATCH /api/v1/notifications/{notificationId}/read` returns `204`.

`OtpService` issues challenges that already exist on the gateway:

- `POST /api/v1/transfers/otp-challenges`
- `POST /api/v1/bill-payments/otp-challenges`
- `POST /api/v1/beneficiaries/{beneficiaryId}/activation-challenges`
- `POST /api/v1/forex/quotes/{quoteId}/otp-challenges`

Each returns `{ "challengeId": "...", "status": "..." }`. Loan repayment does not have a separate OTP challenge; it uses an idempotency key on the loan payment request.

## Errors

Failed responses use `ApiErrorResponse`: `timestamp`, `status`, `code`, `message`, `path`, `correlationId`, and `fieldErrors`. Validation failures use `VALIDATION_FAILED` and field messages. The screens show `message` and matching `fieldErrors`. Passwords, authenticator secrets, and access tokens are not written to the page log.
