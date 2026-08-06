package com.financialapp.gateway.domain.gateway;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.dashboard.LoanView;
import com.financialapp.gateway.domain.model.dashboard.UpcomingPaymentView;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface BanksGateway {

    CompletableFuture<List<LoanView>> fetchActiveLoans(UserId userId);

    CompletableFuture<List<UpcomingPaymentView>> fetchUpcomingPayments(UserId userId, LocalDate from, LocalDate to);

    CompletableFuture<List<Currency>> accountCurrencies(UserId userId);

    CompletableFuture<List<Map<String, Object>>> fetchAccounts(UserId userId);

    CompletableFuture<List<Map<String, Object>>> fetchCards(UserId userId);

    CompletableFuture<List<Map<String, Object>>> fetchBalanceSnapshots(UserId userId, LocalDate from, LocalDate to);

    CompletableFuture<List<Map<String, Object>>> fetchFees(UserId userId);
}
