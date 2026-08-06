package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.model.bff.SearchBffData;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.usecase.bff.GetSearchBffUseCase;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GetSearchBffUseCaseImpl implements GetSearchBffUseCase {

    private final FinancesGateway finances;
    private final InvestmentsGateway investments;
    private final PageTimeoutBudget budget;
    private final Clock clock;

    public GetSearchBffUseCaseImpl(FinancesGateway finances, InvestmentsGateway investments, PageTimeoutBudget budget) {
        this(finances, investments, budget, Clock.systemUTC());
    }

    public GetSearchBffUseCaseImpl(
            FinancesGateway finances, InvestmentsGateway investments,
            PageTimeoutBudget budget, Clock clock) {
        this.finances = finances;
        this.investments = investments;
        this.budget = budget != null ? budget : PageTimeoutBudget.fromMillis(5000);
        this.clock = clock;
    }

    @Override
    public CompletableFuture<SearchBffData> execute(UserId userId, String query) {
        CompletableFuture<Section<List<Map<String, Object>>>> movements = applyBudget(
                Section.guard(finances.searchTransactions(userId, query), List.of(), clock), List.of());

        CompletableFuture<Section<List<Map<String, Object>>>> positions = applyBudget(
                Section.guard(investments.searchPositions(userId, query), List.of(), clock), List.of());

        CompletableFuture<Section<List<Map<String, Object>>>> categories = applyBudget(
                Section.guard(finances.fetchCategorizationRules(userId), List.of(), clock), List.of());

        return CompletableFuture.allOf(movements, positions, categories)
                .thenApply(v -> new SearchBffData(movements.join(), positions.join(), categories.join()));
    }

    private <T> CompletableFuture<Section<T>> applyBudget(CompletableFuture<Section<T>> sectionFuture, T fallback) {
        return sectionFuture.completeOnTimeout(
                Section.unavailable(fallback, ObservedAt.now(clock)),
                budget.total().toMillis(),
                TimeUnit.MILLISECONDS);
    }
}
