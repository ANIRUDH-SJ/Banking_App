# Member 1 manual test plan

Run the gateway on port 8080 and the JET app with `npx ojet serve` from `frontend/` so the origin is `http://localhost:8000`.

## Signed-out shell

- Open the app. The header shows Internet Banking, Sign in, and Register. Customer navigation is hidden.
- Submit sign-in with an empty username and a short password. Both fields show validation text and no request is required for the empty username.
- Open Register and leave the form incomplete. Each invalid field is identified. A future date of birth is rejected. A password shorter than 12 characters is rejected.
- Register a new customer. The customer number is shown, and the password field is cleared. Continue to sign-in does not enter the app without authenticator setup.

## Authenticator and session

- Sign in before authenticator setup. The setup screen shows a QR image with alternative text and a manual entry key. Confirm a valid code, then sign in again.
- The second sign-in asks for a 6-digit code. A valid code opens the dashboard. A wrong code stays on the verification screen and does not store a session.
- Refresh during setup or verification. The in-memory challenge is gone, so the app returns to sign-in.
- In the browser session storage, `nb.session.v1` contains the token, type, user id, username, roles, and expiry only.
- Wait until less than one minute remains. The header warns that the sign-in cannot be extended. At expiry the session is removed and sign-in explains that the session ended.

## Profile, dashboard, and notifications

- The dashboard shows the profile name, customer number, KYC status, and the latest notifications. With no account or product module registered, the accounts and products section explains that those summaries are supplied elsewhere.
- Profile shows read-only customer number, date of birth, and KYC status. Saving a valid mobile number updates the profile. An invalid mobile number is rejected on the form.
- The notification center shows an empty state when there are no items. Mark an unread item as read and confirm the label changes. Previous and Next follow the paged response.
- Sign out. The session key is removed and protected pages return to sign-in.

## Administrator navigation

- Sign in as a customer. Administration is absent from the navigation.
- Sign in as an administrator. Administration is visible and states that monitoring screens belong to the administration module. It does not show user, account, or audit records.
