package com.financialapp.gateway.web.dto.response;

import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(
        SectionResponse<List<CurrencySummary>> yearToDate,
        SectionResponse<List<CurrencySummary>> month,
        SectionResponse<List<Loan>> activeLoans,
        SectionResponse<List<UpcomingPayment>> upcomingPayments) {

    public record SectionResponse<T>(String status, T items) {}

    public record CurrencySummary(String currency, String totalIncome, String totalExpense, String balance) {}

    public record Loan(
            Long id, String name, String currency, String principal,
            int totalInstallments, int remainingInstallments, boolean active) {}

    public record UpcomingPayment(
            Long id, String type, String description, String amount, String currency, LocalDate dueDate,
            int installmentNumber, int totalInstallments, boolean paid) {}
}
