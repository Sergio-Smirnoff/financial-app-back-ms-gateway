package com.financialapp.gateway.application.dashboard.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.dashboard.CurrencySummary;
import com.financialapp.gateway.domain.model.dashboard.DashboardData;
import com.financialapp.gateway.domain.model.dashboard.LoanView;
import com.financialapp.gateway.domain.model.dashboard.UpcomingPaymentView;
import com.financialapp.gateway.domain.usecase.dashboard.GetDashboardData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GetDashboardDataImpl implements GetDashboardData {

    private final FinancesGateway finances;
    private final BanksGateway banks;
    private final PageTimeoutBudget budget;
    private final Clock clock;

    @Autowired
    public GetDashboardDataImpl(FinancesGateway finances, BanksGateway banks, PageTimeoutBudget budget) {
        this(finances, banks, budget, Clock.systemUTC());
    }

    public GetDashboardDataImpl(FinancesGateway finances, BanksGateway banks, PageTimeoutBudget budget, Clock clock) {
        this.finances = finances;
        this.banks = banks;
        this.budget = budget != null ? budget : PageTimeoutBudget.fromMillis(5000);
        this.clock = clock;
    }

    @Override
    public CompletableFuture<DashboardData> execute(
            UserId userId,
            LocalDate yearFrom, LocalDate yearTo,
            LocalDate monthFrom, LocalDate monthTo) {

        CompletableFuture<Section<List<CurrencySummary>>> ytd =
                applyBudget(Section.guard(finances.fetchSummary(userId, yearFrom, yearTo), List.of(), clock), List.of());
        CompletableFuture<Section<List<CurrencySummary>>> month =
                applyBudget(Section.guard(finances.fetchSummary(userId, monthFrom, monthTo), List.of(), clock), List.of());
        CompletableFuture<Section<List<LoanView>>> loans =
                applyBudget(Section.guard(banks.fetchActiveLoans(userId), List.of(), clock), List.of());
        CompletableFuture<Section<List<UpcomingPaymentView>>> payments =
                applyBudget(Section.guard(banks.fetchUpcomingPayments(userId, monthFrom, monthTo), List.of(), clock), List.of());

        return CompletableFuture.allOf(ytd, month, loans, payments)
                .thenApply(ignored -> new DashboardData(
                        ytd.join(), month.join(), loans.join(), payments.join()));
    }

    private <T> CompletableFuture<Section<T>> applyBudget(CompletableFuture<Section<T>> sectionFuture, T fallback) {
        return sectionFuture.completeOnTimeout(
                Section.unavailable(fallback, ObservedAt.now(clock)),
                budget.total().toMillis(),
                TimeUnit.MILLISECONDS);
    }
}
