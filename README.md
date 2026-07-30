# ms-gateway

API Gateway — single entry point for all `/api/v1/**` traffic. Runs on **port 8080**.

Every request from the browser or external client passes through this service before reaching any downstream microservice. It enforces authentication, rate-limits requests, applies CORS policy, normalises error shapes, and provides BFF aggregation endpoints (`/api/v1/dashboard/data`, `/api/v1/bff/currencies`).

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
│   │   ├── BanksGateway.java         # port — loans, payments, account currencies
│   │   ├── FinancesGateway.java      # port — transaction summaries
│   │   ├── InvestmentsGateway.java   # port — fx rates, holding currencies
│   │   ├── UsersGateway.java         # port — user preferences, manual currency rates
│   │   └── TokenVerificationGateway.java
│   ├── model/
│   │   ├── admission/
│   │   │   ├── RateLimitPolicy.java
│   │   │   └── TokenBucket.java
│   │   ├── composition/
│   │   │   ├── ObservedAt.java       # freshness timestamp VO
│   │   │   ├── PageTimeoutBudget.java# per-page outer timeout budget VO
│   │   │   ├── Section.java          # partial-degradation wrapper with ObservedAt
│   │   │   └── SectionStatus.java    # OK | UNAVAILABLE
│   │   ├── currency/
│   │   │   ├── Currency.java
│   │   │   ├── DisplayMoney.java
│   │   │   ├── FxRate.java
│   │   │   ├── FxRateMode.java
│   │   │   ├── ManualCurrencyRate.java
│   │   │   ├── Money.java
│   │   │   └── UserDisplayPreferences.java
│   │   └── dashboard/
│   │       ├── CurrencySummary.java
│   │       ├── DashboardData.java
│   │       ├── LoanView.java
│   │       └── UpcomingPaymentView.java
│   ├── service/
│   │   ├── AvailableCurrencies.java  # currency selector resolution service
│   │   └── MoneyConversion.java      # 3-case currency conversion service
│   └── usecase/
│       ├── currency/
│       │   └── GetAvailableCurrencies.java
│       └── dashboard/
│           └── GetDashboardData.java
│
├── application/
│   ├── currency/impl/
│   │   └── GetAvailableCurrenciesUseCaseImpl.java
│   └── dashboard/impl/
│       └── GetDashboardDataImpl.java # concurrent fan-out, Section.guard, PageTimeoutBudget
│
├── infrastructure/
│   ├── cache/
│   │   └── TtlCache.java             # 30s single-flight TTL cache
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
│       │   ├── AccountResponse.java
│       │   ├── FinanceCurrencyTotals.java
│       │   ├── FxRateResponse.java
│       │   ├── GatewayApiResponse.java
│       │   ├── HoldingResponse.java
│       │   ├── LoanResponse.java
│       │   ├── ManualCurrencyRateResponse.java
│       │   ├── UpcomingPaymentResponse.java
│       │   └── UserPreferencesResponse.java
│       └── Impl/
│           ├── BanksGatewayImpl.java
│           ├── FinancesGatewayImpl.java
│           ├── InvestmentsGatewayImpl.java
│           ├── JwtTokenVerificationGateway.java
│           └── UsersGatewayImpl.java
│
└── web/
    ├── controller/
    │   ├── CurrenciesController.java # GET /api/v1/bff/currencies
    │   └── DashboardController.java  # GET /api/v1/dashboard/data
    ├── dto/response/
    │   ├── (envelope from commons-core)
    │   ├── AvailableCurrenciesResponse.java
    │   └── DashboardResponse.java
    ├── error/
    │   ├── ErrorResponseRenderer.java
    │   ├── GatewayErrorWebExceptionHandler.java
    │   └── GlobalExceptionHandler.java
    ├── filter/
    │   ├── JwtAuthFilter.java        # order -2 (enforces access token type check)
    │   ├── RateLimitFilter.java      # order -1 (idle bucket eviction)
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
| `GET` | `/api/v1/dashboard/data` | `DashboardController` (local) | BFF — aggregated dashboard (finances + banks) with section `observedAt` |
| `GET` | `/api/v1/bff/currencies` | `CurrenciesController` (local) | BFF — available currencies & default currency selector options |
| `*` | `/api/v1/auth/**` | proxy → ms-users :8081 | Login, register, token refresh, logout — **JWT-exempt** |
| `*` | `/api/v1/users/**` | proxy → ms-users :8081 | User profile management |
| `*` | `/api/v1/finances/**` | proxy → ms-finances :8082 | Transactions, categories, loans, card expenses |
| `*` | `/api/v1/banks/**` | proxy → ms-banks :8083 | Bank accounts, loans, upcoming payments |
| `GET` | `/api/v1/notifications/stream` | proxy → ms-notifications :8084 | SSE stream — `response-timeout: -1` |
| `*` | `/api/v1/notifications/**` | proxy → ms-notifications :8084 | Notification read/management |
| `*` | `/api/v1/upload/**` | proxy → ms-upload :8085 | File upload |
| `*` | `/api/v1/investments/**` | proxy → ms-investments :8086 | Holdings, prices, portfolio |
| `GET` | `/v3/api-docs/{service}` | proxy → each service | Per-service OpenAPI JSON (rewrite filter) |
| `GET` | `/swagger-ui.html` | local (SpringDoc) | Aggregated Swagger UI — **JWT-exempt** |
| `GET` | `/actuator/**` | local (Spring Actuator) | Health, info, Prometheus metrics — **JWT-exempt** |

All proxied routes receive an `X-Internal-Token` header (set via `AddRequestHeader` default filter) plus `X-User-Id` injected by `JwtAuthFilter`.

## Filter Execution Order

```
CorsWebFilter (HIGHEST_PRECEDENCE)
  └─ JwtAuthFilter          @Order(-2)   reads access_token cookie → verifies type != "refresh" → injects X-User-Id
       └─ RateLimitFilter   @Order(-1)   per-IP token bucket (evicts idle buckets)
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
| `GATEWAY_TIMEOUT_PAGE_BUDGET_MS` | `5000` | Per-page outer timeout budget for page BFFs |
| `CACHE_FX_TTL_SECONDS` | `30` | Shared TTL cache duration for FX rate reads |
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

## CI/CD

| Workflow | Trigger | Does |
|---|---|---|
| `ci.yml` | PRs; push to develop/master | tests + docker build via shared `backend-ci.yml` |
| `docker-publish.yml` | push to master; `v*` tags | GHCR publish: `latest`, `sha-*`, semver on tags |
| `release.yml` | manual (bump dropdown) | next `vX.Y.Z` tag + Release + versioned publish |

Reusable workflows live in the root repo `Sergio-Smirnoff/financial-app`.
