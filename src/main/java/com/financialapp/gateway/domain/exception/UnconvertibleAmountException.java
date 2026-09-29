package com.financialapp.gateway.domain.exception;

import com.financialapp.commons.core.error.DomainException;
import com.financialapp.gateway.domain.model.currency.Currency;

public class UnconvertibleAmountException extends DomainException {

    public UnconvertibleAmountException(Currency currency) {
        super(DomainErrorCode.UNCONVERTIBLE_AMOUNT, "No exchange rate to convert " + currency.code() + " into ARS");
    }
}
