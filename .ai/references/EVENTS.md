# ms-gateway — messaging and jobs

## Published
None. ms-gateway is a stateless HTTP edge proxy and BFF aggregator. It produces no Kafka events.

## Consumed
None. ms-gateway consumes no Kafka events.

## SSE Passthrough
- `GET /api/v1/notifications/stream` is proxied to ms-notifications with `response-timeout: -1` to allow long-lived Server-Sent Events connections.

## Scheduled jobs
- Idle IP rate-limiter bucket eviction runs every 60s in memory.
