# Member 1 foundation contract

The Oracle JET shell in `frontend/` owns authentication, session storage, navigation, shared validation, and the one-time-code request used by other screens. Account, payment, card, loan, and administration screens keep their own review, submission, and receipt behaviour.

RequireJS `baseUrl` is `js`. Import the shared registry with the module id `services/registry`.

## API client

```javascript
define(['services/registry', 'services/account-service'], function (registry, AccountService) {
  var accounts = new AccountService(registry.apiClient);

  accounts.getAccounts().then(function (rows) {
    // rows is the parsed JSON body
  }).catch(function (error) {
    // error.status, error.code, error.message, error.fieldErrors, error.correlationId, error.retryAfter
  });
});
```

`registry.apiClient` methods:

| Method | Arguments |
| --- | --- |
| `get(path, options)` | Authenticated `GET` |
| `post(path, body, options)` | JSON body. Omit `body` when the endpoint has none. |
| `put(path, body, options)` | JSON body |
| `patch(path, body, options)` | JSON body. A read-mark with no body omits the body. |
| `delete(path, options)` | Authenticated `DELETE` |

`path` is the gateway path, beginning with `/api/v1`. The dev server proxies `/api/` to `http://127.0.0.1:8080`. Set `window.NET_BANKING_API_BASE` before startup only when the API is on another origin.

`options.auth` defaults to `true`. Public identity calls pass `{ auth: false }`. A protected call without a usable session, and a protected `401`, clear the session and move the shell to `session-expired`.

Successful `204` responses resolve to `null`. JSON responses resolve to the parsed object. Any other successful body resolves to text, which covers statement CSV downloads.

## Session

```javascript
var token = registry.session.getAccessToken();
var session = registry.session.getSession();
```

`getSession()` returns `null` when there is no session or the access token's `exp` has passed. Otherwise it returns:

```javascript
{
  accessToken: 'jwt',
  tokenType: 'Bearer',
  userId: 7,
  username: 'asha',
  roles: ['CUSTOMER'],
  expiresAt: 1710000000000
}
```

The shell stores that record in `sessionStorage` under `internet-banking.session`. Passwords, authenticator secrets, setup QR data, and login challenges are not stored. `registry.session.clear()` removes the record. There is no refresh token; the access token lasts 60 minutes and the shell signs out when it expires.

`registry.session.hasRole('ADMIN')` and `hasRole('CUSTOMER')` read the current session.

## Errors

Failed responses become an `ApiError`:

```javascript
{
  name: 'ApiError',
  status: 400,
  code: 'VALIDATION_FAILED',
  message: 'Request validation failed.',
  path: '/api/v1/auth/register',
  correlationId: 'server-correlation-id',
  fieldErrors: { email: 'Enter a valid email address.' },
  retryAfter: '30'
}
```

Known `code` values include `VALIDATION_FAILED`, `UNAUTHORIZED`, `FORBIDDEN`, `NOT_FOUND`, `CONFLICT`, `INVALID_STATE`, `INVALID_REQUEST`, `RATE_LIMITED`, `UPSTREAM_REQUEST_FAILED`, and `INTERNAL_ERROR`. A transport failure uses `NETWORK`. Read a field with `error.fieldErrors.email`. Show `error.message` as the bank's message. Do not replace a payment or balance result with a value calculated in the browser.

## One-time code

`registry.otp` requests a challenge. The calling screen still submits the code on its own confirm endpoint.

| Method | Endpoint | Body |
| --- | --- | --- |
| `requestTransferChallenge(sourceAccountId, beneficiaryId, amount)` | `POST /api/v1/transfers/otp-challenges` | `sourceAccountId`, `beneficiaryId`, `amount` |
| `requestBeneficiaryActivation(beneficiaryId)` | `POST /api/v1/beneficiaries/{beneficiaryId}/activation-challenges` | none |
| `requestBillPaymentChallenge(request)` | `POST /api/v1/bill-payments/otp-challenges` | `sourceAccountId`, `billerId`, `billReference`, `amount` |
| `requestForexChallenge(quoteId)` | `POST /api/v1/forex/quotes/{quoteId}/otp-challenges` | none |

Each method resolves to `{ challengeId, status }`. Payment challenges use status `OTP_SENT`. `registry.otp.validateCode(code)` returns an empty string for six digits and a shared validation message otherwise.

Loan repayment does not have a challenge endpoint. Submit that payment with its idempotency key from the loans screen.

Activate a beneficiary with `POST /api/v1/beneficiaries/{beneficiaryId}/activate` and body `{ otpChallengeId, otpCode }`. Confirm a transfer with `POST /api/v1/transfers` and the transfer body, including `otpChallengeId` and `otpCode`. Those submissions stay in the accounts workspace.

## Routes

The shell uses the Oracle JET `UrlParamAdapter`. The address is `?ojr=<path>`.

