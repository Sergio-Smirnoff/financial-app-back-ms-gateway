# ms-gateway

API Gateway — single entry point for all `/api/v1/**` traffic. Runs on **port 8080**.

Every request from the browser or external client passes through this service before reaching any downstream microservice. It enforces authentication, rate-limits requests, applies CORS policy, normalises error shapes, and provides a BFF aggregation endpoint so the frontend dashboard loads in a single round-trip.

## Tech Stack

- Java 21
- Spring Cloud Gateway (WebFlux / Reactor)
- Spring Boot 3.4.2
- Spring Cloud 2024.0.1

## File Distribution

```
src/main/java/com/financialapp/gateway/
├── GatewayApplication.java
│
├── domain/
│   ├── common/model/
│   │   ├── AccessToken.java          # VO wrapping raw JWT string
│   │   ├── Principal.java            # authenticated identity (UserId)
│   │   ├── TimeoutPolicy.java        # VO — perCall Duration
│   │   └── UserId.java               # typed user identifier
│   ├── exception/
│   │   ├── DomainErrorCode.java
│   │   └── InvalidAccessTokenException.java
│   ├── gateway/
│   │   ├── BanksGateway.java         # port — loans + upcoming payments
│   │   ├── FinancesGateway.java      # port — transaction summaries
│   │   └── TokenVerificationGateway.java
│   ├── model/
│   │   ├── admission/
│   │   │   ├── RateLimitPolicy.java
│   │   │   └── TokenBucket.java
│   │   ├── composition/
│   │   │   ├── Section.java          # partial-degradation wrapper
│   │   │   └── SectionStatus.java    # OK | UNAVAILABLE
│   │   └── dashboard/
│   │       ├── CurrencySummary.java
│   │       ├── DashboardData.java
│   │       ├── LoanView.java
│   │       └── UpcomingPaymentView.java
│   └── usecase/dashboard/
│       └── GetDashboardData.java
│
├── application/
│   └── dashboard/impl/
│       └── GetDashboardDataImpl.java # concurrent fan-out, Section.guard
│
├── infrastructure/
│   ├── config/
│   │   ├── CorsConfig.java
│   │   ├── JwtProperties.java
│   │   ├── ResilienceConfig.java
│   │   ├── ServicesProperties.java
│   │   └── TimeoutProperties.java
│   └── gateway/
│       ├── client/
│       │   └── WebClientConfig.java
│       ├── dto/
│       │   ├── FinanceCurrencyTotals.java
│       │   ├── GatewayApiResponse.java
│       │   ├── LoanResponse.java
│       │   └── UpcomingPaymentResponse.java
│       └── Impl/
│           ├── BanksGatewayImpl.java
│           ├── FinancesGatewayImpl.java
│           └── JwtTokenVerificationGateway.java
│
└── web/
    ├── controller/
    │   └── DashboardController.java  # GET /api/v1/dashboard/data
    ├── dto/response/
    │   ├── (envelope from commons-core)
    │   └── DashboardResponse.java
    ├── error/
    │   ├── ErrorResponseRenderer.java
    │   ├── GatewayErrorWebExceptionHandler.java
    │   └── GlobalExceptionHandler.java
    ├── filter/
    │   ├── JwtAuthFilter.java        # order -2
    │   ├── RateLimitFilter.java      # order -1
    │   └── LoggingFilter.java        # order  0
    └── mapper/
        └── DashboardMapper.java
```

## Response envelope

Gateway-rendered responses (auth 401 `unauthorized`, rate-limit 429 `rate_limit_exceeded`,
upstream failures `upstream_unavailable`) and the BFF endpoints use the shared envelope
`{ status, title, code, message, data }` from `commons-core` (built from `financial-app-parent`).
Downstream service error bodies pass through with their own `code` preserved.

## Endpoints / Routes

