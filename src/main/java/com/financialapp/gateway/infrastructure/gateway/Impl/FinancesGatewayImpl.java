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
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Component
public class FinancesGatewayImpl implements FinancesGateway {

    private static final ParameterizedTypeReference<GatewayApiResponse<Map<String, FinanceCurrencyTotals>>> SUMMARY_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<GatewayApiResponse<Map<String, Object>>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<GatewayApiResponse<List<Map<String, Object>>>> LIST_MAP_TYPE =
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

    @Override
    public CompletableFuture<Map<String, Object>> fetchTransactions(
            UserId userId, int page, int size, List<String> categories, List<String> accounts, LocalDate from, LocalDate to) {
        return webClient.get()
                .uri(UriComponentsBuilder.fromUriString(financesUrl + "/api/v1/finances/transactions")
                        .queryParam("size", size)
                        .queryParamIfPresent("from", Optional.ofNullable(from))
                        .queryParamIfPresent("to", Optional.ofNullable(to))
                        .build()
                        .toUri())
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .map(r -> r.data() != null ? r.data() : Map.<String, Object>of())
                .onErrorReturn(Map.of())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    @Override
    public CompletableFuture<Map<String, Object>> fetchTransactionById(UserId userId, Long id) {
        return webClient.get()
                .uri(financesUrl + "/api/v1/finances/transactions/{id}", id)
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .map(r -> r.data() != null ? r.data() : Map.<String, Object>of())
                .onErrorReturn(Map.of())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> fetchBudgets(UserId userId, String period) {
        return webClient.get()
                .uri(financesUrl + "/api/v1/finances/budgets?period={period}", period)
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(LIST_MAP_TYPE)
                .map(r -> r.data() != null ? r.data() : List.<Map<String, Object>>of())
                .onErrorReturn(List.of())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    @Override
    public CompletableFuture<Map<String, Object>> fetchBudgetPace(UserId userId, String period) {
        return webClient.get()
                .uri(financesUrl + "/api/v1/finances/budgets/pace?period={period}", period)
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .map(r -> r.data() != null ? r.data() : Map.<String, Object>of())
                .onErrorReturn(Map.of())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> fetchCategorizationRules(UserId userId) {
        return webClient.get()
                .uri(financesUrl + "/api/v1/finances/categorization-rules")
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(LIST_MAP_TYPE)
                .map(r -> r.data() != null ? r.data() : List.<Map<String, Object>>of())
                .onErrorReturn(List.of())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> fetchSpendByCategory(UserId userId, LocalDate from, LocalDate to, String kind) {
        return webClient.get()
                .uri(financesUrl + "/api/v1/finances/categories/spend?from={from}&to={to}&kind={kind}", from, to, kind)
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(LIST_MAP_TYPE)
                .map(r -> r.data() != null ? r.data() : List.<Map<String, Object>>of())
                .onErrorReturn(List.of())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    @Override
    public CompletableFuture<Map<String, Object>> fetchUncategorisedCount(UserId userId) {
        return webClient.get()
                .uri(financesUrl + "/api/v1/finances/transactions/uncategorised/count")
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .map(r -> r.data() != null ? r.data() : Map.<String, Object>of())
                .onErrorReturn(Map.of())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> searchTransactions(UserId userId, String query) {
        return webClient.get()
                .uri(financesUrl + "/api/v1/finances/transactions/search?q={query}", query)
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(LIST_MAP_TYPE)
                .map(r -> r.data() != null ? r.data() : List.<Map<String, Object>>of())
                .onErrorReturn(List.of())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> fetchMonthlyFlow(UserId userId, LocalDate from, LocalDate to) {
        return webClient.get()
                .uri(financesUrl + "/api/v1/finances/transactions/summary/monthly?from={from}&to={to}", from, to)
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(LIST_MAP_TYPE)
                .map(r -> r.data() != null ? r.data() : List.<Map<String, Object>>of())
                .onErrorReturn(List.of())
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
