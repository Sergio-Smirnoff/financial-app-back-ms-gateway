package com.financialapp.gateway.application.dashboard.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.dashboard.CurrencySummary;
import com.financialapp.gateway.domain.model.dashboard.DashboardData;
import com.financialapp.gateway.domain.model.dashboard.LoanView;
import com.financialapp.gateway.domain.model.dashboard.UpcomingPaymentView;
import com.financialapp.gateway.domain.usecase.dashboard.GetDashboardData;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class GetDashboardDataImpl implements GetDashboardData {

    private final FinancesGateway finances;
    private final BanksGateway banks;

    public GetDashboardDataImpl(FinancesGateway finances, BanksGateway banks) {
        this.finances = finances;
        this.banks = banks;
    }

    @Override
    public CompletableFuture<DashboardData> execute(
            UserId userId,
            LocalDate yearFrom, LocalDate yearTo,
            LocalDate monthFrom, LocalDate monthTo) {

        CompletableFuture<Section<List<CurrencySummary>>> ytd =
                Section.guard(finances.fetchSummary(userId, yearFrom, yearTo), List.of());
        CompletableFuture<Section<List<CurrencySummary>>> month =
                Section.guard(finances.fetchSummary(userId, monthFrom, monthTo), List.of());
        CompletableFuture<Section<List<LoanView>>> loans =
                Section.guard(banks.fetchActiveLoans(userId), List.of());
        CompletableFuture<Section<List<UpcomingPaymentView>>> payments =
                Section.guard(banks.fetchUpcomingPayments(userId, monthFrom, monthTo), List.of());

        return CompletableFuture.allOf(ytd, month, loans, payments)
                .thenApply(ignored -> new DashboardData(
                        ytd.join(), month.join(), loans.join(), payments.join()));
    }
}
