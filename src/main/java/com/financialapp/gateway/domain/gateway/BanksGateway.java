package com.financialapp.gateway.domain.gateway;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.dashboard.LoanView;
import com.financialapp.gateway.domain.model.dashboard.UpcomingPaymentView;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Outbound gateway to ms-banks. Framework-free (CompletableFuture, not Reactor). */
public interface BanksGateway {

    CompletableFuture<List<LoanView>> fetchActiveLoans(UserId userId);

    CompletableFuture<List<UpcomingPaymentView>> fetchUpcomingPayments(UserId userId, LocalDate from, LocalDate to);
}
