package com.financialapp.gateway.domain.exception;

public class InvalidAccessTokenException extends RuntimeException {
    public InvalidAccessTokenException(String message) {
        super(message);
    }
}
