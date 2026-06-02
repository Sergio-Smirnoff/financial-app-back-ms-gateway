package com.financialapp.gateway.web.mapper;

import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.dashboard.CurrencySummary;
import com.financialapp.gateway.domain.model.dashboard.DashboardData;
import com.financialapp.gateway.domain.model.dashboard.LoanView;
import com.financialapp.gateway.domain.model.dashboard.UpcomingPaymentView;
import com.financialapp.gateway.web.dto.response.DashboardResponse;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardMapperTest {

    private final DashboardMapper mapper = new DashboardMapper();

    @Test
    void maps_ok_sections_with_status_and_string_money() {
        var data = new DashboardData(
                Section.ok(List.of(new CurrencySummary("ARS", "1000.00", "400.00", "600.00"))),
                Section.ok(List.of(new CurrencySummary("USD", "10.00", "2.00", "8.00"))),
                Section.ok(List.of(new LoanView(1L, "Car", "ARS", "50000.00", 12, 9, true))),
                Section.ok(List.of(new UpcomingPaymentView(
                        9L, "LOAN", "Car #3", "1500.00", "ARS", LocalDate.of(2026, 6, 10), 3, 12, false))));

        DashboardResponse out = mapper.toResponse(data);

        assertThat(out.yearToDate().status()).isEqualTo("OK");
        assertThat(out.yearToDate().items()).containsExactly(
                new DashboardResponse.CurrencySummary("ARS", "1000.00", "400.00", "600.00"));
        assertThat(out.activeLoans().items()).containsExactly(
                new DashboardResponse.Loan(1L, "Car", "ARS", "50000.00", 12, 9, true));
        assertThat(out.upcomingPayments().items()).containsExactly(
                new DashboardResponse.UpcomingPayment(
                        9L, "LOAN", "Car #3", "1500.00", "ARS", LocalDate.of(2026, 6, 10), 3, 12, false));
    }

    @Test
    void maps_unavailable_section_to_status_and_empty_items() {
        var data = new DashboardData(
                Section.ok(List.of()),
                Section.ok(List.of()),
                Section.unavailable(List.of()),
                Section.ok(List.of()));

        DashboardResponse out = mapper.toResponse(data);

        assertThat(out.activeLoans().status()).isEqualTo("UNAVAILABLE");
        assertThat(out.activeLoans().items()).isEmpty();
    }
}
