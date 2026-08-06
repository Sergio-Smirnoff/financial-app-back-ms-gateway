package com.financialapp.gateway.infrastructure.gateway.Impl;

import com.financialapp.gateway.domain.common.model.TimeoutPolicy;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.FxRate;
import com.financialapp.gateway.domain.model.currency.FxRateMode;
import com.financialapp.gateway.infrastructure.config.ServicesProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class InvestmentsGatewayImplTest {

    private InvestmentsGatewayImpl gatewayReturning(String json) {
        return gatewayReturning(json, new AtomicInteger());
    }

    private InvestmentsGatewayImpl gatewayReturning(String json, AtomicInteger callCounter) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    callCounter.incrementAndGet();
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .body(json)
                            .build());
                })
                .build();
        ServicesProperties services = new ServicesProperties();
        services.setInvestmentsUrl("http://investments.test");
        return new InvestmentsGatewayImpl(webClient, services, new TimeoutPolicy(Duration.ofSeconds(5)));
    }

    @Test
    void maps_fx_rates_response() {
        String json = """
            {
              "success": true,
              "data": [
                { "date": "2026-07-30", "view": "MEP", "buy": "1000.00", "sell": "1050.00", "source": "synthetic" },
                { "date": "2026-07-30", "view": "CCL", "buy": "1100.00", "sell": "1150.00", "source": "synthetic" }
              ]
            }
            """;
        List<FxRate> result = gatewayReturning(json).latestFxRates().join();
        assertThat(result).hasSize(2);
        assertThat(result.get(0)).isEqualTo(new FxRate(
                LocalDate.of(2026, 7, 30), FxRateMode.MEP, new BigDecimal("1000.00"), new BigDecimal("1050.00")));
    }

    @Test
    void caches_fx_rates_within_ttl() {
        String json = """
            {
              "success": true,
              "data": [
                { "date": "2026-07-30", "view": "MEP", "buy": "1000.00", "sell": "1050.00", "source": "synthetic" }
              ]
            }
            """;
        AtomicInteger counter = new AtomicInteger();
        InvestmentsGatewayImpl gateway = gatewayReturning(json, counter);

        List<FxRate> firstCall = gateway.latestFxRates().join();
        List<FxRate> secondCall = gateway.latestFxRates().join();

        assertThat(firstCall).isEqualTo(secondCall);
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    void maps_holding_currencies_deduped() {
        String json = """
            {
              "success": true,
              "data": [
                { "id": 1, "symbol": "AAPL", "currency": "USD" },
                { "id": 2, "symbol": "AL30", "currency": "ARS" },
                { "id": 3, "symbol": "MSFT", "currency": "USD" }
              ]
            }
            """;
        List<Currency> result = gatewayReturning(json).holdingCurrencies(42L).join();
        assertThat(result).containsExactly(Currency.USD, Currency.ARS);
    }

    @Test
    void searchPositionsCallsTheRealInvestmentsPath() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    assertThat(request.url().getPath()).isEqualTo("/api/v1/investments/positions/search");
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .body("""
                                    {"success":true,"data":[{"holdingId":1,"ticker":"YPFD","name":"YPF S.A.",
                                     "quantity":"10","marketValue":"35000.00","currency":"ARS"}]}""")
                            .build());
                })
                .build();
        ServicesProperties services = new ServicesProperties();
        services.setInvestmentsUrl("http://investments.test");
        InvestmentsGatewayImpl gateway = new InvestmentsGatewayImpl(webClient, services, new TimeoutPolicy(Duration.ofSeconds(5)));

        List<Map<String, Object>> hits = gateway.searchPositions(new UserId(1L), "ypf").join();
        assertThat(hits).singleElement().extracting(m -> m.get("ticker")).isEqualTo("YPFD");
    }
}
