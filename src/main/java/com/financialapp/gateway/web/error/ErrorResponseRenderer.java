package com.financialapp.gateway.web.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.financialapp.gateway.web.dto.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class ErrorResponseRenderer {

    private final ObjectMapper objectMapper;

    public ErrorResponseRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Mono<Void> render(ServerWebExchange exchange, HttpStatus status, String message) {
        var response = exchange.getResponse();
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(ApiResponse.error(message));
            response.setStatusCode(status);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            // Preserve CORS headers so browsers can read the error response.
            String origin = exchange.getRequest().getHeaders().getOrigin();
            if (origin != null) {
                response.getHeaders().setAccessControlAllowOrigin(origin);
                response.getHeaders().setAccessControlAllowCredentials(true);
            }
            return response.writeWith(Mono.just(response.bufferFactory().wrap(bytes)));
        } catch (Exception e) {
            response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
            return response.setComplete();
        }
    }
}
