# Backend testing guide

This guide verifies the backend from a clean local checkout through the public API gateway. It covers the automated test suite, Oracle schemas, Eureka registration, JWT authentication, Microsoft Authenticator, Postman, customer APIs, payments, products, notifications, admin authorization, idempotency, and service discovery failures.

Use only development credentials and data. Keep `.local/`, passwords, JWTs, OTPs, QR images, and Authenticator secrets out of Git, screenshots, shared Postman exports, and logs.

## 1. What is being tested

The local system contains:

| Application | Port | Database schema |
|---|---:|---|
| Eureka service registry | 8761 | None |
| API gateway | 8080 | None |
| Identity service | 8081 | `NB_IDENTITY` |
| Accounts and ledger service | 8082 | `NB_ACCOUNTS` |
| Payments service | 8083 | `NB_PAYMENTS` |
| Products service | 8084 | `NB_PRODUCTS` |
| Notification service | 8085 | `NB_NOTIFICATIONS` |
| Audit and reporting service | 8086 | `NB_AUDIT` |

All client and Postman requests should use `http://localhost:8080/api/v1`. Direct service ports are useful for health checks only. They are not the browser API.

## 2. Prerequisites

Install:

- Git;
- Java 17 with `JAVA_HOME` set;
- Node.js 22 LTS or another supported Node.js version;
- Oracle Database 26ai with `FREEPDB1` on `127.0.0.1:1521`;
- SQLcl, SQL*Plus, or SQL Developer;
- Postman;
- Microsoft Authenticator on a phone whose clock is set automatically.

Confirm the command line tools:

```sh
java -version
node --version
git --version
```

For a fresh checkout:

```sh
git clone https://github.com/ANIRUDH-SJ/Banking_App.git
cd Banking_App
```

Run every repository command below from the repository root unless the guide says otherwise.

## 3. Build and automated checks

On Linux or macOS:

```sh
./mvnw verify
node scripts/verify-architecture.mjs
```

On Windows PowerShell:

```powershell
./mvnw.cmd verify
node scripts/verify-architecture.mjs
```

The Maven suite uses H2 for normal tests. It checks application behavior, but it does not prove that the Oracle mappings are valid. The architecture verifier checks service boundaries and prohibited dependencies.

To build one application with its required local modules:

```sh
./mvnw -pl :payments-service -am verify
```

Replace `payments-service` with the required Maven artifact name.

## 4. Create the six Oracle schemas

Connect to `FREEPDB1` as a PDB administrator. Do not run the scripts in `CDB$ROOT`.

With SQLcl, start a session and run:

```text
sql system@//127.0.0.1:1521/FREEPDB1
@database/bootstrap/nb_identity.sql
@database/bootstrap/nb_accounts.sql
@database/bootstrap/nb_payments.sql
@database/bootstrap/nb_products.sql
@database/bootstrap/nb_notifications.sql
@database/bootstrap/nb_audit.sql
```

Each script asks for a private schema password. Store the six passwords in a password manager. The bootstrap scripts are intentionally one-time operations and fail when a schema already exists.

## 5. Generate private local configuration

Generate configuration once:

```sh
node scripts/init-local.mjs
```

This creates `.local/*.json` with Eureka credentials, service credentials, a 3072-bit RSA JWT key pair, and the AES key used to encrypt TOTP secrets. The command refuses to replace an existing `.local` directory because doing so would invalidate enrolled authenticators and existing JWT trust.

Edit the following six files and replace `REPLACE_WITH_LOCAL_SCHEMA_PASSWORD` with the password for that file's `DB_USERNAME`:

```text
.local/identity-service.json
.local/accounts-ledger-service.json
.local/payments-service.json
.local/products-service.json
.local/notification-service.json
.local/audit-reporting-service.json
```

If Oracle is elsewhere, update `DB_URL` in those files. Do not commit `.local`.

For faster beneficiary tests, add this property to `.local/payments-service.json` before starting the applications:

```json
"APP_BENEFICIARY_ACTIVATION_COOLDOWN_MINUTES": "0"
```

