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

## BFF conversion & investment figures

`BffMoneyConverter` (`domain/service`) is the converter the BFF use cases actually call; `MoneyConversion` above is not referenced by any BFF code (see parent `docs/specs/IDEAS.md`).

| Type | Role |
|---|---|
| `CurrencyAmounts` | Immutable `Currency -> BigDecimal` bucket map: `plus(currency, amount)`, `plus(other)`, `needsUsdRate()` (any non-ARS bucket with a non-zero amount) |
| `PortfolioSummary` | Investments summary read: `marketValue` and `cost` as `CurrencyAmounts`, plus `marketValueByAssetType` (asset type -> `CurrencyAmounts`) |
| `PortfolioValuePoint` | One evolution point: `date` + `marketValue` as `CurrencyAmounts` (no cost) |
| `Percentages.percentOf(part, whole)` | Single percentage formula (2 dp HALF_EVEN); `0` when `whole <= 0` or an operand is null |
| `BffMoneyConverter.toArs(CurrencyAmounts, Optional<FxRate>)` | Sums every non-zero bucket in ARS (USD at the rate's `buy`); a currency other than ARS/USD, or a USD bucket without a usable rate, throws `UnconvertibleAmountException` |

`BffMoneyConverter.convert` keeps the single-amount rules: ARS view converts foreign amounts at `buy`; a USD view converts ARS at `sell` and leaves USD untouched.

### Error codes and exceptions

`DomainErrorCode` values: `unauthorized`, `rate_limit_exceeded`, `invalid_request` (400), `resource_not_found` (404), `upstream_unavailable` (500), `upstream_contract_violation` (500), `unconvertible_amount` (500), `internal_error` (500).

| Exception | Code | Thrown when |
|---|---|---|
| `DownstreamContractViolationException(source, field, problem)` | `upstream_contract_violation` | `DownstreamPayload` finds a required key missing or malformed in a downstream payload |
| `ResourceNotFoundException(resource, identifier)` | `resource_not_found` | `FinancesGatewayImpl.fetchTransactionById` gets a downstream 404 |
| `UnconvertibleAmountException(currency)` | `unconvertible_amount` | `BffMoneyConverter.toArs` has no rate for a non-zero bucket |
| `InvalidAccessTokenException` | `unauthorized` / `invalid_token_type` | Bad or refresh-type `access_token` |

`invalid_request` has no dedicated exception: `StatusErrorCodes.codeFor` maps a 400 (e.g. a `ResponseStatusException`) to it.

## Resilience & Timeout Policies

| Policy / Model | Config / Default | Purpose |
|---|---|---|
| `TimeoutPolicy` | `GATEWAY_TIMEOUT_PER_CALL_MS` (3 000 ms) | Per-call timeout for WebClient calls to downstream services |
| `PageTimeoutBudget` | `GATEWAY_TIMEOUT_PAGE_BUDGET_MS` (5 000 ms) | Outer timeout budget for page-level BFF aggregation calls |
| `Section<T>` | `OK` or `UNAVAILABLE` + `ObservedAt` | Wraps BFF data sections; supports partial degradation with freshness timestamp |
| `TtlCache` | `CACHE_FX_TTL_SECONDS` (30 s) | 30s single-flight TTL cache on shared FX rate reads |

## Transaction Filtering & Paging Translation

`TransactionQuery` forwards filter parameters to `FinancesGateway`:
- `categories=none` translates to `onlyUncategorised=true` in `FinancesGatewayImpl`.
- Multiple categories/accounts, `method`, `q`, and 0-based `page` are forwarded as query params.
- `BffDomainModels.TransactionsPage` (`rows`, `page`, `size`, `totalElements`, `totalPages`) is
  derived (`totalPages = ceil(totalElements / size)`) because ms-finances returns cursor paging.
