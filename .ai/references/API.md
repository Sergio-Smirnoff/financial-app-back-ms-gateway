# ms-gateway — API

Route mappings, BFF endpoints, and gateway error normalization. Envelope shape: parent `.ai/references/APP_STRUCTURE.md`.

## Route Definitions

| Path prefix | Upstream target | JWT auth | Notes |
|---|---|---|---|
| `/api/v1/auth/**` | ms-users (:8081) | Exempt | Login, register, refresh, logout |
| `/api/v1/users/**` | ms-users (:8081) | Required | User profile & session management |
| `/api/v1/finances/**` | ms-finances (:8082) | Required | Ledger transactions & categories |
| `/api/v1/banks/**` | ms-banks (:8083) | Required | Accounts, cards, loans, fee schedules |
| `/api/v1/notifications/stream` | ms-notifications (:8084) | Required | SSE event stream (`response-timeout: -1`) |
| `/api/v1/notifications/**` | ms-notifications (:8084) | Required | Notification management & preferences |
| `/api/v1/upload/**` | ms-upload (:8085) | Required | Statement import runs & files |
| `/api/v1/investments/**` | ms-investments (:8086) | Required | Holdings, quotes & fee schedules |
| `/v3/api-docs/{service}` | Upstream services | Exempt | OpenAPI spec rewriting |
| `/swagger-ui.html` | Gateway local | Exempt | Aggregated Swagger UI |
| `/actuator/**` | Gateway local | Exempt | Health, metrics, Prometheus |

## BFF Endpoints

| Method | Path | Purpose | Downstream calls |
|---|---|---|---|
| GET | `/api/v1/dashboard/data` | Aggregated dashboard view (finances + banks + FX) with partial degradation | ms-finances, ms-banks, ms-investments |
| GET | `/api/v1/bff/currencies` | Available currencies list & default selector options | ms-banks, ms-investments, ms-users |
| GET | `/api/v1/bff/transactions` | Paginated transactions with summary, filter options, and uncategorised count (`?page=&size=&categories=&accounts=&method=&q=&from=&to=&currency=&secondary=`). Page metadata (`totalPages = ceil(totalElements / size)`) is derived because ms-finances returns cursor paging. `filterOptions` calls ms-banks (`fetchAccounts`) for the account list. | ms-finances, ms-investments, ms-banks |

## BFF composition notes

- `FinancesGateway.fetchCategories` — port added on this branch (`GET /api/v1/finances/categories`),
  consumed by `GetCategoriesBffUseCaseImpl` to build the budget-merge below.
- **Budget merge (categories BFF):** `GetCategoriesBffUseCaseImpl` walks every category returned by
  `fetchCategories` — including ones with no `fetchBudgets` row — and every one of its
  subcategories, emitting a `BudgetRow` per category **and** per subcategory. A subcategory's row
  carries a synthetic `"<parent name> / <child name>"` label (`BudgetRow` has a single `name`
  field) and a nullable `parentId` — the parent category's id on subcategory rows, `null` on
  root-category rows and on orphan rows. The frontend offers "add subcategory" only on rows whose
  `parentId` is `null`, since ms-finances rejects a subcategory as a parent. Any budget left
  unmatched after the walk (an orphaned `categoryId`) still gets a row, named from the budget's own
  `categoryName`, with `parentId` `null`.
- **Card figures:** `CardFigures` (`application/bff/impl/CardFigures.java`) is the shared reader for
  a card's `usedAmount`/`usedPercent` off the raw ms-banks map — `usedPercent` falls back to
  computing `usedAmount / creditLimit` when ms-banks reports `0`.
- **Search-hit contract:** `GetSearchBffUseCaseImpl` links every movements search hit to
  `href = "/transactions?id=" + id` — the frontend's `/transactions` page must open the detail
  panel for that `id` query param (see front `API_CLIENT.md`).
- **Percent-encoding:** `FinancesGatewayImpl.fetchTransactions` builds its dynamic filter query
  with `UriComponentsBuilder` and must call `.build().encode().toUri()` — **never**
  `.encode().build()`, which throws on a literal `{`/`}` in a filter value (e.g. a category name).
  Every other `FinancesGatewayImpl` call (`fetchSummary`, `fetchBudgets`, `fetchCategories`,
  `fetchTransactionById`, `searchTransactions`, `fetchMonthlyFlow`, …) passes its values as URI
  template variables to WebClient's `.uri(template, vars...)` overload, which Spring's default
  `UriBuilderFactory` percent-encodes on its own — braces, `&`, `=`, `%` and non-ASCII characters
  in a filter value are percent-encoded either way, so a literal `&` or `%` in a search term
  cannot inject an extra query parameter, but the two call shapes reach that guarantee through
  different mechanisms.

## DomainError catalog

| Slug | HTTP status | When it is thrown |
|---|---|---|
| `unauthorized` | 401 | Missing or invalid `access_token` cookie |
| `invalid_token_type` | 401 | `access_token` cookie carries `type == "refresh"` |
| `rate_limit_exceeded` | 429 | IP token bucket empty (> RATE_LIMIT_RPM) |
| `service_unavailable` | 503 | Upstream service connection refused or timed out |
| `internal_error` | 500 | Unmapped gateway exception |
