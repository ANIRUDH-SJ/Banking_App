# OTP delivery

The identity service generates, hashes, expires, and verifies one-time codes. Outside the `local`
profile it sends each new challenge to the notification service over an authenticated internal HTTP
request. The notification service dispatches the code through its configured email provider and,
when the customer has a mobile number, its SMS provider.

## Security properties

- Only `identity-service` service credentials can call `POST /internal/otp-deliveries`.
- Commands accept exactly six numeric digits and a known OTP purpose.
- The delivery response contains the challenge identifier and channel names, never the code.
- OTP messages are not stored as in-app notifications or notification payloads.
- The local adapter suppresses the raw code instead of writing it to application logs.
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
