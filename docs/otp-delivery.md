# OTP delivery

The identity service generates, hashes, expires, and verifies one-time codes. It sends each new
challenge to the notification service over an authenticated internal HTTP request, in every profile
including `local`. The notification service dispatches the code through its configured email provider and,
when the customer has a mobile number, its SMS provider.

## Security properties

- Only `identity-service` service credentials can call `POST /internal/otp-deliveries`.
- Commands accept exactly six numeric digits and a known OTP purpose.
- The delivery response contains the challenge identifier and channel names, never the code.
- OTP messages are not stored as in-app notifications or notification payloads.
- The log-only adapters write a raw code only when a development flag below is set.
- The identity database stores only the password-encoded OTP value and its expiry.

The internal request contains the raw code because the channel provider must deliver it. Deployments
must therefore use TLS and private service networking in addition to the existing service-token
authentication.

## Delivery behavior

Registered email is the required delivery channel. SMS is added when a customer profile with a
mobile number exists. This allows login challenges to be delivered before customer onboarding has
finished, while payment and beneficiary challenges can use both channels.

Provider failures propagate back to identity. OTP issuance is transactional, so a challenge is not
committed when its delivery request fails. Clients can request a new challenge after the provider is
available again, subject to the configured issuance limits.

Production deployments must provide non-local `EmailService` and `SmsService` implementations in the
notification service. The development implementations deliberately avoid logging message bodies.

## Local development

With no SMTP or SMS provider configured, the notification service uses its log providers. The
local launcher (`node scripts/run-service.mjs` and `run-all-services.mjs`) sets
`NOTIFICATION_LOG_MESSAGE_CONTENT=true` for the notification service, so each OTP appears in its
console:

```text
DEVELOPMENT ONLY: email to ***@example.com with subject Your banking verification code: Your banking verification code is 482913. ...
```

Set `NOTIFICATION_LOG_MESSAGE_CONTENT` to `false` in `.local/notification-service.json` to hide
codes, or configure SMTP as described in [SMTP email delivery](smtp-email.md) to receive them by
email. Never enable the flag outside a developer machine.

| Property | Environment variable | Default | Effect |
| --- | --- | --- | --- |
| `app.security.otp.delivery` | `OTP_DELIVERY` | `notification` | `log` keeps challenges inside the identity service, for tests and isolated runs |
| `app.security.otp.log-codes` | `OTP_LOG_CODES` | `false` | with `log` delivery, prints the code in the identity console |
| `app.notifications.log-message-content` | `NOTIFICATION_LOG_MESSAGE_CONTENT` | `false` | log email and SMS providers print the message |
