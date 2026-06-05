package com.financialapp.gateway.domain.exception;

import com.financialapp.commons.core.error.ErrorCategory;
import com.financialapp.commons.core.error.ErrorCode;

public enum DomainErrorCode implements ErrorCode {

    UNAUTHORIZED(ErrorCategory.UNAUTHORIZED, "unauthorized"),
    RATE_LIMITED(ErrorCategory.TOO_MANY_REQUESTS, "rate_limit_exceeded"),
    UPSTREAM_UNAVAILABLE(ErrorCategory.INTERNAL_SERVER_ERROR, "upstream_unavailable"),
    INTERNAL_ERROR(ErrorCategory.INTERNAL_SERVER_ERROR, "internal_error");

    private final ErrorCategory category;
    private final String code;

    DomainErrorCode(ErrorCategory category, String code) {
        this.category = category;
        this.code = code;
    }

    @Override
    public ErrorCategory category() { return category; }

    @Override
    public String code() { return code; }
}
