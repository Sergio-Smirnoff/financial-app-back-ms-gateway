package com.financialapp.gateway.web.mapper;

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
    void maps_all_sections_preserving_string_money() {
        var data = new DashboardData(
                List.of(new CurrencySummary("ARS", "1000.00", "400.00", "600.00")),
                List.of(new CurrencySummary("USD", "10.00", "2.00", "8.00")),
                List.of(new LoanView(1L, "Car", "ARS", "50000.00", true)),
                List.of(new UpcomingPaymentView(9L, "LOAN", "Car #3", "1500.00", "ARS", LocalDate.of(2026, 6, 10))));

        DashboardResponse out = mapper.toResponse(data);

        assertThat(out.yearToDate()).containsExactly(
                new DashboardResponse.CurrencySummary("ARS", "1000.00", "400.00", "600.00"));
        assertThat(out.month()).containsExactly(
                new DashboardResponse.CurrencySummary("USD", "10.00", "2.00", "8.00"));
        assertThat(out.activeLoans()).containsExactly(
                new DashboardResponse.Loan(1L, "Car", "ARS", "50000.00", true));
        assertThat(out.upcomingPayments()).containsExactly(
                new DashboardResponse.UpcomingPayment(9L, "LOAN", "Car #3", "1500.00", "ARS", LocalDate.of(2026, 6, 10)));
    }
}
