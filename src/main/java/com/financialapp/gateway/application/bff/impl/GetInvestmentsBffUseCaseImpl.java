package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.gateway.NotificationsGateway;
import com.financialapp.gateway.domain.model.bff.AssetTypeTotals;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.bff.InvestmentsBffData;
import com.financialapp.gateway.domain.model.bff.PortfolioSummary;
import com.financialapp.gateway.domain.model.bff.PortfolioValuePoint;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.model.currency.FxRate;
import com.financialapp.gateway.domain.service.BffMoneyConverter;
import com.financialapp.gateway.domain.service.Percentages;
import com.financialapp.gateway.domain.usecase.bff.GetInvestmentsBffUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GetInvestmentsBffUseCaseImpl implements GetInvestmentsBffUseCase {

    private static final String MARKET_SOURCE = "ms-investments market panel";
    private static final String HOLDINGS_SOURCE = "ms-investments portfolio holdings";
    private static final String ALERTS_SOURCE = "ms-notifications latest";
    private static final String THRESHOLD_ALERT_TYPE = "INVESTMENT_THRESHOLD";
    private static final int RECENT_OPERATIONS = 10;

    private final InvestmentsGateway investments;
    private final NotificationsGateway notifications;
    private final PageTimeoutBudget budget;
    private final Clock clock;

    @Autowired
    public GetInvestmentsBffUseCaseImpl(
            InvestmentsGateway investments, NotificationsGateway notifications, PageTimeoutBudget budget) {
        this(investments, notifications, budget, Clock.systemUTC());
    }

    public GetInvestmentsBffUseCaseImpl(
            InvestmentsGateway investments, NotificationsGateway notifications,
            PageTimeoutBudget budget, Clock clock) {
        this.investments = investments;
        this.notifications = notifications;
        this.budget = budget != null ? budget : PageTimeoutBudget.fromMillis(5000);
        this.clock = clock;
    }

    @Override
    public CompletableFuture<InvestmentsBffData> execute(UserId userId, CurrencyView currencyView, String secondary) {
        LocalDate today = LocalDate.now(clock);

        CompletableFuture<Optional<FxRate>> fxRateFuture = currencyView != CurrencyView.ARS ?
                investments.fetchFxRate(currencyView, today) : CompletableFuture.completedFuture(Optional.empty());

        CompletableFuture<PortfolioSummary> summaryFuture = investments.fetchPortfolioSummary(userId)
                .thenApply(PortfolioFigures::summary);
        CompletableFuture<Optional<FxRate>> summaryRateFuture = summaryFuture.thenCompose(summary ->
                PortfolioFigures.usdRate(summary.needsUsdRate(), currencyView, fxRateFuture, investments, today));
        CompletableFuture<List<PortfolioValuePoint>> evolutionFuture = investments.fetchPortfolioEvolution(userId)
                .thenApply(PortfolioFigures::evolution);
        CompletableFuture<Optional<FxRate>> evolutionRateFuture = evolutionFuture.thenCompose(points ->
                PortfolioFigures.usdRate(points.stream().anyMatch(point -> point.marketValue().needsUsdRate()),
                        currencyView, fxRateFuture, investments, today));
        CompletableFuture<List<DownstreamPayload>> holdingsFuture = investments.fetchHoldings(userId)
                .thenApply(rows -> DownstreamPayload.rows(HOLDINGS_SOURCE, rows));

        CompletableFuture<Section<List<MarketQuote>>> marketStripSec = applyBudget(
                investments.fetchMarketPanel()
                        .thenApply(panel -> {
                            Instant observed = clock.instant();
                            return new DownstreamPayload(MARKET_SOURCE, panel).list("indices").stream()
                                    .filter(index -> !index.textOr("code", "").isBlank())
                                    .map(index -> {
                                        String code = index.text("code");
                                        return new MarketQuote(code, indexLabel(code),
                                                index.decimalOrZero("value"), index.decimalOrZero("variation"),
                                                indexUnit(code), observed);
                                    })
                                    .toList();
                        })
                        .handle((quotes, ex) -> {
                            ObservedAt stamp = ObservedAt.now(clock);
                            return ex == null && !quotes.isEmpty()
                                    ? Section.ok(quotes, stamp)
                                    : Section.unavailable(List.<MarketQuote>of(), stamp);
                        }),
                List.of());

        CompletableFuture<Section<InvestmentsKpis>> kpisSec = applyBudget(
                Section.guard(
                        summaryFuture.thenCombine(summaryRateFuture, (summary, rate) -> {
                            BigDecimal marketValue = BffMoneyConverter.toArs(summary.marketValue(), rate);
                            BigDecimal cost = BffMoneyConverter.toArs(summary.cost(), rate);
                            BigDecimal pnl = marketValue.subtract(cost);
                            return new InvestmentsKpis(
                                    BffMoneyConverter.convert(marketValue, Currency.ARS, currencyView, secondary, rate),
                                    BffMoneyConverter.convert(cost, Currency.ARS, currencyView, secondary, rate),
                                    BffMoneyConverter.convert(pnl, Currency.ARS, currencyView, secondary, rate),
                                    Percentages.percentOf(pnl, cost));
                        }),
                        InvestmentsKpis.empty(), clock),
                InvestmentsKpis.empty());

        CompletableFuture<Section<List<EvolutionPoint>>> evolutionSec = applyBudget(
                Section.guard(
                        evolutionFuture.thenCombine(evolutionRateFuture, (points, rate) -> points.stream()
                                .map(point -> new EvolutionPoint(point.date(),
                                        BffMoneyConverter.convert(BffMoneyConverter.toArs(point.marketValue(), rate),
                                                Currency.ARS, currencyView, secondary, rate),
                                        null))
                                .toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<PositionRow>>> positionsSec = applyBudget(
                Section.guard(
                        holdingsFuture.thenCombine(fxRateFuture, (holdings, fx) -> holdings.stream().map(holding -> {
                            Currency currency = Currency.of(holding.text("currency"));
                            return new PositionRow(
                                    holding.longValue("id"), holding.text("ticker"), holding.text("name"),
                                    holding.decimal("quantity"),
                                    BffMoneyConverter.convert(holding.decimal("avgPurchasePrice"), currency, currencyView, secondary, fx),
                                    BffMoneyConverter.convert(holding.decimal("currentPrice"), currency, currencyView, secondary, fx),
                                    BffMoneyConverter.convert(holding.decimal("currentValue"), currency, currencyView, secondary, fx),
                                    BffMoneyConverter.convert(holding.decimal("plAmount"), currency, currencyView, secondary, fx),
                                    holding.decimal("plPercent"), holding.text("bankNumber"),
                                    holding.text("assetType"));
                        }).toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<AssetTypeSlice>>> compositionSec = applyBudget(
                Section.guard(
                        summaryFuture.thenCombine(summaryRateFuture, (summary, rate) -> {
                            BigDecimal total = BffMoneyConverter.toArs(summary.marketValue(), rate);
                            return summary.byAssetType().entrySet().stream()
                                    .sorted(Map.Entry.comparingByKey())
                                    .map(slice -> {
                                        AssetTypeTotals totals = slice.getValue();
                                        BigDecimal amount = BffMoneyConverter.toArs(totals.marketValue(), rate);
                                        BigDecimal cost = BffMoneyConverter.toArs(totals.cost(), rate);
                                        BigDecimal pnl = amount.subtract(cost);
                                        return new AssetTypeSlice(slice.getKey(), slice.getKey(),
                                                BffMoneyConverter.convert(amount, Currency.ARS, currencyView, secondary, rate),
                                                BffMoneyConverter.convert(cost, Currency.ARS, currencyView, secondary, rate),
                                                BffMoneyConverter.convert(pnl, Currency.ARS, currencyView, secondary, rate),
                                                Percentages.percentOf(pnl, cost),
                                                Percentages.percentOf(amount, total),
                                                totals.count());
                                    })
                                    .toList();
                        }),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<OperationRow>>> recentOperationsSec = applyBudget(
                Section.guard(
                        holdingsFuture.thenCombine(fxRateFuture, (holdings, fx) -> holdings.stream()
                                .map(holding -> {
                                    BigDecimal quantity = holding.decimal("quantity");
                                    OperationKind kind = quantity.signum() >= 0 ? OperationKind.BUY : OperationKind.SELL;
                                    Currency currency = Currency.of(holding.text("currency"));
                                    return new OperationRow(holding.longValue("id"), holding.text("ticker"), kind,
                                            holding.date("createdAt"), quantity.abs(),
                                            BffMoneyConverter.convert(holding.decimal("currentValue"), currency, currencyView, secondary, fx));
                                })
                                .sorted(Comparator.comparing(OperationRow::date).reversed())
                                .limit(RECENT_OPERATIONS)
                                .toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<AlertRow>>> alertsSec = applyBudget(
                Section.guard(
                        notifications.fetchLatestOfType(userId, THRESHOLD_ALERT_TYPE)
                                .thenApply(rows -> DownstreamPayload.rows(ALERTS_SOURCE, rows).stream()
                                        .map(alert -> new AlertRow(alert.longValue("id"), alert.text("title"),
                                                alert.text("message"), alert.instant("createdAt"), alert.flag("read")))
                                        .toList()),
                        List.of(), clock),
                List.of());

        return CompletableFuture.allOf(marketStripSec, kpisSec, evolutionSec, positionsSec, compositionSec, recentOperationsSec, alertsSec)
                .thenApply(v -> new InvestmentsBffData(
                        marketStripSec.join(), kpisSec.join(), evolutionSec.join(),
                        positionsSec.join(), compositionSec.join(), recentOperationsSec.join(), alertsSec.join()));
    }

    private <T> CompletableFuture<Section<T>> applyBudget(CompletableFuture<Section<T>> sectionFuture, T fallback) {
        return sectionFuture.completeOnTimeout(
                Section.unavailable(fallback, ObservedAt.now(clock)),
                budget.total().toMillis(),
                TimeUnit.MILLISECONDS);
    }

    private static String indexLabel(String code) {
        return switch (code) {
            case "MERVAL" -> "Merval";
            case "SP500" -> "S&P 500";
            case "RIESGO_PAIS" -> "Riesgo país";
            default -> code;
        };
    }

    private static MarketQuoteUnit indexUnit(String code) {
        return "RIESGO_PAIS".equals(code) ? MarketQuoteUnit.POINTS : MarketQuoteUnit.PERCENT;
    }
}