| Method | Path | Handled by | Purpose |
|--------|------|-----------|---------|
| `GET` | `/api/v1/dashboard/data` | `DashboardController` (local) | BFF — aggregated dashboard (finances + banks) |
| `*` | `/api/v1/auth/**` | proxy → ms-users :8081 | Login, register, token refresh, logout — **JWT-exempt** |
| `*` | `/api/v1/users/**` | proxy → ms-users :8081 | User profile management |
| `*` | `/api/v1/finances/**` | proxy → ms-finances :8082 | Transactions, categories, loans, card expenses |
| `*` | `/api/v1/banks/**` | proxy → ms-banks :8083 | Bank accounts, loans, upcoming payments |
| `GET` | `/api/v1/notifications/stream` | proxy → ms-notifications :8084 | SSE stream — `response-timeout: -1` |
| `*` | `/api/v1/notifications/**` | proxy → ms-notifications :8084 | Notification read/management |
| `*` | `/api/v1/upload/**` | proxy → ms-upload :8085 | File upload (skeleton) |
| `*` | `/api/v1/investments/**` | proxy → ms-investments :8086 | Holdings, prices, portfolio |
| `GET` | `/v3/api-docs/{service}` | proxy → each service | Per-service OpenAPI JSON (rewrite filter) |
| `GET` | `/swagger-ui.html` | local (SpringDoc) | Aggregated Swagger UI — **JWT-exempt** |
| `GET` | `/actuator/**` | local (Spring Actuator) | Health, info, Prometheus metrics — **JWT-exempt** |

All proxied routes receive an `X-Internal-Token` header (set via `AddRequestHeader` default filter) plus `X-User-Id` injected by `JwtAuthFilter`.

## Filter Execution Order

```
CorsWebFilter (HIGHEST_PRECEDENCE)
  └─ JwtAuthFilter          @Order(-2)   reads access_token cookie → injects X-User-Id
       └─ RateLimitFilter   @Order(-1)   per-IP token bucket
            └─ LoggingFilter @Order(0)   timer + structured log
                 └─ Spring Cloud Gateway routing
```

## Environment Variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `JWT_ENABLED` | `true` | Disable JWT validation in test environments |
| `JWT_SECRET` | *(dev placeholder)* | HMAC-SHA signing key (Base64) |
| `RATE_LIMIT_RPM` | `600` | Token bucket capacity per IP per minute |
| `GATEWAY_TIMEOUT_PER_CALL_MS` | `3000` | Per-call timeout for BFF WebClient calls (ms) |
| `ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:8080` | CORS allowed origins |
| `INTERNAL_AUTH_TOKEN` | — | Added as `X-Internal-Token` on every proxied request |
| `USERS_SERVICE_URL` | `http://localhost:8081` | ms-users upstream |
| `FINANCES_SERVICE_URL` | `http://localhost:8082` | ms-finances upstream |
| `BANKS_SERVICE_URL` | `http://localhost:8083` | ms-banks upstream |
| `NOTIFICATIONS_SERVICE_URL` | `http://localhost:8084` | ms-notifications upstream |
| `UPLOAD_SERVICE_URL` | `http://localhost:8085` | ms-upload upstream |
| `INVESTMENTS_SERVICE_URL` | `http://localhost:8086` | ms-investments upstream |

See `.env.example` for the canonical list.

## Run

**From the parent workspace (recommended):**
```bash
./scripts/dev.sh local service-gateway
```

**From this directory (Maven directly):**
```bash
# Install parent BOM first (once)
cd ../financial-app-parent && mvn install -N

# Run
cd ../ms-gateway
cp .env.example .env   # fill in secrets
mvn spring-boot:run
```

**Docker (full stack):**
```bash
# From workspace root
./scripts/dev.sh up
```

Service listens on **http://localhost:8080**.  
Swagger UI: **http://localhost:8080/swagger-ui.html**

## Build

```bash
mvn clean package -DskipTests
```

> Full design: `docs/specs/services/ms-gateway.md` (in the parent workspace).
