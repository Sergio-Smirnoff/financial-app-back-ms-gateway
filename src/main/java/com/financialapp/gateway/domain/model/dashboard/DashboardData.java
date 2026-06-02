package com.financialapp.gateway.domain.model.dashboard;

import com.financialapp.gateway.domain.model.composition.Section;

import java.util.List;

public record DashboardData(
        Section<List<CurrencySummary>> yearToDate,
        Section<List<CurrencySummary>> month,
        Section<List<LoanView>> activeLoans,
        Section<List<UpcomingPaymentView>> upcomingPayments) {}
