package com.financialapp.gateway.web.mapper;

import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.dashboard.CurrencySummary;
import com.financialapp.gateway.domain.model.dashboard.DashboardData;
import com.financialapp.gateway.domain.model.dashboard.LoanView;
import com.financialapp.gateway.domain.model.dashboard.UpcomingPaymentView;
import com.financialapp.gateway.web.dto.response.DashboardResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Component
public class DashboardMapper {

    public DashboardResponse toResponse(DashboardData data) {
        return new DashboardResponse(
                section(data.yearToDate(), this::toCurrencySummaries),
                section(data.month(), this::toCurrencySummaries),
                section(data.activeLoans(), this::toLoans),
                section(data.upcomingPayments(), this::toUpcomingPayments));
    }

    private <S, T> DashboardResponse.SectionResponse<T> section(Section<S> in, Function<S, T> map) {
        return new DashboardResponse.SectionResponse<>(
                in.status().name(), map.apply(in.data()), in.observedAt().value());
    }

    private List<DashboardResponse.CurrencySummary> toCurrencySummaries(List<CurrencySummary> in) {
        return in.stream()
                .map(c -> new DashboardResponse.CurrencySummary(
                        c.currency(), c.totalIncome(), c.totalExpense(), c.balance()))
                .toList();
    }

    private List<DashboardResponse.Loan> toLoans(List<LoanView> in) {
        return in.stream()
                .map(l -> new DashboardResponse.Loan(
                        l.id(), l.name(), l.currency(), l.principal(),
                        l.totalInstallments(), l.remainingInstallments(), l.active()))
                .toList();
    }

    private List<DashboardResponse.UpcomingPayment> toUpcomingPayments(List<UpcomingPaymentView> in) {
        return in.stream()
                .map(p -> new DashboardResponse.UpcomingPayment(
                        p.id(), p.type(), p.description(), p.amount(), p.currency(), p.dueDate(),
                        p.installmentNumber(), p.totalInstallments(), p.paid()))
                .toList();
    }
}
