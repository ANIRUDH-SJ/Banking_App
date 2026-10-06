# Member 1 API contract

Customer access screens call the gateway. The server response is the authority for registration, sign-in, profile, and notices. The browser does not decide a balance, a role, or whether an account exists during password recovery.

## Registration

`POST /api/v1/auth/register`

```json
{
  "username": "asha",
  "email": "asha@example.com",
  "password": "a-sufficiently-long-password",
  "firstName": "Asha",
  "lastName": "Menon",
  "dateOfBirth": "1990-04-02",
  "mobileNumber": "+919876543210"
}
```

`201 Created`

```json
{
  "userId": 7,
  "customerId": 7,
  "customerNumber": "CUST0000000000000007",
  "status": "ACTIVE"
}
```

`400` uses `VALIDATION_FAILED` and `fieldErrors`. `409` uses `CONFLICT` when the details are already in use. No access token is issued.

## Sign in

`POST /api/v1/auth/login`

```json
{ "usernameOrEmail": "asha", "password": "a-sufficiently-long-password" }
```

```json
{ "challengeId": null, "status": "TOTP_SETUP_REQUIRED" }
```

or

```json
{ "challengeId": "short-lived-signed-challenge", "status": "TOTP_REQUIRED" }
```

Invalid credentials return `401` with `UNAUTHORIZED`. Repeated authentication posts can return `429` with `RATE_LIMITED` and `Retry-After`.

## Microsoft Authenticator setup

`POST /api/v1/auth/totp/setup` with the same login body.

```json
{
  "provisioningUri": "otpauth://totp/...",
  "qrCodeDataUri": "data:image/png;base64,...",
  "manualEntryKey": "BASE32_SECRET",
  "issuer": "Internet Banking",
  "accountName": "asha"
}
```

The screen shows `qrCodeDataUri` and offers `manualEntryKey` only as a fallback. It does not keep the provisioning URI.

`POST /api/v1/auth/totp/confirm`

```json
{
  "credentials": { "usernameOrEmail": "asha", "password": "a-sufficiently-long-password" },
  "code": "123456"
}
```

`204 No Content` means the authenticator is enabled. `401` means the code was rejected.

## Authenticator verification

`POST /api/v1/auth/login/verify-totp`

```json
{ "challengeId": "short-lived-signed-challenge", "code": "654321" }
```

```json
{
  "accessToken": "jwt",
  "tokenType": "Bearer",
  "userId": 7,
  "username": "asha",
  "roles": ["CUSTOMER"]
}
```

The access token expires 15 minutes after it is issued. A bad or expired challenge returns `401`.

## Password recovery

`POST /api/v1/auth/password-reset/challenges`

```json
{ "usernameOrEmail": "asha@example.com" }
```

```json
{ "challengeId": "2f0bff82-7aca-43f9-b2b4-40ae50645558", "status": "OTP_SENT_IF_ACCOUNT_EXISTS" }
```

The same status is returned when an account does not exist. The screen must not treat it as proof of an account.

`POST /api/v1/auth/password-reset/confirm`

```json
{
  "challengeId": "2f0bff82-7aca-43f9-b2b4-40ae50645558",
  "code": "123456",
  "newPassword": "a-new-strong-password"
}
```

`204 No Content` on success. `401` when the code or challenge cannot be used. The new password must be 12 to 128 characters and different from the current password.

## Profile

`GET /api/v1/profile` and `PUT /api/v1/profile` require `Authorization: Bearer <accessToken>` and the `CUSTOMER` role.

`PUT` body:

```json
{ "firstName": "Asha", "lastName": "Menon", "mobileNumber": "+919876543210" }
```

Response:

```json
{
  "customerId": 7,
  "customerNumber": "CUST0000000000000007",
  "firstName": "Asha",
  "lastName": "Menon",
  "dateOfBirth": "1990-04-02",
  "mobileNumber": "+919876543210",
  "kycStatus": "VERIFIED",
  "active": true
}
```

Customer number, date of birth, KYC status, and active state are read-only in the screen.

## Notices

`GET /api/v1/notifications?page=0&size=20`

```json
{
  "content": [
    {
      "notificationId": 4,
      "userId": 7,
      "title": "Transfer received",
      "message": "A transfer was credited to your account.",
      "read": false,
      "createdAt": "2026-10-05T09:30:00"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

`PATCH /api/v1/notifications/{notificationId}/read` returns `204 No Content`.

## Home summaries

The home screen reads these endpoints and displays the returned values. It does not recalculate balances or decide whether a card or loan action is allowed.

| Read | Service module |
| --- | --- |
| `GET /api/v1/accounts` | `services/account-service` `getAccounts()` |
| `GET /api/v1/accounts/{accountId}/transactions?page=0&size=5` | `services/transaction-service` `list(accountId, query)` |
| `GET /api/v1/cards` | `services/card-service` `list()` |
| `GET /api/v1/loans` | `services/loan-service` `list()` |

Account fields used on Home: `accountId`, `accountNumber`, `accountType`, `currencyCode`, `accountStatus`, `currentBalance`, `availableBalance`.

Transaction fields used on Home: `narration`, `reference`, `entryType`, `status`, `amount`, `currencyCode`, `postedAt`.

Card fields used on Home: `maskedCardNumber`, `cardType`, `status`.

Loan fields used on Home: `loanAccountNumber`, `loanType`, `outstandingPrincipal`, `currencyCode`, `nextDueDate`, `status`.

## Sign out

Sign out clears the browser session. The identity service does not expose a logout endpoint. The next protected request without a token returns `401`.
