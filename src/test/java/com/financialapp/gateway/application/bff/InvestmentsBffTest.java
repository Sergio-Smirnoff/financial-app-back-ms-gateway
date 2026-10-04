package com.financialapp.gateway.application.bff;

import com.financialapp.gateway.application.bff.impl.GetInvestmentsBffUseCaseImpl;
import com.financialapp.gateway.contracts.DownstreamFixtures;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.gateway.NotificationsGateway;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.AlertRow;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.AssetTypeSlice;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.EvolutionPoint;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.InvestmentsKpis;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.MarketQuote;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.MarketQuoteUnit;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.OperationKind;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.OperationRow;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.PositionRow;
import com.financialapp.gateway.domain.model.bff.HistoryRange;
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
import static org.mockito.Mockito.verify;
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
        lenient().when(investments.fetchPortfolioEvolution(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(DownstreamFixtures.list("investments/portfolio-evolution.json")));
        lenient().when(investments.fetchHoldings(any()))
                .thenReturn(CompletableFuture.completedFuture(DownstreamFixtures.list("investments/portfolio-holdings.json")));
        lenient().when(notifications.fetchLatestOfType(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(List.of()));
    }

    private InvestmentsBffData inArs() {
        return useCase.execute(new UserId(1L), CurrencyView.ARS, "none", HistoryRange.ONE_MONTH).join();
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
    void compositionSlicesComeFromEachBucketsBreakdownWithCostPnlAndCount() {
        List<AssetTypeSlice> slices = inArs().composition().data();

        assertThat(slices).extracting(AssetTypeSlice::assetType).containsExactly("BOND", "CEDEAR");
        assertThat(slices).extracting(AssetTypeSlice::label).containsExactly("BOND", "CEDEAR");
        AssetTypeSlice bonds = slices.get(0);
        assertThat(bonds.amount().amount()).isEqualByComparingTo("904779.00");
        assertThat(bonds.cost().amount()).isEqualByComparingTo("986380.86");
        assertThat(bonds.pnl().amount()).isEqualByComparingTo("-81601.86");
        assertThat(bonds.pnlPct()).isEqualByComparingTo("-8.27");
        assertThat(bonds.pct()).isEqualByComparingTo("8.49");
        assertThat(bonds.count()).isEqualTo(1);
        AssetTypeSlice cedears = slices.get(1);
        assertThat(cedears.amount().amount()).isEqualByComparingTo("9757113.60");
        assertThat(cedears.cost().amount()).isEqualByComparingTo("9189497.45");
        assertThat(cedears.pnl().amount()).isEqualByComparingTo("567616.15");
        assertThat(cedears.pnlPct()).isEqualByComparingTo("6.18");
        assertThat(cedears.pct()).isEqualByComparingTo("91.51");
        assertThat(cedears.count()).isEqualTo(5);
    }

    @Test
    void theSlicesAddUpToTheKpis() {
        InvestmentsBffData data = inArs();

        BigDecimal amounts = data.composition().data().stream()
                .map(slice -> slice.amount().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal costs = data.composition().data().stream()
                .map(slice -> slice.cost().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(amounts).isEqualByComparingTo(data.kpis().data().marketValue().amount());
        assertThat(costs).isEqualByComparingTo(data.kpis().data().cost().amount());
    }

    @Test
    void aBreakdownWithoutCostIsUnavailableNotZero() {
        when(investments.fetchPortfolioSummary(any())).thenReturn(CompletableFuture.completedFuture(Map.of(
                "byCurrency", List.of(Map.of(
                        "currency", "ARS", "totalValue", "100", "totalCost", "80",
                        "breakdown", List.of(Map.of("assetType", "STOCK", "totalValue", "100", "count", 1)))))));

        InvestmentsBffData data = inArs();

        assertThat(data.composition().status()).isEqualTo(SectionStatus.UNAVAILABLE);
        assertThat(data.kpis().status()).isEqualTo(SectionStatus.UNAVAILABLE);
        assertThat(data.positions().status()).isEqualTo(SectionStatus.OK);
    }

    @Test
    void aBreakdownWithoutTotalPlIsUnavailableNotDerived() {
        when(investments.fetchPortfolioSummary(any())).thenReturn(CompletableFuture.completedFuture(Map.of(
                "byCurrency", List.of(Map.of(
                        "currency", "ARS", "totalValue", "100", "totalCost", "80",
                        "breakdown", List.of(Map.of("assetType", "STOCK", "totalValue", "100",
                                "totalCost", "80", "count", 1)))))));

        InvestmentsBffData data = inArs();

        assertThat(data.composition().status()).isEqualTo(SectionStatus.UNAVAILABLE);
    }

    @Test
    void aSliceWithZeroCostHasZeroPnlPctAndKeepsItsShare() {
        when(investments.fetchPortfolioSummary(any())).thenReturn(CompletableFuture.completedFuture(Map.of(
                "byCurrency", List.of(Map.of(
                        "currency", "ARS", "totalValue", "1000.00", "totalCost", "600.00", "totalPl", "400.00",
                        "breakdown", List.of(
                                Map.of("assetType", "BOND", "totalValue", "250.00",
                                        "totalCost", "0", "totalPl", "250.00", "count", 1),
                                Map.of("assetType", "STOCK", "totalValue", "750.00",
                                        "totalCost", "600.00", "totalPl", "150.00", "count", 2)))))));

        InvestmentsBffData data = inArs();

        assertThat(data.composition().status()).isEqualTo(SectionStatus.OK);
        List<AssetTypeSlice> slices = data.composition().data();
        assertThat(slices).extracting(AssetTypeSlice::assetType).containsExactly("BOND", "STOCK");
        AssetTypeSlice bonds = slices.get(0);
        assertThat(bonds.cost().amount()).isEqualByComparingTo("0");
        assertThat(bonds.pnl().amount()).isEqualByComparingTo("250.00");
        assertThat(bonds.pnlPct()).isEqualByComparingTo("0");
        assertThat(bonds.pct()).isEqualByComparingTo("25.00");
        assertThat(slices.get(1).pct()).isEqualByComparingTo("75.00");
    }

    @Test
    void positionsCarryTheirAssetType() {
        assertThat(inArs().positions().data().get(0).assetType()).isEqualTo("BOND");
    }

    @Test
    void sameTypeInBothBucketsMergesIntoOneSlice() {
        when(investments.fetchPortfolioSummary(any())).thenReturn(CompletableFuture.completedFuture(Map.of(
                "byCurrency", List.of(
                        Map.of("currency", "ARS", "totalValue", "1000000.00", "totalCost", "800000.00",
                                "totalPl", "200000.00", "plPercent", "25.0000",
                                "breakdown", List.of(Map.of("assetType", "STOCK", "totalValue", "1000000.00",
                                        "totalCost", "800000.00", "totalPl", "200000.00",
                                        "percentage", "100.0000", "count", 2))),
                        Map.of("currency", "USD", "totalValue", "100.00", "totalCost", "120.00",
                                "totalPl", "-20.00", "plPercent", "-16.6700",
                                "breakdown", List.of(Map.of("assetType", "STOCK", "totalValue", "100.00",
                                        "totalCost", "120.00", "totalPl", "-20.00",
                                        "percentage", "100.0000", "count", 1)))))));
        when(investments.fetchFxRate(eq(CurrencyView.USD_MEP), any())).thenReturn(CompletableFuture.completedFuture(MEP));

        List<AssetTypeSlice> slices = inArs().composition().data();

        assertThat(slices).hasSize(1);
        AssetTypeSlice stocks = slices.get(0);
        assertThat(stocks.assetType()).isEqualTo("STOCK");
        assertThat(stocks.amount().amount()).isEqualByComparingTo("1120000.00");
        assertThat(stocks.cost().amount()).isEqualByComparingTo("944000.00");
        assertThat(stocks.pnl().amount()).isEqualByComparingTo("176000.00");
        assertThat(stocks.pnlPct()).isEqualByComparingTo("18.64");
        assertThat(stocks.pct()).isEqualByComparingTo("100.00");
        assertThat(stocks.count()).isEqualTo(3);
    }

    @Test
    void slicePnlIsTheTotalPlOfMsInvestmentsConvertedAndMergedNotValueMinusCost() {
        when(investments.fetchPortfolioSummary(any())).thenReturn(CompletableFuture.completedFuture(Map.of(
                "byCurrency", List.of(
                        Map.of("currency", "ARS", "totalValue", "1000000.00", "totalCost", "800000.00",
                                "totalPl", "199999.99", "plPercent", "25.0000",
                                "breakdown", List.of(Map.of("assetType", "STOCK", "totalValue", "1000000.00",
                                        "totalCost", "800000.00", "totalPl", "199999.99",
                                        "percentage", "100.0000", "count", 2))),
                        Map.of("currency", "USD", "totalValue", "100.00", "totalCost", "120.00",
                                "totalPl", "-20.01", "plPercent", "-16.6700",
                                "breakdown", List.of(Map.of("assetType", "STOCK", "totalValue", "100.00",
                                        "totalCost", "120.00", "totalPl", "-20.01",
                                        "percentage", "100.0000", "count", 1)))))));
        when(investments.fetchFxRate(eq(CurrencyView.USD_MEP), any())).thenReturn(CompletableFuture.completedFuture(MEP));

        AssetTypeSlice inArs = inArs().composition().data().get(0);
        AssetTypeSlice inUsd = useCase.execute(new UserId(1L), CurrencyView.USD_MEP, "ARS", HistoryRange.ONE_MONTH)
                .join().composition().data().get(0);

        assertThat(inArs.amount().amount()).isEqualByComparingTo("1120000.00");
        assertThat(inArs.cost().amount()).isEqualByComparingTo("944000.00");
        assertThat(inArs.pnl().amount()).isEqualByComparingTo("175987.99");
        assertThat(inArs.pnl().currency()).isEqualTo(Currency.ARS);
        assertThat(inArs.pnlPct()).isEqualByComparingTo("18.64");
        assertThat(inArs.count()).isEqualTo(3);
        assertThat(inUsd.pnl().amount()).isEqualByComparingTo("140.79");
        assertThat(inUsd.pnl().currency()).isEqualTo(Currency.USD);
        assertThat(inUsd.pnl().secondary().amount()).isEqualByComparingTo("175987.99");
        assertThat(inUsd.pnl().secondary().currency()).isEqualTo(Currency.ARS);
        assertThat(inUsd.pnlPct()).isEqualByComparingTo("18.64");
    }

    @Test
    void kpiPnlIsTheTotalPlOfMsInvestmentsAndEqualsTheSumOfTheSlicePnls() {
        when(investments.fetchPortfolioSummary(any())).thenReturn(CompletableFuture.completedFuture(Map.of(
                "byCurrency", List.of(
                        Map.of("currency", "ARS", "totalValue", "1000000.00", "totalCost", "800000.00",
                                "totalPl", "199999.99", "plPercent", "25.0000",
                                "breakdown", List.of(Map.of("assetType", "STOCK", "totalValue", "1000000.00",
                                        "totalCost", "800000.00", "totalPl", "199999.99",
                                        "percentage", "100.0000", "count", 2))),
                        Map.of("currency", "USD", "totalValue", "100.00", "totalCost", "120.00",
                                "totalPl", "-20.01", "plPercent", "-16.6700",
                                "breakdown", List.of(Map.of("assetType", "CEDEAR", "totalValue", "100.00",
                                        "totalCost", "120.00", "totalPl", "-20.01",
                                        "percentage", "100.0000", "count", 1)))))));
        when(investments.fetchFxRate(eq(CurrencyView.USD_MEP), any())).thenReturn(CompletableFuture.completedFuture(MEP));

        InvestmentsBffData data = inArs();

        InvestmentsKpis kpis = data.kpis().data();
        BigDecimal slicePnls = data.composition().data().stream()
                .map(slice -> slice.pnl().amount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(kpis.pnl().amount()).isEqualByComparingTo("175987.99");
        assertThat(kpis.pnl().amount()).isEqualByComparingTo(slicePnls);
        assertThat(kpis.pnlPct()).isEqualByComparingTo("18.64");
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
        List<AssetTypeSlice> slices = data.composition().data();
        assertThat(slices).extracting(AssetTypeSlice::assetType).containsExactly("CEDEAR", "STOCK");
        assertThat(slices.get(0).amount().amount()).isEqualByComparingTo("120000.00");
        assertThat(slices.get(0).cost().amount()).isEqualByComparingTo("144000.00");
        assertThat(slices.get(0).pnl().amount()).isEqualByComparingTo("-24000.00");
        assertThat(slices.get(0).pnlPct()).isEqualByComparingTo("-16.67");
        assertThat(slices.get(0).pct()).isEqualByComparingTo("10.71");
        assertThat(slices.get(0).count()).isEqualTo(1);
        assertThat(slices.get(1).cost().amount()).isEqualByComparingTo("800000.00");
        assertThat(slices.get(1).pnlPct()).isEqualByComparingTo("25.00");
        assertThat(slices.get(1).pct()).isEqualByComparingTo("89.29");
        assertThat(slices.get(1).count()).isEqualTo(2);
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

        InvestmentsKpis kpis = useCase.execute(new UserId(1L), CurrencyView.USD_MEP, "none", HistoryRange.ONE_MONTH).join().kpis().data();

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

    @Test
    void evolutionFetchesTheRequestedRange() {
        useCase.execute(new UserId(1L), CurrencyView.ARS, "none", HistoryRange.THREE_MONTHS).join();

        verify(investments).fetchPortfolioEvolution(new UserId(1L), HistoryRange.THREE_MONTHS);
    }
}