Keep the preceding JSON property comma valid. Without this local override, wait the default 30 minutes before requesting beneficiary activation.

Validate all Oracle migrations and Hibernate mappings:

```sh
node scripts/verify-oracle.mjs
```

This command applies Flyway migrations in all six schemas. A passing Maven build alone is not a substitute for this check.

## 6. Start all applications

Open eight terminals at the repository root and run the commands in this order:

```sh
node scripts/run-service.mjs service-registry
```

```sh
node scripts/run-service.mjs identity-service
```

```sh
node scripts/run-service.mjs accounts-ledger-service
```

```sh
node scripts/run-service.mjs audit-reporting-service
```

```sh
node scripts/run-service.mjs notification-service
```

```sh
node scripts/run-service.mjs payments-service
```

```sh
node scripts/run-service.mjs products-service
```

```sh
node scripts/run-service.mjs api-gateway
```

Open `http://localhost:8761` and sign in with `EUREKA_USERNAME` and `EUREKA_PASSWORD` from `.local/service-registry.json`. Allow time for registration. Expect one instance of the gateway and each of the six business services.

Basic health checks:

```sh
curl http://localhost:8761/actuator/health
curl http://localhost:8080/api/v1/health
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
curl http://localhost:8083/actuator/health
curl http://localhost:8084/actuator/health
curl http://localhost:8085/actuator/health
curl http://localhost:8086/actuator/health
```

Each response should report `UP`. If the gateway starts before its peers have registered, retry after several seconds.

## 7. Create a Postman environment

Create an environment named `Banking Local` with these variables:

| Variable | Initial value | Purpose |
|---|---|---|
| `baseUrl` | `http://localhost:8080/api/v1` | Public gateway API |
| `username` | `microtest` | Test login |
| `password` | Leave empty | Enter locally as a secret value |
| `loginChallengeId` | Leave empty | Short-lived login challenge |
| `accessToken` | Leave empty | JWT from successful TOTP login |
| `sourceAccountId` | `930001` | Funded account fixture |
| `destinationAccountId` | `930002` | Destination account fixture |
| `beneficiaryId` | Leave empty | Created beneficiary |
| `beneficiaryOtpChallengeId` | Leave empty | Beneficiary activation challenge |
| `transferOtpChallengeId` | Leave empty | Transfer challenge |
| `billerId` | Leave empty | Biller returned by the API |
| `billOtpChallengeId` | Leave empty | Bill payment challenge |
| `cardId` | `940001` | Card fixture |
| `loanId` | `950001` | Loan fixture |
| `notificationId` | Leave empty | Notification returned by the API |

Use a disposable password that satisfies the 12-character minimum. Mark password and token variables as sensitive. Never export or commit their current values.

For protected requests, set the collection authorization type to **Bearer Token** with:

```text
{{accessToken}}
```

Set the authorization type to **No Auth** on registration, login, TOTP setup, and TOTP confirmation requests so they do not inherit the collection's bearer token. All later customer and admin requests should inherit the collection authorization.

## 8. Register and enroll Microsoft Authenticator

### 8.1 Register the user

Create a `POST {{baseUrl}}/auth/register` request with `Content-Type: application/json`:

```json
{
  "username": "{{username}}",
  "email": "microtest@example.test",
  "password": "{{password}}"
}
```

Expect `201 Created` with no response body. Reusing the username or email should fail.

### 8.2 Confirm setup is required

Send `POST {{baseUrl}}/auth/login`:

```json
{
  "usernameOrEmail": "{{username}}",
  "password": "{{password}}"
}
```

Expect a JSON response whose `status` is `TOTP_SETUP_REQUIRED`.

### 8.3 Generate and display the QR code in Postman

Send `POST {{baseUrl}}/auth/totp/setup` with the same login body. The backend generates the TOTP secret, `otpauth://` URI, QR PNG data URI, issuer, and account name. The frontend's eventual responsibility is only to display the returned image and submit the user's code.

Add this Postman **Tests** script to render the response in Postman's Visualize tab without storing the secret in an environment variable:

