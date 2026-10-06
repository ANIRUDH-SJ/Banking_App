# Member 3 frontend integration

PR #60 owns the Oracle JET shell, navigation, session, sign-in, one-time-code requests, API client, and bank design tokens. Merge it before this draft PR. The billers, cards, loans, and administration pages here are loaded at the routes already reserved by that shell.

The feature services import `services/registry` and use `registry.apiClient`, `registry.accounts`, `registry.cards`, `registry.loans`, or `registry.otp`. They do not maintain a second token store, sign-in dialog, HTTP error parser, or API base URL. A `401` is handled by the foundation session flow.

The page views use the foundation's `nb-page`, `nb-page-head`, `nb-kicker`, and `nb-lede` classes. `member3.css` applies the same petrol, brass, paper, status, typography, and radius tokens to feature cards and tables; it loads once when a Member 3 route opens. The branch does not change the shared shell files.

## Verification

- Run `node frontend/test/member-3-services.test.js` to check shared-service delegation.
- After PR #60 is merged, run `npm test` and `npx ojet build web` in `frontend/`.
- Sign in as a customer and walk through biller listing/payment OTP, card status controls, and loan repayment. Sign in as an administrator for the operations overview. Confirm network failures, expired sessions, narrow screens, and keyboard focus states.

This PR remains a draft until those browser and live-backend checks are complete.
