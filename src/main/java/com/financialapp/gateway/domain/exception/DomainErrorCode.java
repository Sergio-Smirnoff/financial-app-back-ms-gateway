package com.financialapp.gateway.domain.exception;

/** Neutral, framework-free error codes. HTTP mapping lives in web/error. */
public enum DomainErrorCode {
    UNAUTHORIZED,
    RATE_LIMITED,
    UPSTREAM_UNAVAILABLE,
    INTERNAL_ERROR
}
