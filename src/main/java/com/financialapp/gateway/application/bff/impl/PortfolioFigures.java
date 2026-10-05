package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.model.bff.AssetTypeTotals;
import com.financialapp.gateway.domain.model.bff.CurrencyAmounts;
import com.financialapp.gateway.domain.model.bff.PortfolioSummary;
import com.financialapp.gateway.domain.model.bff.PortfolioValuePoint;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.model.currency.FxRate;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

final class PortfolioFigures {

    private static final String SUMMARY_SOURCE = "ms-investments portfolio summary";
    private static final String EVOLUTION_SOURCE = "ms-investments portfolio evolution";

    private PortfolioFigures() {
    }

    static PortfolioSummary summary(Map<String, Object> raw) {
        DownstreamPayload payload = new DownstreamPayload(SUMMARY_SOURCE, raw);
        CurrencyAmounts marketValue = CurrencyAmounts.none();
        CurrencyAmounts cost = CurrencyAmounts.none();
        CurrencyAmounts pnl = CurrencyAmounts.none();
        Map<String, AssetTypeTotals> byAssetType = new HashMap<>();
        for (DownstreamPayload bucket : payload.list("byCurrency")) {
            Currency currency = Currency.of(bucket.text("currency"));
            marketValue = marketValue.plus(currency, bucket.decimal("totalValue"));
            cost = cost.plus(currency, bucket.decimal("totalCost"));
            pnl = pnl.plus(currency, bucket.decimal("totalPl"));
            for (DownstreamPayload slice : bucket.list("breakdown")) {
                byAssetType.merge(slice.text("assetType"),
                        new AssetTypeTotals(
                                CurrencyAmounts.none().plus(currency, slice.decimal("totalValue")),
                                CurrencyAmounts.none().plus(currency, slice.decimal("totalCost")),
                                CurrencyAmounts.none().plus(currency, slice.decimal("totalPl")),
                                Math.toIntExact(slice.longValue("count"))),
                        AssetTypeTotals::plus);
            }
        }
        return new PortfolioSummary(marketValue, cost, pnl, byAssetType);
    }

    static List<PortfolioValuePoint> evolution(List<Map<String, Object>> raw) {
        return DownstreamPayload.rows(EVOLUTION_SOURCE, raw).stream()
                .map(point -> new PortfolioValuePoint(point.date("date"), totalsOf(point)))
                .toList();
    }

    static CompletableFuture<Optional<FxRate>> usdRate(
            boolean needed, CurrencyView view, CompletableFuture<Optional<FxRate>> viewRate,
            InvestmentsGateway investments, LocalDate today) {
        if (view != CurrencyView.ARS) {
            return viewRate;
        }
        return needed
                ? investments.fetchFxRate(CurrencyView.USD_MEP, today)
                : CompletableFuture.completedFuture(Optional.empty());
    }

    private static CurrencyAmounts totalsOf(DownstreamPayload point) {
        CurrencyAmounts total = CurrencyAmounts.none();
        for (DownstreamPayload entry : point.list("totals")) {
            total = total.plus(Currency.of(entry.text("currency")), entry.decimal("totalValue"));
        }
        return total;
    }
}
