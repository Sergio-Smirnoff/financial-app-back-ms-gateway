package com.financialapp.gateway.web.mapper;

import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.dashboard.CurrencySummary;
import com.financialapp.gateway.domain.model.dashboard.DashboardData;
import com.financialapp.gateway.domain.model.dashboard.LoanView;
import com.financialapp.gateway.domain.model.dashboard.UpcomingPaymentView;
import com.financialapp.gateway.web.dto.response.DashboardResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardMapperTest {

    private final DashboardMapper mapper = new DashboardMapper();
    private final ObservedAt stamp = new ObservedAt(Instant.parse("2026-07-30T12:00:00Z"));

    @Test
    void maps_ok_sections_with_status_string_money_and_observed_at() {
        var data = new DashboardData(
                Section.ok(List.of(new CurrencySummary("ARS", "1000.00", "400.00", "600.00")), stamp),
                Section.ok(List.of(new CurrencySummary("USD", "10.00", "2.00", "8.00")), stamp),
                Section.ok(List.of(new LoanView(1L, "Car", "ARS", "50000.00", 12, 9, true)), stamp),
                Section.ok(List.of(new UpcomingPaymentView(
                        9L, "LOAN", "Car #3", "1500.00", "ARS", LocalDate.of(2026, 6, 10), 3, 12, false)), stamp));

        DashboardResponse out = mapper.toResponse(data);

        assertThat(out.yearToDate().status()).isEqualTo("OK");
        assertThat(out.yearToDate().observedAt()).isEqualTo(stamp.value());
        assertThat(out.yearToDate().items()).containsExactly(
                new DashboardResponse.CurrencySummary("ARS", "1000.00", "400.00", "600.00"));
        assertThat(out.activeLoans().items()).containsExactly(
                new DashboardResponse.Loan(1L, "Car", "ARS", "50000.00", 12, 9, true));
        assertThat(out.upcomingPayments().items()).containsExactly(
                new DashboardResponse.UpcomingPayment(
                        9L, "LOAN", "Car #3", "1500.00", "ARS", LocalDate.of(2026, 6, 10), 3, 12, false));
    }

    @Test
    void maps_unavailable_section_to_status_empty_items_and_observed_at() {
        var data = new DashboardData(
                Section.ok(List.of(), stamp),
                Section.ok(List.of(), stamp),
                Section.unavailable(List.of(), stamp),
                Section.ok(List.of(), stamp));

        DashboardResponse out = mapper.toResponse(data);

        assertThat(out.activeLoans().status()).isEqualTo("UNAVAILABLE");
        assertThat(out.activeLoans().observedAt()).isEqualTo(stamp.value());
        assertThat(out.activeLoans().items()).isEmpty();
    }
}
