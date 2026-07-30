package com.financialapp.gateway.infrastructure.gateway.Impl;

import com.financialapp.gateway.domain.common.model.TimeoutPolicy;
import com.financialapp.gateway.domain.gateway.UsersGateway;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.FxRateMode;
import com.financialapp.gateway.domain.model.currency.ManualCurrencyRate;
import com.financialapp.gateway.domain.model.currency.UserDisplayPreferences;
import com.financialapp.gateway.infrastructure.config.ServicesProperties;
import com.financialapp.gateway.infrastructure.gateway.dto.GatewayApiResponse;
import com.financialapp.gateway.infrastructure.gateway.dto.ManualCurrencyRateResponse;
import com.financialapp.gateway.infrastructure.gateway.dto.UserPreferencesResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@Component
public class UsersGatewayImpl implements UsersGateway {

    private static final ParameterizedTypeReference<GatewayApiResponse<UserPreferencesResponse>> PREFERENCES_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<GatewayApiResponse<List<ManualCurrencyRateResponse>>> RATES_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final String usersUrl;
    private final TimeoutPolicy timeoutPolicy;

    public UsersGatewayImpl(WebClient internalWebClient, ServicesProperties services, TimeoutPolicy timeoutPolicy) {
        this.webClient = internalWebClient;
        this.usersUrl = services.getUsersUrl();
        this.timeoutPolicy = timeoutPolicy;
    }

    @Override
    public CompletableFuture<UserDisplayPreferences> displayPreferences(Long userId) {
        return webClient.get()
                .uri(usersUrl + "/api/v1/users/me/preferences")
                .header("X-User-Id", userId.toString())
                .retrieve()
                .bodyToMono(PREFERENCES_TYPE)
                .map(response -> toDisplayPreferences(response.data()))
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    @Override
    public CompletableFuture<List<ManualCurrencyRate>> manualCurrencyRates(Long userId) {
        return webClient.get()
                .uri(usersUrl + "/api/v1/users/me/currency-rates")
                .header("X-User-Id", userId.toString())
                .retrieve()
                .bodyToMono(RATES_TYPE)
                .map(response -> nullSafe(response.data()).stream()
                        .map(this::toManualCurrencyRate)
                        .filter(Objects::nonNull)
                        .toList())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    private UserDisplayPreferences toDisplayPreferences(UserPreferencesResponse dto) {
        if (dto == null) {
            return new UserDisplayPreferences(Currency.ARS, null, "1.234,56", 2, true);
        }
        Currency primary = dto.primaryCurrency() != null ? Currency.of(dto.primaryCurrency()) : Currency.ARS;
        FxRateMode secondary = parseFxRateMode(dto.secondaryCurrency());
        String numberFormat = dto.numberFormat() != null ? dto.numberFormat() : "1.234,56";
        int decimals = dto.decimals() != null ? dto.decimals() : 2;
        boolean colorForAmounts = dto.colorForAmounts() == null || dto.colorForAmounts();

        return new UserDisplayPreferences(primary, secondary, numberFormat, decimals, colorForAmounts);
    }

    private FxRateMode parseFxRateMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String clean = raw.toUpperCase().replace("USD_", "");
        try {
            return FxRateMode.valueOf(clean);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private ManualCurrencyRate toManualCurrencyRate(ManualCurrencyRateResponse dto) {
        try {
            return new ManualCurrencyRate(Currency.of(dto.currency()), new BigDecimal(dto.ratePerArs()));
        } catch (Exception e) {
            return null;
        }
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list == null ? List.of() : list;
    }
}
