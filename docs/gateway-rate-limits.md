# Gateway authentication rate limits

The API gateway limits `POST /api/v1/auth/**` requests before forwarding them to the identity
service. By default, one client address may make 10 authentication requests in a fixed 60-second
window. Password-reset requests use a separate 10-request bucket per client address and window,
so exhausted sign-in attempts do not prevent account recovery. Other API routes and CORS preflight
requests are not counted.

When the limit is exceeded, the gateway returns `429 Too Many Requests` with:

- a `Retry-After` header containing the remaining window duration in seconds;
- `Cache-Control: no-store`; and
- the standard correlated API error body with code `RATE_LIMITED`.

Configure the limit through the gateway process environment:

| Environment variable | Default | Meaning |
|---|---:|---|
| `AUTH_RATE_LIMIT_MAX_REQUESTS` | `10` | Allowed authentication requests per client and window |
| `AUTH_RATE_LIMIT_WINDOW_SECONDS` | `60` | Fixed-window duration in seconds |

Both values must be positive. Invalid values prevent the gateway from starting so that an unsafe
configuration cannot silently disable throttling.

## Client identity and deployment limits

The limiter keys requests by the gateway connection's remote address. It does not trust
caller-supplied `Forwarded` or `X-Forwarded-For` headers, which prevents a caller from evading the
limit by rotating a forged header value.

The current limiter is in memory and intentionally targets the repository's single-instance local
deployment. Limits reset when the gateway restarts and are not shared between gateway instances.
Before a multi-instance production rollout, place the gateway behind a trusted-proxy configuration
that replaces untrusted forwarding headers, then use a shared rate-limit store or an edge rate
limiter. Otherwise every request from a reverse proxy would share the proxy's socket address while
each gateway instance would maintain a separate counter.

Expired client windows are removed periodically to keep one-off client addresses from accumulating
in memory.
