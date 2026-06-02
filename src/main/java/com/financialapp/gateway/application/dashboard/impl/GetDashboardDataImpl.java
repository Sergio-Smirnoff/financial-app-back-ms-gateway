package com.financialapp.gateway.application.dashboard.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.model.dashboard.DashboardData;
import com.financialapp.gateway.domain.usecase.dashboard.GetDashboardData;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
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

        var ytd = finances.fetchSummary(userId, yearFrom, yearTo);
        var month = finances.fetchSummary(userId, monthFrom, monthTo);
        var loans = banks.fetchActiveLoans(userId);
        var payments = banks.fetchUpcomingPayments(userId, monthFrom, monthTo);

        return CompletableFuture.allOf(ytd, month, loans, payments)
                .thenApply(ignored -> new DashboardData(
                        ytd.join(), month.join(), loans.join(), payments.join()));
    }
}
