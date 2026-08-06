package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.model.bff.InvestmentsBffData;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.usecase.bff.GetInvestmentsBffUseCase;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GetInvestmentsBffUseCaseImpl implements GetInvestmentsBffUseCase {

    private final InvestmentsGateway investments;
    private final PageTimeoutBudget budget;
    private final Clock clock;

    public GetInvestmentsBffUseCaseImpl(InvestmentsGateway investments, PageTimeoutBudget budget) {
        this(investments, budget, Clock.systemUTC());
    }

    public GetInvestmentsBffUseCaseImpl(InvestmentsGateway investments, PageTimeoutBudget budget, Clock clock) {
        this.investments = investments;
        this.budget = budget != null ? budget : PageTimeoutBudget.fromMillis(5000);
        this.clock = clock;
    }

    @Override
    public CompletableFuture<InvestmentsBffData> execute(UserId userId, CurrencyView currencyView, String secondary) {
        CompletableFuture<Section<Map<String, Object>>> marketStrip = applyBudget(
                Section.guard(investments.fetchMarketPanel(), Map.of(), clock), Map.of());

        CompletableFuture<Section<Map<String, Object>>> kpis = applyBudget(
                Section.guard(investments.fetchPortfolioSummary(userId), Map.of(), clock), Map.of());

        CompletableFuture<Section<List<Map<String, Object>>>> evolution = applyBudget(
                Section.guard(investments.fetchPortfolioEvolution(userId), List.of(), clock), List.of());

        CompletableFuture<Section<List<Map<String, Object>>>> positions = applyBudget(
                Section.guard(investments.fetchHoldings(userId), List.of(), clock), List.of());

        CompletableFuture<Section<Map<String, Object>>> composition = applyBudget(
                Section.guard(investments.fetchPortfolioSummary(userId), Map.of(), clock), Map.of());

        CompletableFuture<Section<List<Map<String, Object>>>> recentOperations = applyBudget(
                Section.guard(investments.fetchHoldings(userId), List.of(), clock), List.of());

        CompletableFuture<Section<List<Map<String, Object>>>> alerts = applyBudget(
                Section.guard(investments.fetchBrokerFees(userId), List.of(), clock), List.of());

        return CompletableFuture.allOf(marketStrip, kpis, evolution, positions, composition, recentOperations, alerts)
                .thenApply(v -> new InvestmentsBffData(
                        marketStrip.join(), kpis.join(), evolution.join(), positions.join(),
                        composition.join(), recentOperations.join(), alerts.join()));
    }

    private <T> CompletableFuture<Section<T>> applyBudget(CompletableFuture<Section<T>> sectionFuture, T fallback) {
        return sectionFuture.completeOnTimeout(
                Section.unavailable(fallback, ObservedAt.now(clock)),
                budget.total().toMillis(),
                TimeUnit.MILLISECONDS);
    }
}
