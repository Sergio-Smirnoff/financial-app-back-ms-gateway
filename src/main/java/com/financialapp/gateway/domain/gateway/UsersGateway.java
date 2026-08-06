package com.financialapp.gateway.domain.gateway;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.currency.ManualCurrencyRate;
import com.financialapp.gateway.domain.model.currency.UserDisplayPreferences;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface UsersGateway {
    CompletableFuture<UserDisplayPreferences> displayPreferences(Long userId);

    CompletableFuture<List<ManualCurrencyRate>> manualCurrencyRates(Long userId);

    CompletableFuture<List<Map<String, Object>>> fetchSessions(UserId userId);

    CompletableFuture<Map<String, Object>> fetchPreferences(UserId userId);

    CompletableFuture<Map<String, Object>> fetchProfile(UserId userId);
}
