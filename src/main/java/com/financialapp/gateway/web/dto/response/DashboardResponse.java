package com.financialapp.gateway.web.dto.response;

import java.time.LocalDate;
import java.util.List;

/** BFF dashboard payload: finances + banks, money as String. */
public record DashboardResponse(
        List<CurrencySummary> yearToDate,
        List<CurrencySummary> month,
        List<Loan> activeLoans,
        List<UpcomingPayment> upcomingPayments) {

    public record CurrencySummary(String currency, String totalIncome, String totalExpense, String balance) {}

    public record Loan(Long id, String name, String currency, String principal, boolean active) {}

    public record UpcomingPayment(
            Long id, String type, String description, String amount, String currency, LocalDate dueDate) {}
}
