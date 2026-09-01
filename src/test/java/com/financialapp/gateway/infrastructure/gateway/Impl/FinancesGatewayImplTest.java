package com.financialapp.gateway.infrastructure.gateway.Impl;

import com.financialapp.gateway.domain.common.model.TimeoutPolicy;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.bff.CurrencySummary;
import com.financialapp.gateway.infrastructure.config.ServicesProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FinancesGatewayImplTest {

    private FinancesGatewayImpl gatewayReturning(String json) {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body(json)
                        .build()))
                .build();
        ServicesProperties services = new ServicesProperties();
        services.setFinancesUrl("http://finances.test");
        return new FinancesGatewayImpl(webClient, services, new TimeoutPolicy(Duration.ofSeconds(5)));
    }

    @Test
    void translates_per_currency_map_into_currency_summaries() {
        String json = """
            { "success": true, "data": {
                "ARS": { "totalIncome": "1000.00", "totalExpense": "400.00", "balance": "600.00" } } }
            """;
        List<CurrencySummary> result = gatewayReturning(json)
                .fetchSummary(new UserId(1L), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
                .join();
        assertThat(result).containsExactly(new CurrencySummary("ARS", "1000.00", "400.00", "600.00"));
    }

    @Test
    void returns_empty_list_when_data_is_null() {
        String json = "{ \"success\": true, \"data\": null }";
        List<CurrencySummary> result = gatewayReturning(json)
                .fetchSummary(new UserId(1L), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
                .join();
        assertThat(result).isEmpty();
    }

    @Test
    void searchTransactionsCallsTheRealFinancesPath() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> {
                    assertThat(request.url().getPath()).isEqualTo("/api/v1/finances/transactions/search");
                    return Mono.just(ClientResponse.create(HttpStatus.OK)
                            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .body("""
                                    {"success":true,"data":[{"id":1,"date":"2026-07-04","description":"Supermercado",
                                     "amount":"12500.00","currency":"ARS","direction":"OUT"}]}""")
                            .build());
                })
                .build();
        ServicesProperties services = new ServicesProperties();
        services.setFinancesUrl("http://finances.test");
        FinancesGatewayImpl gateway = new FinancesGatewayImpl(webClient, services, new TimeoutPolicy(Duration.ofSeconds(5)));

        List<Map<String, Object>> hits = gateway.searchTransactions(new UserId(1L), "super").join();
        assertThat(hits).singleElement().extracting(m -> m.get("description")).isEqualTo("Supermercado");
    }
}
