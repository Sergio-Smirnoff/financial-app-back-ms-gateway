# ms-gateway — domain

Stateless API gateway and BFF aggregator. Domain logic is limited to route definitions,
filter chain ordering, resiliency policies, and currency conversion models. Endpoints: `API.md`.

## Filter Execution Order

WebFlux filter chain order before downstream proxy routing:

1. `CorsWebFilter` (`HIGHEST_PRECEDENCE`) — CORS handling for allowed origins.
2. `JwtAuthFilter` (`@Order(-2)`) — Validates `access_token` cookie (`type != "refresh"`), injects `X-User-Id`.
3. `RateLimitFilter` (`@Order(-1)`) — Per-IP token bucket (600 req/min default), sweeps idle buckets every 60s.
4. `LoggingFilter` (`@Order(0)`) — Timer, request/response logging.
5. Spring Cloud Gateway routing.

## Currency Domain & 3-Case Conversion Model

`MoneyConversion` domain service converts currency totals for BFF displays across three cases:

1. **Passthrough**: `money.currency == target` → Returns `DisplayMoney(amount, target)`.
2. **Automatic ARS ↔ USD**: Direct conversion using `arsUsdRate` (ARS→USD: `amount / sellRate`; USD→ARS: `amount * buyRate`, scale 2 HALF_EVEN).
3. **Other pairs**: Converts via ARS using `manualRate.ratePerArs` (e.g. EUR→ARS or composed EUR→USD).

*Unconvertible convention*: If rate is missing, returns `DisplayMoney(originalAmount, originalCurrency)`. Caller detects currency mismatch and renders native subtotal without failing.

## Resilience & Timeout Policies

| Policy / Model | Config / Default | Purpose |
|---|---|---|
| `TimeoutPolicy` | `GATEWAY_TIMEOUT_PER_CALL_MS` (3 000 ms) | Per-call timeout for WebClient calls to downstream services |
| `PageTimeoutBudget` | `GATEWAY_TIMEOUT_PAGE_BUDGET_MS` (5 000 ms) | Outer timeout budget for page-level BFF aggregation calls |
| `Section<T>` | `OK` or `UNAVAILABLE` + `ObservedAt` | Wraps BFF data sections; supports partial degradation with freshness timestamp |
| `TtlCache` | `CACHE_FX_TTL_SECONDS` (30 s) | 30s single-flight TTL cache on shared FX rate reads |
