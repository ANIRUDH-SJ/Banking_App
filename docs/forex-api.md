# Demonstration forex conversions

Forex uses two active accounts owned by the same customer, with different currencies. Customers
can open one USD, EUR, or GBP savings account with `POST /api/v1/accounts/foreign-currency` and
`{"currencyCode":"USD"}`. Opening the same currency again returns the existing account. The
ordinary onboarding account is INR.

The payments service accepts **operator-configured demonstration rates only**. There is no live
market-data feed, external FX settlement, spread, fee, or regulatory suitability check. Do not use
these rates for real money. Local runs default to both directions of INR against USD, EUR and
GBP (for example `USD/INR=85.00000000`), so a new wallet can be quoted straight away. Set
`FOREX_DEMO_RATES` in the payments-service environment to replace the whole list. Each direction
is configured independently; an inverse rate is never inferred. A pair that is not in the list
fails instead of silently inventing a rate. A quote snapshots its rate and expires after 120 seconds by default.

All routes require a customer access token at the API gateway:

1. `POST /api/v1/forex/quotes` with `sourceAccountId`, `destinationAccountId`, and `sourceAmount`.
   The response identifies its `rateSource` as `DEMO_CONFIGURED` and returns the exact rate,
   rounded destination amount, and expiry.
2. `POST /api/v1/forex/quotes/{quoteId}/otp-challenges` to request a purpose-bound OTP.
3. `POST /api/v1/forex/conversions` with `quoteId`, `otpChallengeId`, `otpCode`, and a unique
   `idempotencyKey` (at most 64 characters). Retry an uncertain outcome with the **same** key.
4. `GET /api/v1/forex/conversions` and `GET /api/v1/forex/conversions/{id}` return only the
   customer's own conversion history.

The accounts-ledger service debits source funds and credits destination funds in one database
transaction. It records separate currency-specific transaction references. The conversion
operation is idempotent; authorized operations recover after a network timeout or process restart.
An invalid/expired OTP cannot debit an account. An already used quote cannot authorize a second
conversion. Insufficient funds fail atomically without crediting the destination.
