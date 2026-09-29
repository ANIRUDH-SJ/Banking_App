# Password reset

Password resets use the same hashed, expiring, one-time OTP infrastructure as payment challenges.
Both endpoints are public under `/api/v1/auth`, but neither issues an access token.

## Request a challenge

```http
POST /api/v1/auth/password-reset/challenges
Content-Type: application/json

{
  "usernameOrEmail": "asha@example.com"
}
```

The response always uses the generic `OTP_SENT_IF_ACCOUNT_EXISTS` status and contains a UUID-shaped
challenge identifier. Unknown, disabled, and rate-limited accounts receive the same response shape as
eligible accounts. Clients must not interpret the response as confirmation that an account exists.

Only active and temporarily locked accounts receive a real `PASSWORD_RESET` OTP. Issuing a new reset
challenge expires an older pending reset challenge and uses the configured OTP issue window.

## Confirm the reset

```http
POST /api/v1/auth/password-reset/confirm
Content-Type: application/json

{
  "challengeId": "2f0bff82-7aca-43f9-b2b4-40ae50645558",
  "code": "123456",
  "newPassword": "a-new-strong-password"
}
```

A successful request returns `204 No Content`. The new password must contain 12 to 128 characters and
must differ from the current password. The service locks the user row while replacing the encoded
password and clears a temporary login lock after success.

Invalid, expired, previously consumed, wrong-purpose, or synthetic challenges return `401 Unauthorized`.
Failed OTP attempts count toward the existing maximum-attempt policy. Password values and OTP codes must
never be logged or returned in an error response.
