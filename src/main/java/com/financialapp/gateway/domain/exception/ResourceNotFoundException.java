package com.financialapp.gateway.domain.exception;

import com.financialapp.commons.core.error.DomainException;

public class ResourceNotFoundException extends DomainException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super(DomainErrorCode.RESOURCE_NOT_FOUND, resource + " " + identifier + " not found");
    }
}
