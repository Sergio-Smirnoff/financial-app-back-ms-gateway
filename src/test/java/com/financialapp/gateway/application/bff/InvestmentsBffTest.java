package com.financialapp.gateway.application.bff;

import com.financialapp.gateway.application.bff.impl.GetInvestmentsBffUseCaseImpl;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.gateway.NotificationsGateway;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.AlertRow;
import com.financialapp.gateway.domain.model.bff.InvestmentsBffData;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.SectionStatus;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestmentsBffTest {

    @Mock private InvestmentsGateway investments;
    @Mock private NotificationsGateway notifications;

    private GetInvestmentsBffUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetInvestmentsBffUseCaseImpl(investments, notifications, PageTimeoutBudget.fromMillis(3000));
    }

    @Test
    void execute_returnsOkSectionsForInvestments() {
        when(investments.fetchMarketPanel()).thenReturn(CompletableFuture.completedFuture(
                Map.of("quotes", List.of(Map.of("code", "MERVAL", "label", "Merval", "value", "1200", "variation", "1.5", "unit", "PERCENT")))));
        when(investments.fetchPortfolioSummary(any())).thenReturn(CompletableFuture.completedFuture(Map.of("totalMarketValue", 5000)));
        when(investments.fetchPortfolioEvolution(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(investments.fetchHoldings(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(notifications.fetchLatestByCategory(any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));

        InvestmentsBffData data = useCase.execute(new UserId(1L), CurrencyView.ARS, "none").join();

        assertThat(data.marketStrip().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.kpis().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.positions().status()).isEqualTo(SectionStatus.OK);
    }

    @Test
    void marketStripDegradesWhenTheMarketUpstreamHasNoUsableQuotes() {
        when(investments.fetchMarketPanel()).thenReturn(CompletableFuture.completedFuture(
                Map.of("quotes", List.of(Map.of("code", "", "label", "", "value", "0", "variation", "0", "unit", "PERCENT")))));
        when(investments.fetchPortfolioSummary(any())).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(investments.fetchPortfolioEvolution(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(investments.fetchHoldings(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(notifications.fetchLatestByCategory(any(), any())).thenReturn(CompletableFuture.completedFuture(List.of()));

        InvestmentsBffData data = useCase.execute(new UserId(1L), CurrencyView.ARS, "none").join();

        assertThat(data.marketStrip().status()).isEqualTo(SectionStatus.UNAVAILABLE);
        assertThat(data.marketStrip().data()).isEmpty();
    }

    @Test
    void alertsComeFromPortfolioAlertNotifications() {
        when(notifications.fetchLatestByCategory(any(), eq("PORTFOLIO_ALERTS")))
                .thenReturn(CompletableFuture.completedFuture(List.of(Map.of("id", 9L, "title", "YPFD +8%", "message", "...", "createdAt", Instant.now().toString(), "read", false))));
        when(investments.fetchMarketPanel()).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(investments.fetchPortfolioSummary(any())).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(investments.fetchPortfolioEvolution(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(investments.fetchHoldings(any())).thenReturn(CompletableFuture.completedFuture(List.of()));

        InvestmentsBffData data = useCase.execute(new UserId(1L), CurrencyView.ARS, "none").join();

        assertThat(data.alerts().data()).extracting(AlertRow::title).containsExactly("YPFD +8%");
    }
}