```javascript
const setup = pm.response.json();

pm.visualizer.set(`
  <main style="font-family: sans-serif; text-align: center">
    <h2>{{issuer}}</h2>
    <p>{{accountName}}</p>
    <img src="{{qrCodeDataUri}}" alt="Authenticator QR code">
  </main>
`, setup);
```

Open **Visualize**, then in Microsoft Authenticator choose **Add account** > **Other account** and scan the QR code. The account should show issuer `Internet Banking`, the username, a six-digit code, and a 30-second countdown. Use `manualEntryKey` only when scanning is unavailable, and do not save it outside this one setup session.

### 8.4 Confirm enrollment

Send `POST {{baseUrl}}/auth/totp/confirm` with the current six-digit Microsoft Authenticator code:

```json
{
  "credentials": {
    "usernameOrEmail": "{{username}}",
    "password": "{{password}}"
  },
  "code": "123456"
}
```

Replace `123456` immediately before sending. Expect `204 No Content`.

If this returns `401 Unauthorized`, wait for a new code and verify that automatic date and time are enabled on both the phone and the computer.

### 8.5 Obtain a JWT

Send `POST {{baseUrl}}/auth/login` again. Expect `status` equal to `TOTP_REQUIRED` and a non-empty `challengeId`. Add this Tests script:

```javascript
const response = pm.response.json();
pm.environment.set('loginChallengeId', response.challengeId);
```

Send `POST {{baseUrl}}/auth/login/verify-totp` with a current, unused Authenticator code:

```json
{
  "challengeId": "{{loginChallengeId}}",
  "code": "654321"
}
```

Add this Tests script:

```javascript
pm.test('JWT was issued', function () {
  pm.response.to.have.status(200);
  const response = pm.response.json();
  pm.expect(response.tokenType).to.eql('Bearer');
  pm.expect(response.accessToken).to.be.a('string').and.not.empty;
  pm.environment.set('accessToken', response.accessToken);
});
```

Microsoft Authenticator codes authenticate the login. The returned JWT authorizes later APIs. A login challenge is not a bearer token. The same six-digit code cannot be reused for another successful login.

Verify the JWT by sending `GET {{baseUrl}}/profile` with collection authorization. Before the customer fixture is added, a not-found profile response is expected; `401` would mean the JWT was rejected.

## 9. Add repeatable local customer fixtures

Registration creates the security user only. The following development-only rows link that user to customer, account, card, and loan records. Use the exact numeric IDs because separate service schemas refer to the same logical identifiers without cross-schema foreign keys.

Connect as `NB_IDENTITY` and run:

```sql
INSERT INTO customer (
    customer_id, user_id, customer_number, first_name, last_name,
    date_of_birth, mobile_number, kyc_status, kyc_verified_at, is_active
)
SELECT
    900001, user_id, 'CUST900001', 'Micro', 'Tester',
    DATE '1995-01-01', '9000000001', 'VERIFIED', SYSTIMESTAMP, 'Y'
FROM app_user
WHERE username = 'microtest';

COMMIT;
```

Confirm that one row was inserted. If no row was inserted, the registered username does not match the SQL.

Connect as `NB_ACCOUNTS` and run:

```sql
INSERT INTO bank (bank_id, bank_code, legal_name, display_name, is_active)
VALUES (910001, 'DEMO', 'Demo Bank Limited', 'Demo Bank', 'Y');

INSERT INTO branch (
    branch_id, bank_id, branch_code, branch_name, ifsc_code,
    address_line_1, city, state, postal_code, is_active
)
VALUES (
    920001, 910001, 'MAIN', 'Demo Main Branch', 'DEMO0000001',
    '1 Test Street', 'Bengaluru', 'Karnataka', '560001', 'Y'
);

INSERT INTO bank_account (
    account_id, branch_id, account_number, account_type, currency_code,
    account_status, current_balance, available_balance
)
VALUES (930001, 920001, '1000000001', 'SAVINGS', 'INR', 'ACTIVE', 100000, 100000);

INSERT INTO bank_account (
    account_id, branch_id, account_number, account_type, currency_code,
    account_status, current_balance, available_balance
)
VALUES (930002, 920001, '1000000002', 'SAVINGS', 'INR', 'ACTIVE', 25000, 25000);

INSERT INTO account_holder (account_id, customer_id, holder_type, is_active)
VALUES (930001, 900001, 'PRIMARY', 'Y');

INSERT INTO account_holder (account_id, customer_id, holder_type, is_active)
VALUES (930002, 900001, 'PRIMARY', 'Y');

COMMIT;
```