| Path | Who can open it | Owner |
| --- | --- | --- |
| `login`, `register`, `password-recovery`, `totp-setup`, `totp-verify`, `session-expired` | Public | App foundation |
| `dashboard`, `profile`, `notifications` | `CUSTOMER` | App foundation |
| `accounts`, `transactions`, `beneficiaries`, `transfer` | `CUSTOMER` | Accounts and transfers |
| `billers`, `bill-payments`, `cards`, `loans` | `CUSTOMER` | Payments and administration |
| `deposits`, `forex` | `CUSTOMER` | Products |
| `admin`, `admin-users`, `admin-accounts`, `admin-transactions`, `admin-audit` | `ADMIN` | Payments and administration |

Navigate with `registry.go('accounts')`. A customer who opens an administrator route returns to `dashboard`. An administrator without the customer role returns to `admin`. The navigation labels for the accounts workspace are Accounts, Transactions, Beneficiaries, and Transfer.

If a workspace view is not in this branch yet, the shell keeps the route and shows a reserved page. Adding `viewModels/<path>.js` and `views/<path>.html` replaces that page.

The masthead groups routes into Overview, Accounts, Payments, Products (cards, loans, FD & RD, forex), and Profile. The bar under the masthead lists the routes in the current group, so a new screen appears there as soon as its route is in the router.

## Screen building blocks

Screens use the JET Core Pack components under `oj-c/` (`oj-c-input-text`, `oj-c-input-password`, `oj-c-button`, `oj-c-form-layout`, `oj-c-badge`, `oj-c-skeleton`). Field messages go in `messages-custom` with the text in both `summary` and `detail`, because Core Pack fields display `detail`.

The stylesheet provides these page classes:

| Class | Use |
| --- | --- |
| `nb-page`, `nb-page-head`, `nb-kicker`, `nb-lede` | page frame, title block, eyebrow, intro text |
| `nb-sheet`, `nb-sheet-head` | white panel with a heading row |
| `nb-alert` with `nb-alert-text` and `nb-alert-ref` | form or server error and its reference |
| `nb-success` | confirmation message |
| `nb-empty-panel` | empty state |
| `nb-actions` | button row |
| `nb-icon i-<name>` | icon from `css/icons/<name>.svg` in the current text colour |
| `nb-work` with `nb-aside` | main panel with a history rail that stacks below 1080px |
| `nb-flow-sheet`, `nb-flow`, `nb-flow-panel` | stepped payment: steps bar and one panel per step |
| `nb-choices`, `nb-choice`, `nb-picked`, `nb-glyph` | selectable tiles, such as billers, and the chosen tile |
| `nb-review`, `nb-review-amount`, `nb-review-figure` | label and value list for review and receipt steps |
| `nb-note` (`is-warn`) | inline explanation, such as where the one-time code is sent |
| `nb-otp`, `nb-code-input` | six-digit code field |
| `nb-receipt` with `nb-done-mark` (`is-clock` while pending) | final step of a payment |
| `nb-toolbar` | filter row above a list or table |
| `nb-table` | data table; rows stack on phones, `is-wide` columns are hidden there |
| `nb-items`, `nb-item`, `nb-item-side`, `nb-item-extra` | list rows with actions and an inline panel |
| `nb-inline-panel` (`is-danger`) | confirmation or code entry inside a row |
| `nb-quote` | rate, amount and maturity summary for deposits and forex |
| `nb-meter` | progress bar for loan repayment and recurring instalments |
| `nb-paycard` | card artwork on the cards screen |

`services/ui-support` holds the helpers these screens share:

| Helper | Use |
| --- | --- |
| `Flow(names)` | step index with `at`, `is(i)`, `stateOf(i)` and `go(i)`; `go` focuses the visible `.nb-flow-panel h2[tabindex]` |
| `Problem()` | form error `text` and gateway `reference`, set from an `ApiError` |
| `messages(text)` | `messages-custom` array for a field |
| `options(rows)`, `accountOption(account)` | `oj-c-select-single` data keyed on `value` |
| `amountError(value, min, max, currency)`, `parseAmount(value)` | amount checks with two decimal places |
| `amountConverter` | two-decimal converter for `oj-c-input-number` |
| `newKey(prefix)` | idempotency key for one submission; reuse it when the user retries |
| `hand(key, value)`, `take(key)` | one-time selection passed between screens, such as a biller or beneficiary |
| `statusVariant(status)` | `oj-c-badge` variant for a status |

## Validation messages

Use `registry.validation` for shared field checks. Screen-specific rules, such as an IFSC or transfer amount, stay with that screen.

| Method | Rule |
| --- | --- |
| `username` | 3 to 100 characters |
| `email` | email, at most 254 characters |
| `usernameOrEmail` | 3 to 254 characters |
| `password` | 12 to 128 characters |
| `passwordMatch` | both entries match |
| `personName` | required, at most 100 characters |
| `dateOfBirth` | `YYYY-MM-DD` in the past |
| `mobile` | `^[0-9+][0-9 -]{7,19}$` |
| `otp` | six digits |

An empty string means the value is acceptable. Any other return value is the message to show.
