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
| GET | `/api/v1/bff/investments` | Investments page sections: market strip, KPIs, evolution, positions (with `assetType`), composition per asset type (cost, P&L, count), recent operations, alerts (`?currency=&secondary=&range=`). `range` = `1M`\|`3M`\|`1A` → evolution over the last 30/90/365 days (`GET /portfolio/evolution?days=`); missing or unknown → `1M`. | ms-investments, ms-notifications |

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

## BFF read contract

- **Strict reads.** Every BFF use case reads downstream payloads through `DownstreamPayload` (`application/bff/impl`), which names each audited key and its type. A missing or malformed required key throws `DownstreamContractViolationException` and logs `Downstream contract violation: <source> field '<key>' <problem>`; `Section.guard` then degrades only the section that read it to `UNAVAILABLE`. There are no silent defaults for required keys. Contract fixtures live in `src/test/resources/contracts/<service>/` and mirror the downstream DTOs the tests name.
- **Investments aggregation (`/bff/investments`).** `PortfolioFigures.summary` sums every currency bucket of `GET /portfolio/summary` into `CurrencyAmounts`. Each bucket is converted to ARS at the view rate (`buy` for USD), then to the target view; P&L is `market - cost` in ARS and P&L % is recomputed with `Percentages.percentOf` (never taken from a bucket). In the ARS view the MEP rate is fetched only when a non-ARS bucket has a non-zero amount (`PortfolioFigures.usdRate`); in a USD view the view rate is reused. A bucket that cannot be converted (no rate, or a currency other than ARS/USD) makes that section `UNAVAILABLE` instead of showing a partial sum. The evolution series has no cost: `EvolutionPoint.cost` is `null`. Composition slices (`AssetTypeSliceResponse`, investments only — Bancos keeps `CompositionSliceResponse`) are per asset type, merged across currency buckets: `amount`, `cost` and `pnl = amount − cost` converted to ARS at the bucket's rate and then to the view, `pnlPct = percentOf(pnl, cost)`, `pct = percentOf(amount, total)`, `count` = holdings of that type. Each breakdown entry is read strictly (`totalValue`, `totalCost`, `count`): an ms-investments that does not send them makes `kpis`, `composition` and the overview's net worth UNAVAILABLE, so ms-investments deploys first. Positions carry the holding's `assetType`.
- **USD view spread.** USD view figures convert USD to ARS at `buy` and then ARS to USD at `sell`, so a USD-only portfolio shows a small drift equal to the spread (consequence of D13).
- **Rate views.** `CurrencyView` `USD_MEP` / `USD_CCL` / `USD_OFICIAL` is sent to ms-investments as `view=MEP|CCL|OFICIAL` on `GET /fx/rates`. `InvestmentsGateway.fetchFxRate(view, date)` returns the latest rate on or before `date` within `FX_RATE_LOOKBACK_DAYS` (7); none in the window means no rate.
- **Alerts.** The investments alerts section uses `NotificationsGateway.fetchLatestOfType(userId, "INVESTMENT_THRESHOLD")`, which filters the latest notifications client-side by `type`.
- **Categories.** Transaction filter options and search categories come from `CategoryTree.options` over `fetchCategories`: one option per category and one per subcategory, labelled `"<parent> / <child>"`.
- **Transaction detail (`GET /bff/transactions/{id}`).** A non-numeric `id` is a 400 `invalid_request`; an unknown transaction is a 404 `resource_not_found` (`ResourceNotFoundException` from `fetchTransactionById`). Any other failure of the transaction fetch degrades the detail section rather than failing the request. Rows are built by `TransactionRows`, labelling accounts through `AccountLabels` (alias, then name, then CBU).
- **`/bff/currencies` sources.** Account currencies from ms-banks (`accountCurrencies`), holding currencies from ms-investments (`holdingCurrencies`), and the user's primary currency from ms-users display preferences; each is guarded independently and merged by `AvailableCurrencies`.
- **Fields sent as `null` for lack of a source:** `AccountRow.bankName`, `TransactionOrigin.fileName`, `FeeRow.scope`, `EvolutionPoint.cost`.
- **Rules.** `RuleRow.matchCount` is a strict read of ms-finances `CategorizationRuleResponse.matchCount` (int). Missing → rules section `UNAVAILABLE`.
- **Sessions.** `/bff/settings` reads the `access_token` cookie (`@CookieValue`, optional) and `UsersGateway.fetchSessions(userId, Optional<AccessToken>)` forwards it to ms-users `GET /me/sessions`, which marks the session whose `sid` matches as `current`. This is the only BFF call that forwards a cookie. `current` is a strict read. Without a cookie (`jwt.enabled=false`) no session is current. The OpenAPI documents the optional cookie parameter.
- **Downstream status mapping.** `StatusErrorCodes.codeFor` maps 400 to `invalid_request`, 404 to `resource_not_found`, 401 to `unauthorized`, 429 to `rate_limit_exceeded`, 502/503/504 to `upstream_unavailable`; every other status (including 403, 409, 422) maps to `internal_error`.

## DomainError catalog

| Slug | HTTP status | When it is thrown |
|---|---|---|
| `unauthorized` | 401 | Missing or invalid `access_token` cookie |
| `invalid_token_type` | 401 | `access_token` cookie carries `type == "refresh"` |
| `rate_limit_exceeded` | 429 | IP token bucket empty (> RATE_LIMIT_RPM) |
| `service_unavailable` | 503 | Upstream service connection refused or timed out |
| `invalid_request` | 400 | Malformed request or a downstream 400 (e.g. non-numeric transaction id) |
| `resource_not_found` | 404 | Downstream 404, e.g. unknown transaction id on the detail BFF |
| `upstream_unavailable` | 500 | Downstream 502/503/504 mapped by `StatusErrorCodes` |
| `upstream_contract_violation` | 500 | A downstream payload broke the audited contract (`DownstreamPayload`) |
| `unconvertible_amount` | 500 | An amount has no rate to convert into ARS |
| `internal_error` | 500 | Unmapped gateway exception |
