# Microsoft Authenticator setup and verification

Microsoft Authenticator supports the standard TOTP provisioning format used by this application: SHA-1,
six digits, and a 30-second period.

## Responsibility split

The backend owns all security-sensitive work:

- generates the Base32 secret;
- encrypts the secret with AES-GCM before persistence;
- creates the `otpauth://` provisioning URI and QR image;
- verifies setup and login codes;
- issues a five-minute login challenge only after the password succeeds;
- issues an access token only after the challenge and TOTP code both succeed.

The frontend displays `qrCodeDataUri` in an image, provides the manual entry key as an accessible fallback,
collects the six-digit code, and calls the confirmation endpoint. It must not persist or log the provisioning
URI, QR data, manual key, password, challenge, or access token.

An Oracle JET view model can assign the response value to an observable and bind it to an image:

```javascript
self.qrCodeDataUri = ko.observable('');

const setup = await apiClient.post('/auth/totp/setup', credentials);
self.qrCodeDataUri(setup.qrCodeDataUri);
```

```html
<img data-bind="attr: { src: qrCodeDataUri }"
     alt="Scan this QR code with Microsoft Authenticator">
```

The setup response includes `Cache-Control: no-store`.

## End-to-end flow

### 1. Check the password and setup state

```http
POST /api/v1/auth/login
Content-Type: application/json

{
  "usernameOrEmail": "asha",
  "password": "user-entered-password"
}
```

For a user without an authenticator, the response status is `TOTP_SETUP_REQUIRED`.

### 2. Request enrollment data

```http
POST /api/v1/auth/totp/setup
Content-Type: application/json

{
  "usernameOrEmail": "asha",
  "password": "user-entered-password"
}
```

The response contains:

```json
{
  "provisioningUri": "otpauth://totp/...",
  "qrCodeDataUri": "data:image/png;base64,...",
  "manualEntryKey": "BASE32_SECRET",
  "issuer": "Internet Banking",
  "accountName": "asha"
}
```

Display `qrCodeDataUri`, open Microsoft Authenticator, choose **Add account**, choose **Other account**, and
scan the image. Use `manualEntryKey` only if scanning is unavailable.

### 3. Confirm enrollment

Enter the current six-digit code from Microsoft Authenticator:

```http
POST /api/v1/auth/totp/confirm
Content-Type: application/json

{
  "credentials": {
    "usernameOrEmail": "asha",
    "password": "user-entered-password"
  },
  "code": "123456"
}
```

A `204 No Content` response means the encrypted credential is enabled. An invalid or expired code returns
`401 Unauthorized`. The server and phone clocks must be synchronized.

### 4. Verify a login

Call `/api/v1/auth/login` again with the password. An enabled user receives:

```json
{
  "challengeId": "short-lived-signed-challenge",
  "status": "TOTP_REQUIRED"
}
```

Submit that challenge and the current Authenticator code:

```http
POST /api/v1/auth/login/verify-totp
Content-Type: application/json

{
  "challengeId": "short-lived-signed-challenge",
  "code": "654321"
}
```

A successful response contains a bearer access token. A missing, expired, modified, or wrong-purpose
challenge returns `401 Unauthorized`. A wrong Authenticator code also returns `401` and counts as a failed
login attempt.

## Verification checklist

1. Scan the QR code and confirm that Microsoft Authenticator shows `Internet Banking` and the username.
2. Submit the displayed code to `/totp/confirm`; expect `204`.
3. Confirm `user_totp.is_enabled = 'Y'` and `confirmed_at` is populated. Do not print the ciphertext.
4. Log in again; expect `TOTP_REQUIRED` with a non-empty `challengeId`.
5. Submit the current code with the challenge; expect a bearer access token.
6. Repeat with a wrong code; expect `401` and no token.
7. Modify one character of the challenge or wait beyond five minutes; expect `401`.
8. Try using the login challenge as a bearer token on a protected API; expect `401`.

Automated tests decode the generated PNG back to the exact provisioning URI, validate the standard TOTP
parameters, verify token-purpose separation, and cover successful and rejected authentication paths.
