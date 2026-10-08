# Cards, card PINs and credit-card billing

All card routes require a bearer JWT. The authenticated user's customer record scopes every lookup, so a
card owned by another customer is returned as not found.

| Method and path | Result |
| --- | --- |
| `GET /api/v1/cards` | Lists the customer's cards, with PIN state and, for credit cards, the bill. |
| `GET /api/v1/cards/{cardId}` | Returns one owned card. |
| `PATCH /api/v1/cards/{cardId}/status` | Applies an allowed status action. |
| `POST /api/v1/cards/{cardId}/reveal` | Returns the full card number and expiry for a short on-screen reveal. |
| `GET /api/v1/cards/pin-key` | The public key used to encrypt a PIN in the browser. |
| `PUT /api/v1/cards/{cardId}/pin` | Sets the first PIN, or changes it given the current PIN. |
| `GET /api/v1/cards/{cardId}/transactions?page=0&size=20` | Credit-card purchases, refunds and payments, newest first. |
| `GET /api/v1/cards/{cardId}/transactions/{id}/emi-options` | Monthly instalment, interest and total for each tenure. |
| `POST /api/v1/cards/{cardId}/transactions/{id}/emi` | Converts an eligible purchase to EMI. |
| `GET /api/v1/cards/{cardId}/emi-plans` | The card's EMI plans. |

## Status

The status request accepts `ACTIVATE`, `BLOCK`, or `UNBLOCK`. Activation is allowed only for an inactive
card, blocking only for an active card, and unblocking only for a blocked card. Invalid transitions return
`409 Conflict`.

## Card numbers

List and detail responses expose only `************4242` and `lastFour`. The full number is stored only as
AES-256-GCM ciphertext (`CARD_PAN_ENCRYPTION_KEY`, 32 bytes, base64), with the card token as associated
data so a ciphertext cannot be moved to another card. `revealable` says whether a card has a stored number.
`reveal` decrypts it for one response marked `no-store`, writes a `CARD_DETAILS_REVEALED` audit event, and
returns `revealSeconds` (30) after which clients hide it again. Closed and expired cards are not revealed.
**CVV is never stored or returned.** Without the key, numbers are not stored and reveal returns `409`.

## PINs

A PIN never travels or rests in clear text:

1. The client fetches `GET /api/v1/cards/pin-key` (`keyId`, `algorithm` `RSA-OAEP-256`, SPKI `publicKey`).
2. It encrypts `{"pin":"4826","issuedAt":<epoch ms>}` with RSA-OAEP/SHA-256 (WebCrypto in the browser).
3. It sends `pinKeyId` and the base64 ciphertext. Payloads older than five minutes are refused.

Only products-service holds the private key (`CARD_PIN_TRANSPORT_KEY`, PKCS#8 base64; a temporary key is
generated at start-up when it is unset). The PIN is stored as a BCrypt hash bound to the card.

`PUT /pin` takes `pinKeyId`, `encryptedPin` and, when a PIN already exists, `encryptedCurrentPin`. A PIN has
four digits; repeated digits (`1111`) and common sequences (`1234`, `4321`, ...) are refused. A PIN can be set
on an inactive or active card.

Three wrong PINs in a row (`CARD_PIN_MAX_ATTEMPTS`) lock the PIN for 30 minutes (`CARD_PIN_LOCK_MINUTES`).
Failures are answered with `422` and a field error, never `401`, so the customer stays signed in:

| Code | Status | Meaning |
| --- | --- | --- |
| `PIN_INCORRECT` | 422 | Wrong PIN; the message says how many attempts are left. |
| `PIN_LOCKED` | 423 | Locked; `Retry-After` gives the seconds until it unlocks. |
| `PIN_NOT_SET`, `PIN_REQUIRED` | 422 | No PIN yet, or the current PIN is missing on a change. |
| `PIN_WEAK`, `PIN_REUSED`, `PIN_FORMAT` | 422 | The new PIN is not acceptable. |
| `PIN_KEY_EXPIRED`, `PIN_UNREADABLE` | 422 | Fetch the key again and re-encrypt. |

