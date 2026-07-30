package com.financialapp.gateway.infrastructure.gateway.Impl;

import com.financialapp.gateway.domain.common.model.TimeoutPolicy;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.FxRateMode;
import com.financialapp.gateway.domain.model.currency.ManualCurrencyRate;
import com.financialapp.gateway.domain.model.currency.UserDisplayPreferences;
import com.financialapp.gateway.infrastructure.config.ServicesProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UsersGatewayImplTest {

    private UsersGatewayImpl gatewayReturning(String json) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body(json)
                        .build()))
                .build();
        ServicesProperties services = new ServicesProperties();
        services.setUsersUrl("http://users.test");
        return new UsersGatewayImpl(webClient, services, new TimeoutPolicy(Duration.ofSeconds(5)));
    }

    @Test
    void maps_user_preferences() {
        String json = """
            {
              "success": true,
              "data": {
                "maxIdleMinutes": 15,
                "timezone": "America/Argentina/Buenos_Aires",
                "primaryCurrency": "USD",
                "secondaryCurrency": "USD_MEP",
                "numberFormat": "1.234,56",
                "decimals": 2,
                "colorForAmounts": true
              }
            }
            """;
        UserDisplayPreferences result = gatewayReturning(json).displayPreferences(42L).join();
        assertThat(result.primaryCurrency()).isEqualTo(Currency.USD);
        assertThat(result.secondaryMode()).isEqualTo(FxRateMode.MEP);
        assertThat(result.numberFormat()).isEqualTo("1.234,56");
        assertThat(result.decimals()).isEqualTo(2);
        assertThat(result.colorForAmounts()).isTrue();
    }

    @Test
    void maps_manual_currency_rates() {
        String json = """
            {
              "success": true,
              "data": [
                { "currency": "EUR", "ratePerArs": "1200.00", "updatedAt": "2026-07-30T10:00:00Z" }
              ]
            }
            """;
        List<ManualCurrencyRate> result = gatewayReturning(json).manualCurrencyRates(42L).join();
        assertThat(result).containsExactly(new ManualCurrencyRate(Currency.EUR, new BigDecimal("1200.00")));
    }
}
