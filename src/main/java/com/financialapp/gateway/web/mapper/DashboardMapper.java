package com.financialapp.gateway.web.mapper;

import com.financialapp.gateway.domain.model.dashboard.DashboardData;
import com.financialapp.gateway.web.dto.response.DashboardResponse;
import org.springframework.stereotype.Component;

@Component
public class DashboardMapper {

    public DashboardResponse toResponse(DashboardData data) {
        return new DashboardResponse(
                data.yearToDate().stream()
                        .map(c -> new DashboardResponse.CurrencySummary(
                                c.currency(), c.totalIncome(), c.totalExpense(), c.balance()))
                        .toList(),
                data.month().stream()
                        .map(c -> new DashboardResponse.CurrencySummary(
                                c.currency(), c.totalIncome(), c.totalExpense(), c.balance()))
                        .toList(),
                data.activeLoans().stream()
                        .map(l -> new DashboardResponse.Loan(
                                l.id(), l.name(), l.currency(), l.principal(), l.active()))
                        .toList(),
                data.upcomingPayments().stream()
                        .map(p -> new DashboardResponse.UpcomingPayment(
                                p.id(), p.type(), p.description(), p.amount(), p.currency(), p.dueDate()))
                        .toList());
    }
}