Every set, change, rejection, lock and successful verification is audited.

### Paying with a PIN

Fund transfers and bill payments accept `cardPin` (`cardId`, `pinKeyId`, `encryptedPin`) in place of
`otpChallengeId` and `otpCode`; exactly one of the two must be sent. The card must be the customer's
**active debit card linked to the paying account**. payments-service forwards the ciphertext to
`POST /internal/cards/pin-verifications` on products-service and never sees the PIN. This is an extension of
the PRD, which specifies OTP for these actions; the OTP route is unchanged.

## Credit cards

Credit cards carry `credit`: `creditLimit`, `availableCredit`, `outstandingBalance`, `statementBalance`
(the current bill), `minimumDue`, `statementDate` and `paymentDueDate`. Transactions carry the merchant,
category, type (`PURCHASE`, `REFUND`, `PAYMENT`), status (`PENDING`, `POSTED`, `CONVERTED_TO_EMI`), whether
they are on the current bill, `emiEligible`, and the `emiPlan` once converted.

### EMI

A posted purchase of at least INR 2,500 (`CARD_EMI_MIN_AMOUNT`) from the last 60 days, on an active card,
can be converted once. Tenures and annual rates come from `CARD_EMI_RATES`
(default `3:13.00,6:14.00,9:15.00`). The instalment is the reducing-balance EMI
`P x r x (1 + r)^n / ((1 + r)^n - 1)` with `r` the monthly rate, rounded to paise; total payable is
`instalment x n`.

`POST .../emi` takes `tenureMonths` and an `idempotencyKey`; retrying with the same key returns the same
plan. On conversion the purchase becomes `CONVERTED_TO_EMI`. If it was on the current bill, the bill drops
by the purchase amount and gains the first instalment (due on the current due date); otherwise the first
instalment is due a cycle later. The outstanding balance grows by the plan's interest.

## Local demonstration cards

There is no card-issuing integration. With `CARD_DEMO_ISSUANCE=true` (set by `scripts/init-local.mjs`), a
customer without cards who opens the cards list receives a RuPay debit card and a Visa credit card linked
to their first active INR account, with a closed billing cycle and a few purchases. Never enable it in a
real deployment.

`scripts/init-local.mjs` writes `CARD_PAN_ENCRYPTION_KEY`, `CARD_PIN_TRANSPORT_KEY` and
`CARD_DEMO_ISSUANCE` into `.local/products-service.json`. For an existing `.local`, add them yourself:

```bash
node -e "const c=require('crypto');console.log(JSON.stringify({CARD_PAN_ENCRYPTION_KEY:c.randomBytes(32).toString('base64'),CARD_PIN_TRANSPORT_KEY:c.generateKeyPairSync('rsa',{modulusLength:2048}).privateKey.export({type:'pkcs8',format:'der'}).toString('base64'),CARD_DEMO_ISSUANCE:'true'},null,2))"
```

Flyway migration `V6__card_security_and_credit.sql` adds the columns and tables.

The older `development_seed_data` script inserts card `0103` as `CREDIT` without the
billing columns introduced in V6. For an existing local demo database, run
`database/development_credit_card_0103.sql` as `NB_PRODUCTS` once. It updates only
the matching seed card with a demo limit and zero balances; it is not a
production migration. The card has no seeded purchases, so its transaction list
remains empty until demo transactions are added.

When `CARD_DEMO_ISSUANCE=true` and `CARD_PAN_ENCRYPTION_KEY` is configured, the
products service also encrypts a synthetic PAN for legacy development seed cards
that have no protected number. This makes the eye control available without ever
storing a clear-text PAN. The number is returned only by the authenticated reveal
endpoint, uses no-store response headers, and is automatically masked again after
30 seconds.