Connect as `NB_PRODUCTS` and run:

```sql
INSERT INTO bank_card (
    card_id, customer_id, account_id, card_token, last_four,
    card_type, card_network, expiry_month, expiry_year, card_status
)
VALUES (
    940001, 900001, 930001, 'local-test-token-940001', '4242',
    'DEBIT', 'VISA', 12, 2030, 'INACTIVE'
);

INSERT INTO loan_account (
    loan_id, customer_id, loan_account_number, loan_type,
    principal_amount, outstanding_principal, interest_rate,
    term_months, emi_amount, currency_code, disbursed_on,
    next_due_date, maturity_date, loan_status
)
VALUES (
    950001, 900001, 'LOAN950001', 'PERSONAL',
    50000, 40000, 10.5000,
    24, 2500, 'INR', DATE '2026-01-01',
    DATE '2026-10-01', DATE '2027-12-31', 'ACTIVE'
);

COMMIT;
```

These are synthetic values. No PAN or CVV is inserted; only a token and the last four digits are stored.

Now `GET {{baseUrl}}/profile` should return customer `900001`, and `GET {{baseUrl}}/accounts` should return accounts `930001` and `930002`.

## 10. Test accounts, transactions, and statements

Send these bearer-authenticated requests:

```text
GET {{baseUrl}}/accounts
GET {{baseUrl}}/accounts/{{sourceAccountId}}
GET {{baseUrl}}/accounts/{{sourceAccountId}}/transactions?page=0&size=20
GET {{baseUrl}}/accounts/{{sourceAccountId}}/statement?page=0&size=20
GET {{baseUrl}}/accounts/{{sourceAccountId}}/statement.csv
```

The transaction list is initially empty. For the CSV request, use **Send and Download** in Postman and verify the header row.

Ownership check:

```text
GET {{baseUrl}}/accounts/999999
```

Expect a rejection rather than another customer's data. Also remove collection authorization temporarily and call `GET {{baseUrl}}/accounts`; expect `401 Unauthorized`.

## 11. Test beneficiaries and transfers

Microsoft Authenticator is used for login. Beneficiary, transfer, and bill payment approval uses a separate six-digit transaction OTP. In the local profile, the identity service prints that OTP in its terminal as `LOCAL DEVELOPMENT OTP ...`. This development delivery adapter must be replaced before any non-local environment.

### 11.1 Create and activate a beneficiary

Send `POST {{baseUrl}}/beneficiaries`:

```json
{
  "nickname": "Second account",
  "beneficiaryName": "Micro Tester",
  "accountNumber": "1000000002",
  "ifscCode": "DEMO0000001",
  "bankName": "Demo Bank"
}
```

Add this Tests script:

```javascript
const response = pm.response.json();
pm.environment.set('beneficiaryId', response.beneficiaryId);
```

Expect `201 Created` and `PENDING` status. Send:

```text
POST {{baseUrl}}/beneficiaries/{{beneficiaryId}}/activation-challenges
```

No body is required. Save the returned challenge with:

```javascript
const response = pm.response.json();
pm.environment.set('beneficiaryOtpChallengeId', response.challengeId);
```

Copy the `BENEFICIARY_ACTIVATION` OTP from the identity-service terminal. Send `POST {{baseUrl}}/beneficiaries/{{beneficiaryId}}/activate`:

```json
{
  "otpChallengeId": "{{beneficiaryOtpChallengeId}}",
  "otpCode": "123456"
}
```

