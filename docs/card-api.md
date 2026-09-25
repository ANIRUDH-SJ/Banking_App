# Card controls

All card routes require a bearer JWT. The authenticated user's customer record scopes every lookup, so a
card owned by another customer is returned as not found.

| Method and path | Result |
| --- | --- |
| `GET /api/v1/cards` | Lists the customer's cards. |
| `GET /api/v1/cards/{cardId}` | Returns one owned card. |
| `PATCH /api/v1/cards/{cardId}/status` | Applies an allowed status action. |

The status request accepts `ACTIVATE`, `BLOCK`, or `UNBLOCK`. Activation is allowed only for an inactive
card, blocking only for an active card, and unblocking only for a blocked card. Invalid transitions return
`409 Conflict`.

The database stores a provider token and the final four digits. API responses expose only a value such as
`************4242`. The model and schema contain no field for a complete card number or CVV.

The products service applies its own Flyway migrations in `NB_PRODUCTS`. Customer and account IDs are logical references; no cross-schema foreign keys are used.
