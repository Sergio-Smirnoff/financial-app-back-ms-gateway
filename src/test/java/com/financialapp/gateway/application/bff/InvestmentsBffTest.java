package com.financialapp.gateway.application.bff;

import com.financialapp.gateway.application.bff.impl.GetInvestmentsBffUseCaseImpl;
import com.financialapp.gateway.contracts.DownstreamFixtures;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.gateway.NotificationsGateway;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.AlertRow;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.CompositionSlice;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.EvolutionPoint;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.InvestmentsKpis;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.MarketQuote;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.MarketQuoteUnit;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.OperationKind;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.OperationRow;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.PositionRow;
import com.financialapp.gateway.domain.model.bff.InvestmentsBffData;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.SectionStatus;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.model.currency.FxRate;
import com.financialapp.gateway.domain.model.currency.FxRateMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvestmentsBffTest {

    private static final Optional<FxRate> MEP = Optional.of(new FxRate(
            LocalDate.of(2026, 9, 28), FxRateMode.MEP, new BigDecimal("1200"), new BigDecimal("1250")));

    @Mock private InvestmentsGateway investments;
    @Mock private NotificationsGateway notifications;

    private GetInvestmentsBffUseCaseImpl useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetInvestmentsBffUseCaseImpl(investments, notifications, PageTimeoutBudget.fromMillis(3000));
        lenient().when(investments.fetchMarketPanel())
                .thenReturn(CompletableFuture.completedFuture(Map.of("indices", List.of())));
        lenient().when(investments.fetchPortfolioSummary(any()))
                .thenReturn(CompletableFuture.completedFuture(DownstreamFixtures.object("investments/portfolio-summary.json")));
        lenient().when(investments.fetchPortfolioEvolution(any()))
                .thenReturn(CompletableFuture.completedFuture(DownstreamFixtures.list("investments/portfolio-evolution.json")));
        lenient().when(investments.fetchHoldings(any()))
                .thenReturn(CompletableFuture.completedFuture(DownstreamFixtures.list("investments/portfolio-holdings.json")));
        lenient().when(notifications.fetchLatestOfType(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
    }

    private InvestmentsBffData inArs() {
        return useCase.execute(new UserId(1L), CurrencyView.ARS, "none").join();
    }

    @Test
    void execute_returnsOkSectionsForInvestments() {
        when(investments.fetchMarketPanel()).thenReturn(CompletableFuture.completedFuture(
                Map.of("indices", List.of(
                        Map.of("code", "MERVAL", "value", "2140500.25", "variation", "1.5"),
                        Map.of("code", "RIESGO_PAIS", "value", "745", "variation", "-12")))));

        InvestmentsBffData data = inArs();

        assertThat(data.marketStrip().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.marketStrip().data()).extracting(MarketQuote::code, MarketQuote::label, MarketQuote::unit)
                .containsExactly(
                        tuple("MERVAL", "Merval", MarketQuoteUnit.PERCENT),
                        tuple("RIESGO_PAIS", "Riesgo país", MarketQuoteUnit.POINTS));
        assertThat(data.kpis().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.evolution().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.positions().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.composition().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.recentOperations().status()).isEqualTo(SectionStatus.OK);
        assertThat(data.alerts().status()).isEqualTo(SectionStatus.OK);
    }

    @Test
    void marketStripDegradesWhenTheMarketUpstreamHasNoUsableQuotes() {
        when(investments.fetchMarketPanel()).thenReturn(CompletableFuture.completedFuture(
                Map.of("quotes", List.of(Map.of("ticker", "ALUA", "price", "848.50", "variation", "0")),
                       "indices", List.of(Map.of("code", "", "value", "0", "variation", "0")))));

        InvestmentsBffData data = inArs();

        assertThat(data.marketStrip().status()).isEqualTo(SectionStatus.UNAVAILABLE);
        assertThat(data.marketStrip().data()).isEmpty();
    }

    @Test
    void kpisReadTheArsBucketOfTheRealSummaryContract() {
        InvestmentsKpis kpis = inArs().kpis().data();

        assertThat(kpis.marketValue().amount()).isEqualByComparingTo("10661892.60");
        assertThat(kpis.marketValue().currency()).isEqualTo(Currency.ARS);
        assertThat(kpis.cost().amount()).isEqualByComparingTo("10175878.31");
        assertThat(kpis.pnl().amount()).isEqualByComparingTo("486014.29");
        assertThat(kpis.pnlPct()).isEqualByComparingTo("4.78");
    }

    @Test
    void compositionSlicesComeFromEachBucketsBreakdown() {
        List<CompositionSlice> slices = inArs().composition().data();

        assertThat(slices).extracting(CompositionSlice::label).containsExactly("BOND", "CEDEAR");
        assertThat(slices.get(0).amount().amount()).isEqualByComparingTo("904779.00");
        assertThat(slices.get(0).pct()).isEqualByComparingTo("8.49");
        assertThat(slices.get(1).amount().amount()).isEqualByComparingTo("9757113.60");
        assertThat(slices.get(1).pct()).isEqualByComparingTo("91.51");
    }

    @Test
    void usdBucketsConvertAtMepInTheArsView() {
        when(investments.fetchPortfolioSummary(any())).thenReturn(CompletableFuture.completedFuture(
                DownstreamFixtures.object("investments/portfolio-summary-ars-usd.json")));
        when(investments.fetchFxRate(eq(CurrencyView.USD_MEP), any())).thenReturn(CompletableFuture.completedFuture(MEP));

        InvestmentsBffData data = inArs();

        InvestmentsKpis kpis = data.kpis().data();
        assertThat(kpis.marketValue().amount()).isEqualByComparingTo("1120000.00");
        assertThat(kpis.cost().amount()).isEqualByComparingTo("944000.00");
        assertThat(kpis.pnl().amount()).isEqualByComparingTo("176000.00");
        assertThat(kpis.pnlPct()).isEqualByComparingTo("18.64");
        List<CompositionSlice> slices = data.composition().data();
        assertThat(slices).extracting(CompositionSlice::label).containsExactly("CEDEAR", "STOCK");
        assertThat(slices.get(0).amount().amount()).isEqualByComparingTo("120000.00");
        assertThat(slices.get(0).pct()).isEqualByComparingTo("10.71");
        assertThat(slices.get(1).pct()).isEqualByComparingTo("89.29");
    }

    @Test
    void aUsdBucketWithoutARateIsUnavailableInsteadOfSummedAsPesos() {
        when(investments.fetchPortfolioSummary(any())).thenReturn(CompletableFuture.completedFuture(
                DownstreamFixtures.object("investments/portfolio-summary-ars-usd.json")));
        when(investments.fetchFxRate(eq(CurrencyView.USD_MEP), any()))
                .thenReturn(CompletableFuture.completedFuture(Optional.empty()));

        InvestmentsBffData data = inArs();

        assertThat(data.kpis().status()).isEqualTo(SectionStatus.UNAVAILABLE);
        assertThat(data.composition().status()).isEqualTo(SectionStatus.UNAVAILABLE);
        assertThat(data.positions().status()).isEqualTo(SectionStatus.OK);
    }

    @Test
    void theOldFlatSummaryShapeIsUnavailableNotZero() {
        when(investments.fetchPortfolioSummary(any()))
                .thenReturn(CompletableFuture.completedFuture(Map.of("totalMarketValue", 5000)));

        InvestmentsBffData data = inArs();

        assertThat(data.kpis().status()).isEqualTo(SectionStatus.UNAVAILABLE);
        assertThat(data.composition().status()).isEqualTo(SectionStatus.UNAVAILABLE);
    }

    @Test
    void theUsdViewConvertsTheTotalsAtTheViewRate() {
        when(investments.fetchFxRate(eq(CurrencyView.USD_MEP), any())).thenReturn(CompletableFuture.completedFuture(MEP));

        InvestmentsKpis kpis = useCase.execute(new UserId(1L), CurrencyView.USD_MEP, "none").join().kpis().data();

        assertThat(kpis.marketValue().currency()).isEqualTo(Currency.USD);
        assertThat(kpis.marketValue().amount()).isEqualByComparingTo("8529.51");
    }

    @Test
    void evolutionReadsTheDailyTotalsAndHasNoCostSource() {
        List<EvolutionPoint> points = inArs().evolution().data();

        assertThat(points).extracting(EvolutionPoint::date)
                .containsExactly(LocalDate.of(2026, 9, 26), LocalDate.of(2026, 9, 27));
        assertThat(points.get(1).marketValue().amount()).isEqualByComparingTo("10661892.60");
        assertThat(points.get(1).cost()).isNull();
    }

    @Test
    void positionsCarryTheBondValueSentByMsInvestments() {
        PositionRow row = inArs().positions().data().get(0);

        assertThat(row.holdingId()).isEqualTo(7L);
        assertThat(row.ticker()).isEqualTo("AO29");
        assertThat(row.quantity()).isEqualByComparingTo("687");
        assertThat(row.marketValue().amount()).isEqualByComparingTo("904779.00");
        assertThat(row.pnl().amount()).isEqualByComparingTo("-81601.86");
        assertThat(row.bankNumber()).isEqualTo("017");
    }

    @Test
    void operationsReadTheLocalDateTimeCreatedAt() {
        OperationRow operation = inArs().recentOperations().data().get(0);

        assertThat(operation.date()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(operation.kind()).isEqualTo(OperationKind.BUY);
    }

    @Test
    void alertsComeFromInvestmentThresholdNotifications() {
        when(notifications.fetchLatestOfType(any(), eq("INVESTMENT_THRESHOLD"))).thenReturn(CompletableFuture.completedFuture(
                List.of(DownstreamFixtures.list("notifications/latest.json").get(0))));

        List<AlertRow> alerts = inArs().alerts().data();

        assertThat(alerts).extracting(AlertRow::title).containsExactly("YPFD +8%");
        assertThat(alerts.get(0).createdAt()).isEqualTo(Instant.parse("2026-09-28T12:30:00Z"));
        assertThat(alerts.get(0).read()).isFalse();
    }
}