Replace the code before sending. Expect `ACTIVE` status. Confirm it with `GET {{baseUrl}}/beneficiaries`.

### 11.2 Transfer funds

Send `POST {{baseUrl}}/transfers/otp-challenges`:

```json
{
  "sourceAccountId": {{sourceAccountId}},
  "beneficiaryId": {{beneficiaryId}},
  "amount": 100.00
}
```

Save `challengeId` to `transferOtpChallengeId`. Copy the `FUND_TRANSFER` OTP from the identity-service terminal.

Set `transferKey` once in the Postman environment to a unique value, for example `transfer-local-0001`. Send `POST {{baseUrl}}/transfers`:

```json
{
  "sourceAccountId": {{sourceAccountId}},
  "beneficiaryId": {{beneficiaryId}},
  "amount": 100.00,
  "narration": "Local transfer test",
  "idempotencyKey": "{{transferKey}}",
  "otpChallengeId": "{{transferOtpChallengeId}}",
  "otpCode": "123456"
}
```

Expect `201 Created`, `COMPLETED`, a transaction ID, and a stable transaction reference. Then verify:

```text
GET {{baseUrl}}/transfers
GET {{baseUrl}}/accounts/{{sourceAccountId}}
GET {{baseUrl}}/accounts/{{destinationAccountId}}
GET {{baseUrl}}/accounts/{{sourceAccountId}}/transactions?page=0&size=20
GET {{baseUrl}}/accounts/{{sourceAccountId}}/statement?type=TRANSFER&status=COMPLETED&page=0&size=20
```

Send the exact transfer request again with the same idempotency key. It should return the same completed receipt and must not debit the account twice. Change the amount while keeping the same key; expect a conflict instead of a new transfer.

Also verify that a negative amount, insufficient balance, wrong OTP, expired OTP, inactive beneficiary, and a beneficiary resolving to the source account are rejected.

## 12. Test bill payments

List billers:

```text
GET {{baseUrl}}/billers
```

Choose the `ELECTRICITY` biller and save its ID as `billerId`. Send `POST {{baseUrl}}/bill-payments/otp-challenges`:

```json
{
  "sourceAccountId": {{sourceAccountId}},
  "billerId": {{billerId}},
  "billReference": "1234567890",
  "amount": 75.00
}
```

Save its `challengeId` as `billOtpChallengeId`, then copy the `BILL_PAYMENT` OTP from the identity-service terminal. Set `billPaymentKey` to a unique value such as `bill-local-0001`. Send `POST {{baseUrl}}/bill-payments`:

```json
{
  "sourceAccountId": {{sourceAccountId}},
  "billerId": {{billerId}},
  "billReference": "1234567890",
  "amount": 75.00,
  "idempotencyKey": "{{billPaymentKey}}",
  "otpChallengeId": "{{billOtpChallengeId}}",
  "otpCode": "123456"
}
```

Expect a completed receipt. Verify `GET {{baseUrl}}/bill-payments` and the source account transaction list. Repeat the exact request to verify idempotency, then change the amount with the same key and expect a conflict.

## 13. Test cards and loans

Send:

```text
GET {{baseUrl}}/cards
GET {{baseUrl}}/cards/{{cardId}}
```

Activate the fixture with `PATCH {{baseUrl}}/cards/{{cardId}}/status`:

```json
{
  "action": "ACTIVATE"
}
```

Expect `ACTIVE`. Test `BLOCK` and `UNBLOCK` in the same field. Verify that responses contain masked/tokenized data and never a full PAN or CVV.

Send:

```text
GET {{baseUrl}}/loans
GET {{baseUrl}}/loans/{{loanId}}
GET {{baseUrl}}/loans/{{loanId}}/payments?page=0&size=20
```

Set `loanPaymentKey` to a unique value with at least eight characters, such as `loan-local-0001`. Send `POST {{baseUrl}}/loans/{{loanId}}/payments`:

```json
{
  "sourceAccountId": {{sourceAccountId}},
  "amount": 250.00,
  "idempotencyKey": "{{loanPaymentKey}}"
}
```

