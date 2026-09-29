package com.financialapp.gateway.web.error;

import com.financialapp.commons.core.error.ErrorCategory;
import com.financialapp.gateway.domain.exception.DomainErrorCode;
import org.springframework.http.HttpStatus;

final class StatusErrorCodes {

    private StatusErrorCodes() {
    }

    static DomainErrorCode codeFor(HttpStatus status) {
        return switch (status) {
            case BAD_REQUEST -> DomainErrorCode.INVALID_REQUEST;
            case NOT_FOUND -> DomainErrorCode.RESOURCE_NOT_FOUND;
            case UNAUTHORIZED -> DomainErrorCode.UNAUTHORIZED;
            case TOO_MANY_REQUESTS -> DomainErrorCode.RATE_LIMITED;
            case SERVICE_UNAVAILABLE, BAD_GATEWAY, GATEWAY_TIMEOUT -> DomainErrorCode.UPSTREAM_UNAVAILABLE;
            default -> DomainErrorCode.INTERNAL_ERROR;
        };
    }

    static HttpStatus statusFor(ErrorCategory category) {
        return switch (category) {
            case BAD_REQUEST -> HttpStatus.BAD_REQUEST;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            case INTERNAL_SERVER_ERROR -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
    }
}
