package com.financialapp.gateway.domain.gateway;

import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.FxRate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface InvestmentsGateway {
    CompletableFuture<List<FxRate>> latestFxRates();
    CompletableFuture<List<Currency>> holdingCurrencies(Long userId);
}
