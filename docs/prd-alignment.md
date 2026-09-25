# PRD alignment summary

The current repository now has the required Java 17 / Spring Boot 3.2 backend foundation, Oracle JDBC support,
Spring Security, a versioned health endpoint, and example local configuration without committed secrets.

## Implemented database foundation

- Identity roles, users, and user-role mapping
- Banks, branches, customers, addresses, accounts, account holders, beneficiaries, transactions, ledger entries,
  login audit, and audit events

## Required follow-up database modules

The PRD also requires modules that are not yet implemented. They should be delivered in separate reviewed migrations
and backend modules:

1. OTP verification - hashed OTP, expiry, one-time status, and purpose.
2. Fund transfers - idempotency key, distinct source/destination, transfer lifecycle, and atomic balance update.
3. Billers and bill payments.
4. Masked/tokenized cards and allowed card-status actions. Never store CVV or complete unprotected card numbers.
5. Loans, loan repayments, and due-date information.
6. In-app notifications plus testable email/SMS adapter delivery states.

## First backend endpoint

`GET /api/v1/health` is intentionally public and returns the backend availability. All future customer, administrator,
and financial endpoints must require authentication and role/ownership checks.
