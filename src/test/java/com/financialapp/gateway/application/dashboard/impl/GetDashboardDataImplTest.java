package com.financialapp.gateway.application.dashboard.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.model.dashboard.CurrencySummary;
import com.financialapp.gateway.domain.model.dashboard.DashboardData;
import com.financialapp.gateway.domain.model.dashboard.LoanView;
import com.financialapp.gateway.domain.model.dashboard.UpcomingPaymentView;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetDashboardDataImplTest {

    private final FinancesGateway finances = mock(FinancesGateway.class);
    private final BanksGateway banks = mock(BanksGateway.class);
    private final GetDashboardDataImpl useCase = new GetDashboardDataImpl(finances, banks);

    private final UserId user = new UserId(7L);
    private final LocalDate yearFrom = LocalDate.of(2026, 1, 1);
    private final LocalDate yearTo = LocalDate.of(2026, 12, 31);
    private final LocalDate monthFrom = LocalDate.of(2026, 6, 1);
    private final LocalDate monthTo = LocalDate.of(2026, 6, 30);

    @Test
    void composes_finances_and_banks_into_dashboard_data() {
        var ytd = List.of(new CurrencySummary("ARS", "1000.00", "400.00", "600.00"));
        var month = List.of(new CurrencySummary("ARS", "200.00", "50.00", "150.00"));
        var loans = List.of(new LoanView(1L, "Car", "ARS", "50000.00", 12, 9, true));
        var payments = List.of(new UpcomingPaymentView(
                9L, "LOAN", "Car #3", "1500.00", "ARS", LocalDate.of(2026, 6, 10), 3, 12, false));

        when(finances.fetchSummary(eq(user), eq(yearFrom), eq(yearTo)))
                .thenReturn(CompletableFuture.completedFuture(ytd));
        when(finances.fetchSummary(eq(user), eq(monthFrom), eq(monthTo)))
                .thenReturn(CompletableFuture.completedFuture(month));
        when(banks.fetchActiveLoans(eq(user)))
                .thenReturn(CompletableFuture.completedFuture(loans));
        when(banks.fetchUpcomingPayments(eq(user), eq(monthFrom), eq(monthTo)))
                .thenReturn(CompletableFuture.completedFuture(payments));

        DashboardData result = useCase.execute(user, yearFrom, yearTo, monthFrom, monthTo).join();

        assertThat(result.yearToDate()).isEqualTo(ytd);
        assertThat(result.month()).isEqualTo(month);
        assertThat(result.activeLoans()).isEqualTo(loans);
        assertThat(result.upcomingPayments()).isEqualTo(payments);
    }

    @Test
    void propagates_gateway_failure() {
        when(finances.fetchSummary(eq(user), eq(yearFrom), eq(yearTo)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("boom")));
        when(finances.fetchSummary(eq(user), eq(monthFrom), eq(monthTo)))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchActiveLoans(any()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchUpcomingPayments(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        CompletableFuture<DashboardData> future =
                useCase.execute(user, yearFrom, yearTo, monthFrom, monthTo);

        assertThat(future).isCompletedExceptionally();
    }
}
