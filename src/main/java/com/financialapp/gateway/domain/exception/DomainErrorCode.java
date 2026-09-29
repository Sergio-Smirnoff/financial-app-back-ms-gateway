package com.financialapp.gateway.domain.exception;

import com.financialapp.commons.core.error.ErrorCategory;
import com.financialapp.commons.core.error.ErrorCode;

public enum DomainErrorCode implements ErrorCode {

    UNAUTHORIZED(ErrorCategory.UNAUTHORIZED, "unauthorized"),
    RATE_LIMITED(ErrorCategory.TOO_MANY_REQUESTS, "rate_limit_exceeded"),
    INVALID_REQUEST(ErrorCategory.BAD_REQUEST, "invalid_request"),
    RESOURCE_NOT_FOUND(ErrorCategory.NOT_FOUND, "resource_not_found"),
    UPSTREAM_UNAVAILABLE(ErrorCategory.INTERNAL_SERVER_ERROR, "upstream_unavailable"),
    UPSTREAM_CONTRACT_VIOLATION(ErrorCategory.INTERNAL_SERVER_ERROR, "upstream_contract_violation"),
    UNCONVERTIBLE_AMOUNT(ErrorCategory.INTERNAL_SERVER_ERROR, "unconvertible_amount"),
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
