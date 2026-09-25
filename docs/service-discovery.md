# Service discovery

The repository includes a standalone Eureka registry and configures the banking backend as a Eureka client. The registry gives independently deployed applications a stable service name and a live catalogue of healthy instances.

```text
Browser
   |
   v
net-banking-backend ---- register and renew ----> service-registry
        |                                            :8761
        v
   Oracle Database
```

The backend is currently registered as `NET-BANKING-BACKEND`. Eureka normalizes application names to uppercase in its dashboard and API.

## Compatibility

Both applications use Java 17 and Spring Boot 3.2.12. Spring Cloud is pinned to 2023.0.4, the release train built against Spring Boot 3.2.12.

## Run locally

### Prerequisites

- Java 17
- A reachable Oracle database with the scripts in `database/` applied
- Local backend settings created from `backend/src/main/resources/application-local.properties.example`

Start the registry first:

```bash
cd service-registry
./mvnw spring-boot:run
```

In another terminal, start the backend:

```bash
cd backend
./mvnw spring-boot:run
```

Open `http://localhost:8761`. After the backend starts and sends its first heartbeat, `NET-BANKING-BACKEND` appears under **Instances currently registered with Eureka**.

The registry can also be checked without the dashboard:

```bash
curl http://localhost:8761/actuator/health
curl -H 'Accept: application/json' http://localhost:8761/eureka/apps
```

## Run with Docker Compose

Copy the environment template and replace every placeholder:

```bash
cp .env.example .env
```

`DOCKER_DB_URL` must point to an Oracle listener that containers can reach. The default uses `host.docker.internal`, which targets an Oracle instance running on the development machine.

Build and start both applications:

```bash
docker compose -f compose.discovery.yml up --build
```

The registry dashboard is available at `http://localhost:8761`, and the backend is available at `http://localhost:8080`. Stop the stack with:

```bash
docker compose -f compose.discovery.yml down
```

The Compose file does not create or initialize Oracle. It connects the backend to the database configured by `DOCKER_DB_URL`.

## Configuration

| Variable | Default | Purpose |
|---|---|---|
| `EUREKA_SERVER_PORT` | `8761` | Registry port exposed on the host |
| `EUREKA_DEFAULT_ZONE` | `http://localhost:8761/eureka/` | Registry endpoint used by a client |
| `EUREKA_CLIENT_ENABLED` | `true` | Enables backend registration and discovery |
| `EUREKA_INSTANCE_HOSTNAME` | `localhost` | Hostname advertised by an application instance |
| `EUREKA_PREFER_IP_ADDRESS` | `false` | Advertises the instance IP address when enabled |
| `BACKEND_PORT` | `8080` | Backend port exposed on the host by Compose |

The health endpoint exposes only overall status. The Eureka client reports that health state to the registry, while detailed application health data remains hidden.

## Architecture boundary

Service discovery is the foundation for independently deployable services. The current backend remains one deployable application, so adding Eureka alone does not complete the domain split.

The next architectural step is to extract bounded applications such as identity, accounts, payments, and products. Each extracted application should:

- own its persistence boundary instead of sharing another service's tables directly;
- register a unique `spring.application.name` with Eureka;
- communicate through versioned APIs or events;
- keep authentication, authorization, timeouts, retries, and audit correlation across service calls;
- ship and scale independently.

An API gateway can then resolve registered service names and provide one external entry point. Circuit breakers, centralized tracing, and secured service-to-service traffic should be added as calls begin crossing process boundaries.

The included registry configuration is intended for local development. Keep its dashboard and API on a private network. A deployed environment should add TLS, access control, monitoring, and multiple registry peers before handling production traffic.

## References

- [Spring Cloud Netflix reference](https://docs.spring.io/spring-cloud-netflix/reference/spring-cloud-netflix.html)
- [Spring Cloud 2023.0.4 release announcement](https://spring.io/blog/2024/11/27/spring-cloud-2023-0-4/)
