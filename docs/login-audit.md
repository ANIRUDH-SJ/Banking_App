# Login audit trail

The identity service writes an immutable `login_audit` row for completed logins and rejected login
attempts. Failure records use a separate transaction so the row is retained when authentication
returns an error.

## Recorded outcomes

| Authentication event | Outcome | Failure reason |
|---|---|---|
| Access token issued after valid password and TOTP | `SUCCESS` | — |
| Username or email is unknown | `FAILURE` | `UNKNOWN_PRINCIPAL` |
| Account is locked, disabled, or otherwise ineligible | `FAILURE` | `ACCOUNT_NOT_ELIGIBLE` |
| Password is invalid | `FAILURE` | `INVALID_CREDENTIALS` |
| Login challenge is invalid, expired, or refers to no user | `FAILURE` | `INVALID_CHALLENGE` |
| Authenticator code is invalid | `FAILURE` | `INVALID_TOTP` |

Each row includes the attempted username where available, the known user relationship, occurrence
time, connection address, and user agent. Untrusted strings are truncated to the committed Oracle
column limits before persistence.

The client address comes from the gateway connection and never from caller-supplied `Forwarded` or
`X-Forwarded-For` values. A deployment behind a reverse proxy must configure trusted forwarding at
the infrastructure boundary; otherwise the recorded address is the proxy address. Raw passwords,
authenticator codes, challenges, and access tokens are never stored in the audit row.

The existing `login_audit` table belongs only to the identity-service schema. Cross-service audit
events remain the responsibility of the audit-reporting service and its event ingestion path.
