package com.financialapp.gateway.domain.model.dashboard;

import java.util.List;

public record DashboardData(
        List<CurrencySummary> yearToDate,
        List<CurrencySummary> month,
        List<LoanView> activeLoans,
        List<UpcomingPaymentView> upcomingPayments) {}
