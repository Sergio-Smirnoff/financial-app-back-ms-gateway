package com.financialapp.gateway.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financialapp.gateway.model.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    private final ObjectMapper objectMapper;

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        HttpStatus status;
        String message;

        if (ex instanceof ResponseStatusException rse) {
            status = HttpStatus.resolve(rse.getStatusCode().value());
            if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;

            message = switch (status) {
                case NOT_FOUND         -> "Route not found";
                case SERVICE_UNAVAILABLE, BAD_GATEWAY, GATEWAY_TIMEOUT ->
                        "Service temporarily unavailable";
                default                -> rse.getReason() != null ? rse.getReason() : "Gateway error";
            };
        } else if (ex instanceof ConnectException) {
            status  = HttpStatus.SERVICE_UNAVAILABLE;
            message = "Service temporarily unavailable";
            log.error("Downstream connection error: {}", ex.getMessage());
        } else {
            status  = HttpStatus.INTERNAL_SERVER_ERROR;
            message = "Internal server error";
            log.error("Unhandled gateway error", ex);
        }

        return writeError(exchange, status, message);
    }

    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, String message) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(ApiResponse.error(message));
            var response = exchange.getResponse();
            response.setStatusCode(status);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

            // Preserve CORS headers so browsers can read the error response
            String origin = exchange.getRequest().getHeaders().getOrigin();
            if (origin != null) {
                response.getHeaders().setAccessControlAllowOrigin(origin);
                response.getHeaders().setAccessControlAllowCredentials(true);
            }

            var buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("Failed to write error response", e);
            exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return exchange.getResponse().setComplete();
        }
    }
}
