package com.financialapp.gateway.infrastructure.gateway.Impl;

import com.financialapp.gateway.domain.common.model.TimeoutPolicy;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.bff.HistoryRange;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.model.currency.FxRate;
import com.financialapp.gateway.domain.model.currency.FxRateMode;
import com.financialapp.gateway.infrastructure.config.ServicesProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    private static final LocalDate RATE_DAY = LocalDate.of(2026, 9, 28);

    @ParameterizedTest
    @CsvSource({"USD_MEP,MEP", "USD_CCL,CCL", "USD_OFICIAL,OFICIAL"})
    void fxRatesAskForTheViewMsInvestmentsKnows(CurrencyView view, String downstreamView) {
        AtomicReference<String> query = new AtomicReference<>();
        InvestmentsGatewayImpl gateway = gatewayAnswering(request -> {
            query.set(request.url().getQuery());
            return okJson("{\"data\":[{\"date\":\"2026-09-28\",\"view\":\"" + downstreamView
                    + "\",\"buy\":\"1180.00\",\"sell\":\"1190.00\",\"source\":\"dolarapi\"}]}");
        });

        List<FxRate> rates = gateway.fetchFxRates(RATE_DAY, RATE_DAY, view).join();

        assertThat(query.get()).contains("view=" + downstreamView).doesNotContain("USD_");
        assertThat(rates).extracting(FxRate::mode).containsExactly(FxRateMode.valueOf(downstreamView));
    }

    @Test
    void aRejectedFxRequestFailsInsteadOfReportingNoRate() {
        InvestmentsGatewayImpl gateway = gatewayAnswering(request -> Mono.just(ClientResponse.create(HttpStatus.BAD_REQUEST)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body("{\"status\":400,\"code\":\"invalid_request\",\"message\":\"No enum constant\"}")
                .build()));

        assertThatThrownBy(() -> gateway.fetchFxRates(RATE_DAY, RATE_DAY, CurrencyView.USD_MEP).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(WebClientResponseException.BadRequest.class);
    }

    @Test
    void theArsViewHasNoRateToAskFor() {
        AtomicInteger calls = new AtomicInteger();
        InvestmentsGatewayImpl gateway = gatewayReturning("{\"data\":[]}", calls);

        assertThatThrownBy(() -> gateway.fetchFxRates(RATE_DAY, RATE_DAY, CurrencyView.ARS).join())
                .hasCauseInstanceOf(IllegalArgumentException.class);
        assertThat(calls.get()).isZero();
    }

    @Test
    void aWeekendDayUsesTheLatestRateOnOrBeforeIt() {
        LocalDate saturday = LocalDate.of(2026, 9, 26);
        AtomicReference<String> query = new AtomicReference<>();
        InvestmentsGatewayImpl gateway = gatewayAnswering(request -> {
            query.set(request.url().getQuery());
            return okJson("""
                    {"data":[
                      {"date":"2026-09-24","view":"MEP","buy":"1170.00","sell":"1180.00","source":"dolarapi"},
                      {"date":"2026-09-25","view":"MEP","buy":"1180.00","sell":"1190.00","source":"dolarapi"}]}""");
        });

        Optional<FxRate> rate = gateway.fetchFxRate(CurrencyView.USD_MEP, saturday).join();

        assertThat(query.get()).contains("from=2026-09-19").contains("to=2026-09-26").contains("view=MEP");
        assertThat(rate).hasValueSatisfying(friday -> {
            assertThat(friday.date()).isEqualTo(LocalDate.of(2026, 9, 25));
            assertThat(friday.buy()).isEqualByComparingTo("1180.00");
        });
    }

    @Test
    void noRateInTheLookbackWindowIsNoRate() {
        InvestmentsGatewayImpl gateway = gatewayAnswering(request -> okJson("{\"data\":[]}"));

        assertThat(gateway.fetchFxRate(CurrencyView.USD_MEP, LocalDate.of(2026, 9, 26)).join()).isEmpty();
    }

    @Test
    void holdingCurrenciesReadThePortfolioHoldingsList() {
        AtomicReference<String> path = new AtomicReference<>();
        InvestmentsGatewayImpl gateway = gatewayAnswering(request -> {
            path.set(request.url().getPath());
            return okJson("{\"data\":[{\"id\":7,\"ticker\":\"AO29\",\"currency\":\"ARS\"},"
                    + "{\"id\":8,\"ticker\":\"SPY\",\"currency\":\"USD\"}]}");
        });

        List<Currency> currencies = gateway.holdingCurrencies(42L).join();

        assertThat(path.get()).isEqualTo("/api/v1/investments/portfolio/holdings");
        assertThat(currencies).containsExactly(Currency.ARS, Currency.USD);
    }

    @Test
    void portfolioEvolutionAsksForTheRangesDays() {
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        InvestmentsGatewayImpl gateway = gatewayAnswering(request -> {
            path.set(request.url().getPath());
            query.set(request.url().getQuery());
            return okJson("{\"data\":[{\"date\":\"2026-09-27\",\"totals\":[{\"currency\":\"ARS\",\"totalValue\":\"10\"}]}]}");
        });

        List<Map<String, Object>> points = gateway.fetchPortfolioEvolution(new UserId(1L), HistoryRange.ONE_YEAR).join();

        assertThat(path.get()).isEqualTo("/api/v1/investments/portfolio/evolution");
        assertThat(query.get()).isEqualTo("days=365");
        assertThat(points).singleElement().extracting(m -> m.get("date")).isEqualTo("2026-09-27");
    }

    private InvestmentsGatewayImpl gatewayAnswering(ExchangeFunction exchange) {
        WebClient webClient = WebClient.builder().exchangeFunction(exchange).build();
        ServicesProperties services = new ServicesProperties();
        services.setInvestmentsUrl("http://investments.test");
        return new InvestmentsGatewayImpl(webClient, services, new TimeoutPolicy(Duration.ofSeconds(5)));
    }

    private static Mono<ClientResponse> okJson(String json) {
        return Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(json)
                .build());
    }
}
