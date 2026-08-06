package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.model.bff.TransactionsBffData;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.usecase.bff.GetTransactionsBffUseCase;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GetTransactionsBffUseCaseImpl implements GetTransactionsBffUseCase {

    private final FinancesGateway finances;
    private final PageTimeoutBudget budget;
    private final Clock clock;

    public GetTransactionsBffUseCaseImpl(FinancesGateway finances, PageTimeoutBudget budget) {
        this(finances, budget, Clock.systemUTC());
    }

    public GetTransactionsBffUseCaseImpl(FinancesGateway finances, PageTimeoutBudget budget, Clock clock) {
        this.finances = finances;
        this.budget = budget != null ? budget : PageTimeoutBudget.fromMillis(5000);
        this.clock = clock;
    }

    @Override
    public CompletableFuture<TransactionsBffData> execute(
            UserId userId, int page, int size, List<String> categories, List<String> accounts,
            LocalDate from, LocalDate to, CurrencyView currencyView, String secondary) {

        CompletableFuture<Section<Map<String, Object>>> summary = applyBudget(
                Section.guard(finances.fetchSummary(userId, from, to).thenApply(s -> Map.of("summary", (Object) s)), Map.of(), clock), Map.of());

        CompletableFuture<Section<Map<String, Object>>> pageSection = applyBudget(
                Section.guard(finances.fetchTransactions(userId, page, size, categories, accounts, from, to), Map.of(), clock), Map.of());

        CompletableFuture<Section<Map<String, Object>>> filterOptions = applyBudget(
                Section.guard(finances.fetchCategorizationRules(userId).thenApply(r -> Map.of("rules", (Object) r)), Map.of(), clock), Map.of());

        CompletableFuture<Section<Map<String, Object>>> uncategorised = applyBudget(
                Section.guard(finances.fetchUncategorisedCount(userId), Map.of(), clock), Map.of());

        return CompletableFuture.allOf(summary, pageSection, filterOptions, uncategorised)
                .thenApply(v -> new TransactionsBffData(
                        summary.join(), pageSection.join(), filterOptions.join(), uncategorised.join()));
    }

    private <T> CompletableFuture<Section<T>> applyBudget(CompletableFuture<Section<T>> sectionFuture, T fallback) {
        return sectionFuture.completeOnTimeout(
                Section.unavailable(fallback, ObservedAt.now(clock)),
                budget.total().toMillis(),
                TimeUnit.MILLISECONDS);
    }
}
