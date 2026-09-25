# Eureka service discovery

The registry runs on port 8761. All six business applications and the gateway register under their `spring.application.name`. Eureka displays names in uppercase; clients use the lowercase service IDs listed in the README.

`ServiceResolver` calls Spring's `DiscoveryClient.getInstances(serviceId)` on each request:

- Zero instances: return `503 Service Unavailable`.
- One instance: use its HTTP/HTTPS URI directly.
- More than one instance: return `503`, rather than choosing an instance.

This is discovery without load balancing. The Maven dependencies exclude Spring Cloud LoadBalancer; there are no `lb://` routes or `@LoadBalanced` clients. The registry is not a reverse proxy. The gateway discovers the destination and forwards only the explicitly allowed public API prefixes.

Internal HTTP clients have a three-second connection timeout and ten-second response timeout. The gateway allows thirty seconds for upstream processing. There are no automatic HTTP mutation retries. Payment and repayment recovery workers retry durable operations using the original ledger operation ID.

## Verify locally

1. Start the applications using [teammate setup](TEAMMATE_QUICK_START.md).
2. Visit `http://localhost:8761` using the generated registry credentials.
3. Confirm one instance each of identity, accounts-ledger, payments, products, notification and audit-reporting, plus api-gateway.
4. Check `http://localhost:8080/api/v1/health`. This confirms the gateway itself is available, not that every downstream service is ready.
5. Register/login through the gateway and request an authenticated resource. A valid token is checked again by the destination service.
6. Stop a downstream service. Requests to it should fail with `503`, either from the bounded HTTP timeout or discovery lookup. Other independent routes should continue working.

Eureka caches registration information, so startup and removal are not instant. During a stale registration window, a refused connection still fails closed. Internal endpoints remain inaccessible through gateway routing even when a client supplies service headers.

All components bind to loopback by default. For a future multi-host deployment, set reachable service hostnames and bind addresses, protect Eureka and internal ports on a private network, and use TLS. A single registry and one instance per service are deliberate local-development availability limits.

References: [Spring registration and discovery guide](https://spring.io/guides/gs/service-registration-and-discovery/), [Eureka DiscoveryClient implementation](https://github.com/spring-cloud/spring-cloud-netflix/blob/main/spring-cloud-netflix-eureka-client/src/main/java/org/springframework/cloud/netflix/eureka/EurekaDiscoveryClient.java).
