package com.financialapp.gateway.infrastructure.gateway.Impl;

import com.financialapp.gateway.domain.common.model.TimeoutPolicy;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.model.dashboard.CurrencySummary;
import com.financialapp.gateway.infrastructure.config.ServicesProperties;
import com.financialapp.gateway.infrastructure.gateway.dto.FinanceCurrencyTotals;
import com.financialapp.gateway.infrastructure.gateway.dto.GatewayApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class FinancesGatewayImpl implements FinancesGateway {

    private static final ParameterizedTypeReference<GatewayApiResponse<Map<String, FinanceCurrencyTotals>>> SUMMARY_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final String financesUrl;
    private final TimeoutPolicy timeoutPolicy;

    public FinancesGatewayImpl(WebClient internalWebClient, ServicesProperties services, TimeoutPolicy timeoutPolicy) {
        this.webClient = internalWebClient;
        this.financesUrl = services.getFinancesUrl();
        this.timeoutPolicy = timeoutPolicy;
    }

    @Override
    public CompletableFuture<List<CurrencySummary>> fetchSummary(UserId userId, LocalDate from, LocalDate to) {
        return webClient.get()
                .uri(financesUrl + "/api/v1/finances/transactions/summary?from={from}&to={to}", from, to)
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(SUMMARY_TYPE)
                .map(this::toCurrencySummaries)
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    private List<CurrencySummary> toCurrencySummaries(GatewayApiResponse<Map<String, FinanceCurrencyTotals>> response) {
        Map<String, FinanceCurrencyTotals> byCurrency = response.data();
        if (byCurrency == null) {
            return List.of();
        }
        return byCurrency.entrySet().stream()
                .map(e -> new CurrencySummary(
                        e.getKey(),
                        e.getValue().totalIncome(),
                        e.getValue().totalExpense(),
                        e.getValue().balance()))
                .toList();
    }
}
