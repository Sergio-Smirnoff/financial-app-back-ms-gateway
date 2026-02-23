# financial-app-gateway

API Gateway — single entry point for the frontend. Handles JWT validation, routing, and response aggregation.

## Responsibilities
- JWT validation on every incoming request
- Routing to the appropriate microservice by path
- Aggregating responses from multiple microservices
- CORS configuration
- Centralized Swagger UI for all services

## Port: 8080

## Routes
| Path | Service |
|---|---|
| `/api/v1/auth/**` | Users Service |
| `/api/v1/users/**` | Users Service |
| `/api/v1/finances/**` | Finances Service |
| `/api/v1/cards/**` | Cards Service |
| `/api/v1/notifications/**` | Notifications Service |
| `/api/v1/upload/**` | Upload Service |

## Environment Variables
See `.env.example`.

## Local Development

```bash
# Install parent POM first (only once)
cd ../financial-app-parent && mvn install -N

# Run
cd ../financial-app-gateway
cp .env.example .env
mvn spring-boot:run
```

## Build
```bash
mvn clean package -DskipTests
```
