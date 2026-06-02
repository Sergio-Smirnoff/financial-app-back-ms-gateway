package com.financialapp.gateway.domain.gateway;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.dashboard.CurrencySummary;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface FinancesGateway {

    /** Per-currency income/expense/balance totals for [from, to]. */
    CompletableFuture<List<CurrencySummary>> fetchSummary(UserId userId, LocalDate from, LocalDate to);
}
