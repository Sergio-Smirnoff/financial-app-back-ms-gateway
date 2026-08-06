package com.financialapp.gateway.resilience;

import com.financialapp.gateway.application.bff.impl.GetOverviewBffUseCaseImpl;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.gateway.NotificationsGateway;
import com.financialapp.gateway.domain.model.bff.OverviewBffData;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.SectionStatus;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResilienceTest {

    @Mock private FinancesGateway finances;
    @Mock private BanksGateway banks;
    @Mock private InvestmentsGateway investments;
    @Mock private NotificationsGateway notifications;

    @Test
    void perPageTimeoutBudget_marksSlowSectionUnavailable() {
        GetOverviewBffUseCaseImpl useCase = new GetOverviewBffUseCaseImpl(
                finances, banks, investments, notifications,
                PageTimeoutBudget.fromMillis(50)); // Very short budget for test

        CompletableFuture<List<Map<String, Object>>> slowFuture = new CompletableFuture<>(); // never completes
        when(finances.fetchSummary(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(investments.fetchPortfolioSummary(any())).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(banks.fetchBalanceSnapshots(any(), any(), any())).thenReturn(slowFuture);
        when(banks.fetchUpcomingPayments(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(finances.fetchSpendByCategory(any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(finances.fetchTransactions(any(), any(Integer.class), any(Integer.class), any(), any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(Map.of()));

        OverviewBffData data = useCase.execute(new UserId(1L), CurrencyView.ARS, "none").join();

        assertThat(data.flow().status()).isEqualTo(SectionStatus.UNAVAILABLE);
        assertThat(data.netWorth().status()).isEqualTo(SectionStatus.OK);
    }
}