Expect `201 Created`. Confirm that the source balance and loan outstanding principal both decrease and the payment appears in loan payment history. Repeating the same request key must not debit twice; changing the payload with that key should be rejected.

## 14. Test notifications and audit events

Financial operations publish durable events. Allow a few seconds for event delivery, then send:

```text
GET {{baseUrl}}/notifications?page=0&size=20
```

If a notification is present, save its ID as `notificationId` and send:

```text
PATCH {{baseUrl}}/notifications/{{notificationId}}/read
```

Expect `204 No Content`. Fetch the page again and confirm `read` is true.

A normal customer JWT must be denied:

```text
GET {{baseUrl}}/admin/audit-events
```

Expect `403 Forbidden`.

For a local admin authorization test, connect as `NB_IDENTITY` and run:

```sql
INSERT INTO user_role (user_id, role_id)
SELECT user_id, role_id
FROM app_user
CROSS JOIN role
WHERE username = 'microtest'
  AND role_code = 'ADMIN';

COMMIT;
```

Log in again through password and Microsoft Authenticator to obtain a new JWT containing the new role. Existing JWTs do not change. `GET {{baseUrl}}/admin/audit-events` should now return `200 OK` and recent audit events.

Remove the temporary admin role after the test:

```sql
DELETE FROM user_role
WHERE user_id = (SELECT user_id FROM app_user WHERE username = 'microtest')
  AND role_id = (SELECT role_id FROM role WHERE role_code = 'ADMIN');

COMMIT;
```

## 15. Verify discovery and gateway behavior

The system uses Eureka for discovery and requires exactly one registered instance for each target service. The gateway does not use client-side load balancing.

1. Confirm all expected instances are visible in Eureka.
2. Call `GET {{baseUrl}}/loans` and confirm success.
3. Stop `products-service` with `Ctrl+C`.
4. Wait for Eureka registration and discovery caches to update.
5. Call `GET {{baseUrl}}/loans` again; expect a service-unavailable response rather than silent routing to an unknown address.
6. Call `GET {{baseUrl}}/accounts`; it should continue working because that route resolves a different service.
7. Restart `products-service`, wait for registration, and confirm the loans route recovers.

The gateway exposes only declared public routes. Internal service endpoints should not be reachable through guessed gateway paths.

## 16. Security and negative checks

Complete these checks before considering the local backend verified:

- Protected endpoints return `401` without a JWT and for a modified or expired JWT.
- A customer JWT returns `403` from admin endpoints.
- A modified, expired, or wrong-purpose login challenge does not issue a JWT.
- A wrong or reused Microsoft Authenticator code does not issue a JWT.
- Another customer's account, card, loan, transaction, or notification ID is rejected.
- Invalid, expired, or intent-mismatched payment OTPs are rejected.
- Self-transfer, non-positive amount, insufficient balance, invalid bill reference, and disabled beneficiary are rejected.
- Retrying a completed operation with the same key returns the original result without a second debit.
- Reusing an idempotency key for different request data returns a conflict.
- Full card numbers, CVV, plaintext passwords, plaintext persistent OTPs, private keys, and TOTP secrets are absent from API responses and committed files.
- `.local/`, Postman secret values, and Oracle passwords remain untracked.

Check the worktree before committing:

```sh
git status --short
git ls-files | rg '(^|/)(\.local|\.env)(/|$)|\.py$'
```

The second command should not list local secrets or Python files.

## 17. Expected completion evidence

Keep a short local test record containing:

- commit SHA tested;
- Java, Node.js, and Oracle versions;
- `./mvnw verify`, architecture verification, and Oracle verification results;
- Eureka screenshot showing exactly one expected instance per application;
- Postman run summary with secrets hidden;
- successful Authenticator enrollment and login status codes;
- before and after balances for one transfer, bill payment, and loan payment;
- idempotency replay results;
- negative authorization and ownership results;
- any known failure with the responsible service log excerpt, with credentials and tokens redacted.

Do not store Authenticator QR images, manual keys, OTPs, JWTs, database passwords, or `.local` contents as evidence.
