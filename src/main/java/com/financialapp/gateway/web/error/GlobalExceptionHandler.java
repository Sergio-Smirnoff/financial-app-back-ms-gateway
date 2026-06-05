package com.financialapp.gateway.web.error;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.financialapp.commons.core.response.ApiResponse;
import com.financialapp.gateway.domain.exception.DomainErrorCode;
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
        String code = DomainErrorCode.UPSTREAM_UNAVAILABLE.code();
        try {
            JsonNode body = objectMapper.readTree(ex.getResponseBodyAsString());
            if (body.has("message")) {
                errorMessage = body.get("message").asText();
            }
            if (body.has("code")) {
                code = body.get("code").asText();
            }
        } catch (Exception e) {
            log.warn("Failed to parse error response body", e);
        }

        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return Mono.just(ResponseEntity.status(status)
                .body(ApiResponse.failure(status, code, errorMessage, null)));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<ApiResponse<Void>>> handleGenericError(Exception ex) {
        log.error("Unexpected error in gateway controller", ex);
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.failure(HttpStatus.INTERNAL_SERVER_ERROR,
                        DomainErrorCode.INTERNAL_ERROR.code(), "Internal server error", null)));
    }
}
