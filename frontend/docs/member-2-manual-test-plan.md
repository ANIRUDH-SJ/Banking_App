# Member 2 manual test plan

Run these checks after PR #60 is merged and the backend services are running. The screens use its shared API client and session.

## Accounts and transactions

1. Sign in as a customer with at least one account.
2. Open **Accounts**; verify masked number, currency, available balance, and account status.
3. Open **Transactions**; verify the first 50 entries and search by narration/reference. Pagination remains to be implemented.
4. Select a valid date range; verify the statement matches the server response.
5. Choose a From date after the To date; verify the client-side validation message.
6. Export a statement; verify the downloaded CSV filename and content.

## Beneficiaries

1. Submit an empty form and an invalid IFSC/account number; verify validation feedback.
2. Add a valid beneficiary; verify the new status is pending.
3. Request an activation challenge, then enter an invalid OTP; verify the server error is shown.
4. Enter the valid OTP; verify the status becomes active.
5. Disable a beneficiary and refresh; verify the server remains the source of truth.

## Transfers

1. Enter zero, negative, or blank amount; verify transfer review is blocked.
2. Review a valid transfer and request an OTP.
3. Enter an invalid OTP; verify no receipt is shown.
4. Confirm using a valid OTP; verify the server receipt reference, amount, currency, and status.
5. Resubmit the same idempotency key only where the backend test plan permits it; verify no duplicate debit is created.
6. Check accessibility: keyboard-only navigation, visible focus, labels, live validation messages, and responsive mobile layout.
