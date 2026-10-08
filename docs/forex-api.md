# Demonstration forex conversions

Forex uses two active accounts owned by the same customer, with different currencies. Customers
can open one savings wallet per supported foreign currency (USD, EUR, GBP, JPY, AUD, CAD or SGD)
with `POST /api/v1/accounts/foreign-currency` and `{"currencyCode":"USD"}`. Opening the same
currency again returns the existing account. The ordinary onboarding account is INR. Any two
wallets can be converted, for example JPY to GBP, not only to and from INR.

The payments service accepts **operator-configured demonstration rates only**. There is no live
market-data feed, external FX settlement, spread, fee, or regulatory suitability check. Do not use
these rates for real money.

Rates come from `FOREX_REFERENCE_RATES`, the INR value of one unit of each supported currency
(default `INR=1,USD=85.00,EUR=92.50,GBP=108.00,JPY=0.5700,AUD=56.20,CAD=62.40,SGD=63.80`). The rate
between two currencies is the ratio of their reference values, rounded to 8 decimals, so every
combination is consistent. `FOREX_DEMO_RATES` (for example `USD/INR=84.90000000`) overrides a single
direction when an operator needs a specific figure. `FOREX_RATES_AS_OF` (an ISO-8601 instant)
states when the figures were taken; it defaults to service start. A currency that is not
configured fails instead of silently inventing a rate. Converted amounts are rounded to the
destination currency's minor unit (no decimals for JPY). A quote snapshots its rate and expires
after 120 seconds by default.

Two read-only routes power the currency converter; they book nothing:

- `GET /api/v1/forex/rates` lists the supported currencies with their names, minor units and INR
  reference values, plus `rateSource` and `asOf`.
- `GET /api/v1/forex/rates/convert?from=JPY&to=GBP&amount=10000` returns the rate, the inverse
  rate, the converted amount, `rateSource` and `asOf`.

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
