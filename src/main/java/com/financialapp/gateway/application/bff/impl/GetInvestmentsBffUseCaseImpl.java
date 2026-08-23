package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.gateway.NotificationsGateway;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.bff.InvestmentsBffData;
import com.financialapp.gateway.domain.model.bff.MoneyFigure;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.model.currency.FxRate;
import com.financialapp.gateway.domain.service.BffMoneyConverter;
import com.financialapp.gateway.domain.usecase.bff.GetInvestmentsBffUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GetInvestmentsBffUseCaseImpl implements GetInvestmentsBffUseCase {

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

        // Shared deduplicated futures
        CompletableFuture<Map<String, Object>> portfolioFuture = investments.fetchPortfolioSummary(userId);
        CompletableFuture<List<Map<String, Object>>> holdingsFuture = investments.fetchHoldings(userId);

        CompletableFuture<Section<List<MarketQuote>>> marketStripSec = applyBudget(
                Section.guard(
                        investments.fetchMarketPanel()
                                .thenApply(panel -> {
                                    Object quotesObj = panel.get("quotes");
                                    List<Map<String, Object>> list = quotesObj instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
                                    return list.stream().map(q -> {
                                        String code = String.valueOf(q.getOrDefault("code", ""));
                                        String label = String.valueOf(q.getOrDefault("label", ""));
                                        BigDecimal val = parseDecimal(q.get("value"));
                                        BigDecimal var = parseDecimal(q.get("variation"));
                                        String unitStr = String.valueOf(q.getOrDefault("unit", "PERCENT"));
                                        MarketQuoteUnit unit = MarketQuoteUnit.valueOf(unitStr.toUpperCase());
                                        Instant obs = parseInstant(q.get("observedAt"));
                                        return new MarketQuote(code, label, val, var, unit, obs);
                                    }).toList();
                                }),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<InvestmentsKpis>> kpisSec = applyBudget(
                Section.guard(
                        portfolioFuture.thenCombine(fxRateFuture, (pf, fx) -> {
                            BigDecimal marketVal = parseDecimal(pf.get("totalMarketValue"));
                            BigDecimal cost = parseDecimal(pf.get("totalCost"));
                            BigDecimal pnl = parseDecimal(pf.get("totalPnl"));
                            BigDecimal pnlPct = parseDecimal(pf.get("totalPnlPct"));

                            return new InvestmentsKpis(
                                    BffMoneyConverter.convert(marketVal, Currency.ARS, currencyView, secondary, fx),
                                    BffMoneyConverter.convert(cost, Currency.ARS, currencyView, secondary, fx),
                                    BffMoneyConverter.convert(pnl, Currency.ARS, currencyView, secondary, fx),
                                    pnlPct
                            );
                        }),
                        InvestmentsKpis.empty(), clock),
                InvestmentsKpis.empty());

        CompletableFuture<Section<List<EvolutionPoint>>> evolutionSec = applyBudget(
                Section.guard(
                        investments.fetchPortfolioEvolution(userId)
                                .thenCombine(fxRateFuture, (list, fx) -> list.stream().map(e -> {
                                    LocalDate date = parseDate(e.get("date"));
                                    BigDecimal mv = parseDecimal(e.get("marketValue"));
                                    BigDecimal cost = parseDecimal(e.get("cost"));
                                    return new EvolutionPoint(date,
                                            BffMoneyConverter.convert(mv, Currency.ARS, currencyView, secondary, fx),
                                            BffMoneyConverter.convert(cost, Currency.ARS, currencyView, secondary, fx));
                                }).toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<PositionRow>>> positionsSec = applyBudget(
                Section.guard(
                        holdingsFuture.thenCombine(fxRateFuture, (list, fx) -> list.stream().map(h -> {
                            Long holdingId = parseLong(h.get("id"));
                            String ticker = String.valueOf(h.getOrDefault("ticker", ""));
                            String name = String.valueOf(h.getOrDefault("name", ""));
                            BigDecimal qty = parseDecimal(h.get("quantity"));
                            BigDecimal avgCost = parseDecimal(h.get("avgCost"));
                            BigDecimal price = parseDecimal(h.get("price"));
                            BigDecimal mv = parseDecimal(h.get("marketValue"));
                            BigDecimal pnl = parseDecimal(h.get("pnl"));
                            BigDecimal pnlPct = parseDecimal(h.get("pnlPct"));
                            String bankNumber = String.valueOf(h.getOrDefault("bankNumber", ""));
                            Currency curr = Currency.of(String.valueOf(h.getOrDefault("currency", "ARS")));

                            return new PositionRow(
                                    holdingId, ticker, name, qty,
                                    BffMoneyConverter.convert(avgCost, curr, currencyView, secondary, fx),
                                    BffMoneyConverter.convert(price, curr, currencyView, secondary, fx),
                                    BffMoneyConverter.convert(mv, curr, currencyView, secondary, fx),
                                    BffMoneyConverter.convert(pnl, curr, currencyView, secondary, fx),
                                    pnlPct, bankNumber
                            );
                        }).toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<CompositionSlice>>> compositionSec = applyBudget(
                Section.guard(
                        portfolioFuture.thenCombine(fxRateFuture, (pf, fx) -> {
                            BigDecimal totalMv = parseDecimal(pf.get("totalMarketValue"));
                            Object compObj = pf.get("composition");
                            List<Map<String, Object>> slices = compObj instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
                            return slices.stream().map(s -> {
                                String label = String.valueOf(s.getOrDefault("assetClass", s.getOrDefault("label", "")));
                                BigDecimal amt = parseDecimal(s.get("amount"));
                                BigDecimal pct = totalMv.compareTo(BigDecimal.ZERO) > 0 ? amt.divide(totalMv, 4, RoundingMode.HALF_EVEN).multiply(new BigDecimal("100")) : BigDecimal.ZERO;
                                return new CompositionSlice(label, BffMoneyConverter.convert(amt, Currency.ARS, currencyView, secondary, fx), pct);
                            }).toList();
                        }),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<OperationRow>>> recentOperationsSec = applyBudget(
                Section.guard(
                        holdingsFuture.thenCombine(fxRateFuture, (list, fx) -> list.stream()
                                .map(h -> {
                                    Long holdingId = parseLong(h.get("id"));
                                    String ticker = String.valueOf(h.getOrDefault("ticker", ""));
                                    BigDecimal qty = parseDecimal(h.get("quantity"));
                                    OperationKind kind = qty.compareTo(BigDecimal.ZERO) >= 0 ? OperationKind.BUY : OperationKind.SELL;
                                    LocalDate date = parseDate(h.get("purchaseDate"));
                                    BigDecimal amt = parseDecimal(h.get("marketValue"));
                                    Currency curr = Currency.of(String.valueOf(h.getOrDefault("currency", "ARS")));
                                    return new OperationRow(holdingId, ticker, kind, date, qty.abs(), BffMoneyConverter.convert(amt, curr, currencyView, secondary, fx));
                                })
                                .sorted((o1, o2) -> o2.date().compareTo(o1.date()))
                                .limit(10)
                                .toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<AlertRow>>> alertsSec = applyBudget(
                Section.guard(
                        notifications.fetchLatestByCategory(userId, "PORTFOLIO_ALERTS")
                                .thenApply(list -> list.stream().map(n -> {
                                    Long id = parseLong(n.get("id"));
                                    String title = String.valueOf(n.getOrDefault("title", ""));
                                    String message = String.valueOf(n.getOrDefault("message", ""));
                                    Instant createdAt = parseInstant(n.get("createdAt"));
                                    Boolean read = Boolean.TRUE.equals(n.get("read"));
                                    return new AlertRow(id, title, message, createdAt, read);
                                }).toList()),
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

    private static BigDecimal parseDecimal(Object val) {
        if (val == null) return BigDecimal.ZERO;
        try { return new BigDecimal(val.toString()); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private static Long parseLong(Object val) {
        if (val == null) return null;
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return null; }
    }

    private static LocalDate parseDate(Object val) {
        if (val == null) return LocalDate.now();
        try { return LocalDate.parse(val.toString()); } catch (Exception e) { return LocalDate.now(); }
    }

    private static Instant parseInstant(Object val) {
        if (val == null) return Instant.now();
        try { return Instant.parse(val.toString()); } catch (Exception e) { return Instant.now(); }
    }
}
