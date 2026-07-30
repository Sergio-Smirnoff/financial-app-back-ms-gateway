package com.financialapp.gateway.domain.gateway;

import com.financialapp.gateway.domain.model.currency.ManualCurrencyRate;
import com.financialapp.gateway.domain.model.currency.UserDisplayPreferences;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface UsersGateway {
    CompletableFuture<UserDisplayPreferences> displayPreferences(Long userId);
    CompletableFuture<List<ManualCurrencyRate>> manualCurrencyRates(Long userId);
}
