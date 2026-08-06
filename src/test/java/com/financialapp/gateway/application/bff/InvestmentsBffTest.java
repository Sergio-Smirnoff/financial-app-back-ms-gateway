package com.financialapp.gateway.application.bff;

import com.financialapp.gateway.application.bff.impl.GetInvestmentsBffUseCaseImpl;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.model.bff.InvestmentsBffData;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.SectionStatus;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import org.junit.jupiter.api.BeforeEach;
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
class InvestmentsBffTest {

    @Mock private InvestmentsGateway investments;

    private GetInvestmentsBffUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetInvestmentsBffUseCaseImpl(investments, PageTimeoutBudget.fromMillis(3000));
    }

    @Test
    void execute_returnsOkSectionsForInvestments() {
        when(investments.fetchMarketPanel()).thenReturn(CompletableFuture.completedFuture(Map.of("merval", 1200)));
        when(investments.fetchPortfolioSummary(any())).thenReturn(CompletableFuture.completedFuture(Map.of("total", 5000)));
        when(investments.fetchPortfolioEvolution(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(investments.fetchHoldings(any())).thenReturn(CompletableFuture.completedFuture(List.of()));
        when(investments.fetchBrokerFees(any())).thenReturn(CompletableFuture.completedFuture(List.of()));

        InvestmentsBffData data = useCase.execute(new UserId(1L), CurrencyView.ARS, "none").join();

        assertThat(data.marketStrip().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.kpis().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.positions().status()).isEqualTo(SectionStatus.OK);
    }
}
