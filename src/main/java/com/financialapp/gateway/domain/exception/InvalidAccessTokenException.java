package com.financialapp.gateway.domain.exception;

import com.financialapp.commons.core.error.DomainException;

public class InvalidAccessTokenException extends DomainException {
    public InvalidAccessTokenException(String message) {
        super(DomainErrorCode.UNAUTHORIZED, message);
    }
}
