package com.financialapp.gateway.domain.gateway;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.bff.CurrencySummary;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface FinancesGateway {

    /** Per-currency income/expense/balance totals for [from, to]. */
    CompletableFuture<List<CurrencySummary>> fetchSummary(UserId userId, LocalDate from, LocalDate to);

    CompletableFuture<Map<String, Object>> fetchTransactions(
            UserId userId, int page, int size, List<String> categories, List<String> accounts, LocalDate from, LocalDate to);

    CompletableFuture<Map<String, Object>> fetchTransactionById(UserId userId, Long id);

    CompletableFuture<List<Map<String, Object>>> fetchBudgets(UserId userId, String period);

    CompletableFuture<List<Map<String, Object>>> fetchBudgetPace(UserId userId, String period);

    CompletableFuture<List<Map<String, Object>>> fetchCategorizationRules(UserId userId);

    CompletableFuture<List<Map<String, Object>>> fetchSpendByCategory(UserId userId, LocalDate from, LocalDate to, String kind);

    CompletableFuture<Map<String, Object>> fetchUncategorisedCount(UserId userId);

    CompletableFuture<List<Map<String, Object>>> searchTransactions(UserId userId, String query);

    CompletableFuture<List<Map<String, Object>>> fetchMonthlyFlow(UserId userId, LocalDate from, LocalDate to);
}
