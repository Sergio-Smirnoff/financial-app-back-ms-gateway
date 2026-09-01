package com.financialapp.gateway.application.bff;

import com.financialapp.gateway.application.bff.impl.GetOverviewBffUseCaseImpl;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.gateway.NotificationsGateway;
import com.financialapp.gateway.domain.model.bff.CurrencySummary;
import com.financialapp.gateway.domain.model.bff.MoneyFigure;
import com.financialapp.gateway.domain.model.bff.OverviewBffData;
import com.financialapp.gateway.domain.model.bff.UpcomingPaymentView;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.SectionStatus;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.model.currency.FxRate;
import com.financialapp.gateway.domain.model.currency.FxRateMode;
import com.financialapp.gateway.infrastructure.gateway.dto.FinanceCurrencyTotals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OverviewBffTest {

    @Mock private FinancesGateway finances;
    @Mock private BanksGateway banks;
    @Mock private InvestmentsGateway investments;
    @Mock private NotificationsGateway notifications;

    private GetOverviewBffUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetOverviewBffUseCaseImpl(
                finances, banks, investments, notifications,
                PageTimeoutBudget.fromMillis(3000));
    }

    @Test
    void execute_returnsOkSectionsWhenAllDownstreamsSucceed() {
        when(finances.fetchSummary(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(investments.fetchPortfolioSummary(any())).thenReturn(CompletableFuture.completedFuture(Map.of("totalMarketValue", 1000)));
        when(finances.fetchMonthlyFlow(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchUpcomingPayments(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchAccounts(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchCards(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchLoans(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(finances.fetchSpendByCategory(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(finances.fetchTransactions(any(), any(Integer.class), any(Integer.class), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(Map.of("content", List.of())));

        OverviewBffData data = useCase.execute(new UserId(1L), CurrencyView.ARS, "none").join();

        assertThat(data.kpis().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.netWorth().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.latestMovements().status()).isEqualTo(SectionStatus.OK);
    }

    @Test
    void execute_degradesSectionToUnavailableWhenDownstreamFails() {
        when(finances.fetchSummary(any(), any(), any())).thenReturn(CompletableFuture.failedFuture(new RuntimeException("Finances down")));
        when(investments.fetchPortfolioSummary(any())).thenReturn(CompletableFuture.completedFuture(Map.of("totalMarketValue", 1000)));
        when(finances.fetchMonthlyFlow(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchUpcomingPayments(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchAccounts(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchCards(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchLoans(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(finances.fetchSpendByCategory(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(finances.fetchTransactions(any(), any(Integer.class), any(Integer.class), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(Map.of("content", List.of())));

        OverviewBffData data = useCase.execute(new UserId(1L), CurrencyView.ARS, "none").join();

        assertThat(data.kpis().status()).isEqualTo(SectionStatus.UNAVAILABLE);
        assertThat(data.netWorth().status()).isEqualTo(SectionStatus.OK);
    }

    @Test
    void kpisCarrySecondaryWhenRateExists() {
        LocalDate today = LocalDate.now();
        when(investments.fetchFxRate(CurrencyView.USD_MEP, today))
                .thenReturn(CompletableFuture.completedFuture(Optional.of(new FxRate(today, FxRateMode.MEP, new BigDecimal("1180"), new BigDecimal("1190")))));
        when(finances.fetchSummary(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of(
                new CurrencySummary("ARS", "11900.00", "0.00", "11900.00")
        )));
        when(investments.fetchPortfolioSummary(any())).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(finances.fetchMonthlyFlow(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchUpcomingPayments(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchAccounts(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchCards(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchLoans(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(finances.fetchSpendByCategory(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(finances.fetchTransactions(any(), any(Integer.class), any(Integer.class), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(Map.of("content", List.of())));

        OverviewBffData data = useCase.execute(new UserId(1L), CurrencyView.USD_MEP, "ARS").join();

        MoneyFigure cash = data.kpis().data().cash();
        assertThat(cash.currency().getCurrencyCode()).isEqualTo("USD");
        assertThat(cash.secondary().currency().getCurrencyCode()).isEqualTo("ARS");
    }

    @Test
    void kpisOmitSecondaryWhenNoRateExists() {
        when(investments.fetchFxRate(any(), any())).thenReturn(CompletableFuture.completedFuture(Optional.empty()));
        when(finances.fetchSummary(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(investments.fetchPortfolioSummary(any())).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(finances.fetchMonthlyFlow(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchUpcomingPayments(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchAccounts(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchCards(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchLoans(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(finances.fetchSpendByCategory(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(finances.fetchTransactions(any(), any(Integer.class), any(Integer.class), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(Map.of("content", List.of())));

        OverviewBffData data = useCase.execute(new UserId(1L), CurrencyView.USD_MEP, "ARS").join();

        assertThat(data.kpis().data().cash().secondary()).isNull();
    }

    @Test
    void kpisCommitAndBreakdownComposeFromBanksAndPortfolio() {
        when(finances.fetchSummary(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(investments.fetchPortfolioSummary(any())).thenReturn(CompletableFuture.completedFuture(Map.of("totalMarketValue", "500000")));
        when(finances.fetchMonthlyFlow(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(banks.fetchUpcomingPayments(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of(
                new UpcomingPaymentView(1L, "LOAN", "Cuota", "25000.00", "ARS", LocalDate.now().plusDays(10), 1, 12, false))));
        when(banks.fetchAccounts(any())).thenReturn(CompletableFuture.completedFuture(List.of(
                Map.of("type", "SAVINGS", "balance", "100000.00"),
                Map.of("type", "CHECKING", "balance", "40000.00"))));
        when(banks.fetchCards(any())).thenReturn(CompletableFuture.completedFuture(List.of(Map.of("usedBalance", "15000.00"))));
        when(banks.fetchLoans(any())).thenReturn(CompletableFuture.completedFuture(List.of(Map.of("id", 1, "name", "Auto"))));
        when(banks.fetchLoanInstallments(any(), eq(1L))).thenReturn(CompletableFuture.completedFuture(List.of(
                Map.of("id", 11, "installmentNumber", 1, "amount", "5000.00", "dueDate", "2026-09-10", "paid", false),
                Map.of("id", 10, "installmentNumber", 0, "amount", "7000.00", "dueDate", "2026-08-10", "paid", true))));
        when(finances.fetchSpendByCategory(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(finances.fetchTransactions(any(), any(Integer.class), any(Integer.class), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(Map.of("content", List.of())));

        OverviewBffData data = useCase.execute(new UserId(1L), CurrencyView.ARS, "none").join();

        assertThat(data.kpis().data().committed().amount()).isEqualByComparingTo("25000.00");
        assertThat(data.breakdown().data().investments().amount()).isEqualByComparingTo("500000");
        assertThat(data.breakdown().data().savings().amount()).isEqualByComparingTo("100000.00");
        assertThat(data.breakdown().data().cash().amount()).isEqualByComparingTo("40000.00");
        assertThat(data.breakdown().data().debt().amount()).isEqualByComparingTo("20000.00");
    }
}
