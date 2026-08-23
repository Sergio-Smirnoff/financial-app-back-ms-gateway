package com.financialapp.gateway.application.dashboard.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.SectionStatus;
import com.financialapp.gateway.domain.model.bff.CurrencySummary;
import com.financialapp.gateway.domain.model.dashboard.DashboardData;
import com.financialapp.gateway.domain.model.bff.LoanView;
import com.financialapp.gateway.domain.model.bff.UpcomingPaymentView;
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
    private final GetDashboardDataImpl useCase = new GetDashboardDataImpl(
            finances, banks, PageTimeoutBudget.fromMillis(5000));

    private final UserId user = new UserId(7L);
    private final LocalDate yearFrom = LocalDate.of(2026, 1, 1);
    private final LocalDate yearTo = LocalDate.of(2026, 12, 31);
    private final LocalDate monthFrom = LocalDate.of(2026, 6, 1);
    private final LocalDate monthTo = LocalDate.of(2026, 6, 30);

    @Test
    void composes_all_sections_ok_when_every_call_succeeds() {
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

        assertThat(result.yearToDate().status()).isEqualTo(SectionStatus.OK);
        assertThat(result.yearToDate().data()).isEqualTo(ytd);
        assertThat(result.month().data()).isEqualTo(month);
        assertThat(result.activeLoans().data()).isEqualTo(loans);
        assertThat(result.upcomingPayments().data()).isEqualTo(payments);
    }

    @Test
    void degrades_only_the_failing_section_and_still_completes() {
        when(finances.fetchSummary(eq(user), eq(yearFrom), eq(yearTo)))
                .thenReturn(CompletableFuture.completedFuture(
                        List.of(new CurrencySummary("ARS", "1.00", "0.00", "1.00"))));
        when(finances.fetchSummary(eq(user), eq(monthFrom), eq(monthTo)))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchActiveLoans(any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("banks down")));
        when(banks.fetchUpcomingPayments(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        DashboardData result = useCase.execute(user, yearFrom, yearTo, monthFrom, monthTo).join();

        assertThat(result.yearToDate().status()).isEqualTo(SectionStatus.OK);
        assertThat(result.activeLoans().status()).isEqualTo(SectionStatus.UNAVAILABLE);
        assertThat(result.activeLoans().data()).isEmpty();
        assertThat(result.upcomingPayments().status()).isEqualTo(SectionStatus.OK);
    }

    @Test
    void section_that_times_out_past_budget_resolves_unavailable() {
        GetDashboardDataImpl shortBudgetUseCase = new GetDashboardDataImpl(
                finances, banks, PageTimeoutBudget.fromMillis(100));

        when(finances.fetchSummary(eq(user), eq(yearFrom), eq(yearTo)))
                .thenReturn(CompletableFuture.completedFuture(
                        List.of(new CurrencySummary("ARS", "1.00", "0.00", "1.00"))));
        when(finances.fetchSummary(eq(user), eq(monthFrom), eq(monthTo)))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchActiveLoans(any()))
                .thenReturn(new CompletableFuture<>()); // Never completes
        when(banks.fetchUpcomingPayments(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));

        DashboardData result = shortBudgetUseCase.execute(user, yearFrom, yearTo, monthFrom, monthTo).join();

        assertThat(result.yearToDate().status()).isEqualTo(SectionStatus.OK);
        assertThat(result.activeLoans().status()).isEqualTo(SectionStatus.UNAVAILABLE);
        assertThat(result.activeLoans().data()).isEmpty();
    }
}
