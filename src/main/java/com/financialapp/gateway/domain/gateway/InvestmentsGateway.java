package com.financialapp.gateway.domain.gateway;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.model.currency.FxRate;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface InvestmentsGateway {
    CompletableFuture<List<FxRate>> latestFxRates();

    CompletableFuture<List<Currency>> holdingCurrencies(Long userId);

    CompletableFuture<Map<String, Object>> fetchPortfolioSummary(UserId userId);

    CompletableFuture<List<Map<String, Object>>> fetchHoldings(UserId userId);

    CompletableFuture<List<Map<String, Object>>> fetchPortfolioEvolution(UserId userId);

    CompletableFuture<Map<String, Object>> fetchMarketPanel();

    CompletableFuture<List<FxRate>> fetchFxRates(LocalDate from, LocalDate to, CurrencyView view);

    CompletableFuture<List<Map<String, Object>>> fetchBrokerFees(UserId userId);

    CompletableFuture<List<Map<String, Object>>> searchPositions(UserId userId, String query);
}
