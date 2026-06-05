package com.financialapp.gateway.web.error;

import com.financialapp.gateway.domain.exception.DomainErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.ConnectException;

@Slf4j
@Component
@Order(-1)
@RequiredArgsConstructor
public class GatewayErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    private final ErrorResponseRenderer renderer;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status;
        String message;
        DomainErrorCode code;

        if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.resolve(rse.getStatusCode().value());
            if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;

            message = switch (status) {
                case NOT_FOUND -> "Route not found";
                case SERVICE_UNAVAILABLE, BAD_GATEWAY, GATEWAY_TIMEOUT ->
                        "Service temporarily unavailable";
                default -> rse.getReason() != null ? rse.getReason() : "Gateway error";
            };
            code = switch (status) {
                case SERVICE_UNAVAILABLE, BAD_GATEWAY, GATEWAY_TIMEOUT -> DomainErrorCode.UPSTREAM_UNAVAILABLE;
                case UNAUTHORIZED -> DomainErrorCode.UNAUTHORIZED;
                case TOO_MANY_REQUESTS -> DomainErrorCode.RATE_LIMITED;
                default -> DomainErrorCode.INTERNAL_ERROR;
            };
        } else if (ex instanceof ConnectException) {
            status = HttpStatus.SERVICE_UNAVAILABLE;
            message = "Service temporarily unavailable";
            code = DomainErrorCode.UPSTREAM_UNAVAILABLE;
            log.error("Downstream connection error: {}", ex.getMessage());
        } else {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "Internal server error";
            code = DomainErrorCode.INTERNAL_ERROR;
            log.error("Unhandled gateway error", ex);
        }

        return renderer.render(exchange, status, code, message);
    }
}
