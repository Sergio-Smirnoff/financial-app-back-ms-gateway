package com.financialapp.gateway.domain.usecase.currency;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.service.AvailableCurrencies.AvailableCurrenciesResult;

import java.util.concurrent.CompletableFuture;

public interface GetAvailableCurrencies {
    CompletableFuture<AvailableCurrenciesResult> execute(UserId userId);
}
