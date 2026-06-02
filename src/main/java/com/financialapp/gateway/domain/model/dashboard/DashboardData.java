package com.financialapp.gateway.domain.model.dashboard;

import java.util.List;

/** Composed dashboard read model: finances + banks. No investments/portfolio. */
public record DashboardData(
        List<CurrencySummary> yearToDate,
        List<CurrencySummary> month,
        List<LoanView> activeLoans,
        List<UpcomingPaymentView> upcomingPayments) {}
