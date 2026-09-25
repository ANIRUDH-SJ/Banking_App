# Frontend work division

The customer and administrator experiences live in one Oracle JET application under `frontend/`. Each teammate owns complete screens and the frontend services behind them. The shared shell and API conventions have one owner so the three areas can be integrated without duplicate authentication or request handling.

| Teammate | Screens and flows | Frontend services |
|---|---|---|
| **Member 1: App foundation and customer access** | App shell, navigation, registration, login, logout, password recovery, Microsoft Authenticator setup and verification, session expiry, customer profile, dashboard, and notification center | `ApiClientService`, `AuthService`, `OtpService`, `SessionService`, `RouteGuardService`, `ProfileService`, `NotificationService`, shared `ValidationService` |
| **Member 2: Accounts and transfers** | Account list and details, balances and account status, transaction history and filters, statement export, beneficiary management, transfer form, review, OTP confirmation, receipt, and status | `AccountService`, `TransactionService`, `BeneficiaryService`, `FundTransferService` |
| **Member 3: Payments, products, and administration** | Biller catalogue, bill payment and history, masked card details and permitted controls, loan details and repayments, admin dashboard, user and account monitoring, transaction monitoring, and audit viewer | `BillerService`, `BillPaymentService`, `CardService`, `LoanService`, `AdminService` |

## Shared boundaries

- Member 1 owns the API client, session storage and cleanup, role-aware routing, navigation, and shared validation messages. Members 2 and 3 use those services and implement the rules specific to their forms.
- Member 1 assembles the dashboard. Member 2 provides account balances and recent transactions; Member 3 provides card and loan summaries where shown. The dashboard does not duplicate their API calls or business rules.
- Member 2 uses the shared OTP flow for transfers. Member 3 uses it for bill and loan payments when confirmation is required. Each payment owner remains responsible for its own review, submission, status, and receipt screens.
- Member 3 owns admin pages. Member 1 provides the admin route guard and role-aware navigation; admin data requests stay in `AdminService`.
- Each teammate documents the request and response shapes used by their screens, including validation and error responses. Agree on route names and shared API response handling before connecting the areas.

## Delivery expectations

For every owned flow, include responsive and accessible screens, client-side validation, loading and empty states, success and error handling, and appropriate tests. Use server responses as the authority for balances, permissions, and payment outcomes. Keep work in separate feature branches based on the same frontend scaffold, with a focused pull request for each ownership area.
