package com.financialapp.gateway.domain.gateway;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.model.currency.FxRate;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface InvestmentsGateway {
    int FX_RATE_LOOKBACK_DAYS = 7;

    CompletableFuture<List<FxRate>> latestFxRates();

    CompletableFuture<List<Currency>> holdingCurrencies(Long userId);

    CompletableFuture<Map<String, Object>> fetchPortfolioSummary(UserId userId);

    CompletableFuture<List<Map<String, Object>>> fetchHoldings(UserId userId);

    CompletableFuture<List<Map<String, Object>>> fetchPortfolioEvolution(UserId userId);

    CompletableFuture<Map<String, Object>> fetchMarketPanel();

    CompletableFuture<List<FxRate>> fetchFxRates(LocalDate from, LocalDate to, CurrencyView view);

    CompletableFuture<List<Map<String, Object>>> fetchBrokerFees(UserId userId);

    CompletableFuture<List<Map<String, Object>>> searchPositions(UserId userId, String query);

    default CompletableFuture<Optional<FxRate>> fetchFxRate(CurrencyView view, LocalDate date) {
        return fetchFxRates(date.minusDays(FX_RATE_LOOKBACK_DAYS), date, view)
                .thenApply(rates -> rates.stream()
                        .filter(rate -> !rate.date().isAfter(date))
                        .max(Comparator.comparing(FxRate::date)));
    }
}
