package com.financialapp.gateway.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financialapp.gateway.model.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ObjectMapper objectMapper;

    @ExceptionHandler(WebClientResponseException.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleWebClientError(WebClientResponseException ex) {
        log.error("Upstream service error: {} - body: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
        
        String errorMessage = "Upstream service error";
        try {
            JsonNode body = objectMapper.readTree(ex.getResponseBodyAsString());
            if (body.has("message")) {
                errorMessage = body.get("message").asText();
            }
        } catch (Exception e) {
            log.warn("Failed to parse error response body", e);
        }

        return Mono.just(ResponseEntity
                .status(ex.getStatusCode())
                .body(ApiResponse.error(errorMessage)));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleGenericError(Exception ex) {
        log.error("Unexpected error in gateway controller", ex);
        return Mono.just(ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("Internal server error: " + ex.getMessage())));
    }
}
