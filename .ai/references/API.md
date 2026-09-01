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

## DomainError catalog

| Slug | HTTP status | When it is thrown |
|---|---|---|
| `unauthorized` | 401 | Missing or invalid `access_token` cookie |
| `invalid_token_type` | 401 | `access_token` cookie carries `type == "refresh"` |
| `rate_limit_exceeded` | 429 | IP token bucket empty (> RATE_LIMIT_RPM) |
| `service_unavailable` | 503 | Upstream service connection refused or timed out |
| `internal_error` | 500 | Unmapped gateway exception |
