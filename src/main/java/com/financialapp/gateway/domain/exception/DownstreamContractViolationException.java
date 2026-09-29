package com.financialapp.gateway.domain.exception;

import com.financialapp.commons.core.error.DomainException;

public class DownstreamContractViolationException extends DomainException {

    public DownstreamContractViolationException(String source, String field, String problem) {
        super(DomainErrorCode.UPSTREAM_CONTRACT_VIOLATION, source + " field '" + field + "' " + problem);
    }
}
