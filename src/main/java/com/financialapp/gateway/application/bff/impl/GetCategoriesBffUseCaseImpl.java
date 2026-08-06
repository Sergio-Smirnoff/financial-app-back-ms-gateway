package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.model.bff.CategoriesBffData;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.usecase.bff.GetCategoriesBffUseCase;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GetCategoriesBffUseCaseImpl implements GetCategoriesBffUseCase {

    private final FinancesGateway finances;
    private final PageTimeoutBudget budget;
    private final Clock clock;

    public GetCategoriesBffUseCaseImpl(FinancesGateway finances, PageTimeoutBudget budget) {
        this(finances, budget, Clock.systemUTC());
    }

    public GetCategoriesBffUseCaseImpl(FinancesGateway finances, PageTimeoutBudget budget, Clock clock) {
        this.finances = finances;
        this.budget = budget != null ? budget : PageTimeoutBudget.fromMillis(5000);
        this.clock = clock;
    }

    @Override
    public CompletableFuture<CategoriesBffData> execute(UserId userId, CurrencyView currencyView, String secondary) {
        String period = LocalDate.now().toString().substring(0, 7); // YYYY-MM

        CompletableFuture<Section<Map<String, Object>>> kpis = applyBudget(
                Section.guard(finances.fetchBudgetPace(userId, period), Map.of(), clock), Map.of());

        CompletableFuture<Section<List<Map<String, Object>>>> budgets = applyBudget(
                Section.guard(finances.fetchBudgets(userId, period), List.of(), clock), List.of());

        CompletableFuture<Section<Map<String, Object>>> selectedTrend = applyBudget(
                Section.guard(finances.fetchSpendByCategory(userId, LocalDate.now().minusMonths(6), LocalDate.now(), "EXPENSE")
                        .thenApply(s -> Map.of("trend", (Object) s)), Map.of(), clock), Map.of());

        CompletableFuture<Section<List<Map<String, Object>>>> rules = applyBudget(
                Section.guard(finances.fetchCategorizationRules(userId), List.of(), clock), List.of());

        return CompletableFuture.allOf(kpis, budgets, selectedTrend, rules)
                .thenApply(v -> new CategoriesBffData(
                        kpis.join(), budgets.join(), selectedTrend.join(), rules.join()));
    }

    private <T> CompletableFuture<Section<T>> applyBudget(CompletableFuture<Section<T>> sectionFuture, T fallback) {
        return sectionFuture.completeOnTimeout(
                Section.unavailable(fallback, ObservedAt.now(clock)),
                budget.total().toMillis(),
                TimeUnit.MILLISECONDS);
    }
}
