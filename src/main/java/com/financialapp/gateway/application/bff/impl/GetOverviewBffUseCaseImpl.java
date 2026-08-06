package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.gateway.NotificationsGateway;
import com.financialapp.gateway.domain.model.bff.OverviewBffData;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.usecase.bff.GetOverviewBffUseCase;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GetOverviewBffUseCaseImpl implements GetOverviewBffUseCase {

    private final FinancesGateway finances;
    private final BanksGateway banks;
    private final InvestmentsGateway investments;
    private final NotificationsGateway notifications;
    private final PageTimeoutBudget budget;
    private final Clock clock;

    public GetOverviewBffUseCaseImpl(
            FinancesGateway finances, BanksGateway banks,
            InvestmentsGateway investments, NotificationsGateway notifications,
            PageTimeoutBudget budget) {
        this(finances, banks, investments, notifications, budget, Clock.systemUTC());
    }

    public GetOverviewBffUseCaseImpl(
            FinancesGateway finances, BanksGateway banks,
            InvestmentsGateway investments, NotificationsGateway notifications,
            PageTimeoutBudget budget, Clock clock) {
        this.finances = finances;
        this.banks = banks;
        this.investments = investments;
        this.notifications = notifications;
        this.budget = budget != null ? budget : PageTimeoutBudget.fromMillis(5000);
        this.clock = clock;
    }

    @Override
    public CompletableFuture<OverviewBffData> execute(UserId userId, CurrencyView currencyView, String secondary) {
        LocalDate today = LocalDate.now();
        LocalDate yearStart = today.withDayOfYear(1);

        CompletableFuture<Section<Map<String, Object>>> kpis = applyBudget(
                Section.guard(finances.fetchSummary(userId, yearStart, today).thenApply(s -> Map.of("summary", (Object) s)), Map.of(), clock), Map.of());

        CompletableFuture<Section<Map<String, Object>>> netWorth = applyBudget(
                Section.guard(investments.fetchPortfolioSummary(userId), Map.of(), clock), Map.of());

        CompletableFuture<Section<Map<String, Object>>> breakdown = applyBudget(
                Section.guard(investments.fetchPortfolioSummary(userId), Map.of(), clock), Map.of());

        CompletableFuture<Section<List<Map<String, Object>>>> flow = applyBudget(
                Section.guard(banks.fetchBalanceSnapshots(userId, today.minusMonths(12), today), List.of(), clock), List.of());

        CompletableFuture<Section<List<Map<String, Object>>>> committed = applyBudget(
                Section.guard(banks.fetchUpcomingPayments(userId, today, today.plusMonths(12)).thenApply(p -> List.of(Map.of("payments", (Object) p))), List.of(), clock), List.of());

        CompletableFuture<Section<List<Map<String, Object>>>> upcomingPayments = applyBudget(
                Section.guard(banks.fetchUpcomingPayments(userId, today, today.plusMonths(1)).thenApply(p -> List.of(Map.of("payments", (Object) p))), List.of(), clock), List.of());

        CompletableFuture<Section<List<Map<String, Object>>>> spendByCategory = applyBudget(
                Section.guard(finances.fetchSpendByCategory(userId, today.withDayOfMonth(1), today, "EXPENSE"), List.of(), clock), List.of());

        CompletableFuture<Section<List<Map<String, Object>>>> latestMovements = applyBudget(
                Section.guard(finances.fetchTransactions(userId, 0, 10, null, null, null, null).thenApply(t -> List.of(t)), List.of(), clock), List.of());

        return CompletableFuture.allOf(kpis, netWorth, breakdown, flow, committed, upcomingPayments, spendByCategory, latestMovements)
                .thenApply(v -> new OverviewBffData(
                        kpis.join(), netWorth.join(), breakdown.join(), flow.join(),
                        committed.join(), upcomingPayments.join(), spendByCategory.join(), latestMovements.join()));
    }

    private <T> CompletableFuture<Section<T>> applyBudget(CompletableFuture<Section<T>> sectionFuture, T fallback) {
        return sectionFuture.completeOnTimeout(
                Section.unavailable(fallback, ObservedAt.now(clock)),
                budget.total().toMillis(),
                TimeUnit.MILLISECONDS);
    }
}
