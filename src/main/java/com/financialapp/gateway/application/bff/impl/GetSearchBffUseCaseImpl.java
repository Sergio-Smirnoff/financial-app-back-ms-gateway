package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.bff.SearchBffData;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.usecase.bff.GetSearchBffUseCase;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GetSearchBffUseCaseImpl implements GetSearchBffUseCase {

    private final FinancesGateway finances;
    private final InvestmentsGateway investments;
    private final PageTimeoutBudget budget;
    private final Clock clock;

    public GetSearchBffUseCaseImpl(
            FinancesGateway finances, InvestmentsGateway investments, PageTimeoutBudget budget) {
        this(finances, investments, budget, Clock.systemUTC());
    }

    public GetSearchBffUseCaseImpl(
            FinancesGateway finances, InvestmentsGateway investments, PageTimeoutBudget budget, Clock clock) {
        this.finances = finances;
        this.investments = investments;
        this.budget = budget != null ? budget : PageTimeoutBudget.fromMillis(5000);
        this.clock = clock;
    }

    @Override
    public CompletableFuture<SearchBffData> execute(UserId userId, String query) {
        String q = query != null ? query.trim() : "";

        CompletableFuture<Section<List<SearchHit>>> movementsSec = applyBudget(
                Section.guard(
                        finances.searchTransactions(userId, q)
                                .thenApply(list -> list.stream().map(m -> {
                                    String id = String.valueOf(m.getOrDefault("id", ""));
                                    String label = String.valueOf(m.getOrDefault("description", ""));
                                    String sublabel = String.valueOf(m.getOrDefault("amount", "")) + " " + String.valueOf(m.getOrDefault("currency", "ARS"));
                                    String href = "/transactions/" + id;
                                    return new SearchHit(id, label, sublabel, href);
                                }).toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<SearchHit>>> positionsSec = applyBudget(
                Section.guard(
                        investments.searchPositions(userId, q)
                                .thenApply(list -> list.stream().map(p -> {
                                    String id = String.valueOf(p.getOrDefault("holdingId", p.getOrDefault("id", "")));
                                    String label = String.valueOf(p.getOrDefault("ticker", ""));
                                    String sublabel = String.valueOf(p.getOrDefault("name", ""));
                                    String href = "/investments/holdings/" + id;
                                    return new SearchHit(id, label, sublabel, href);
                                }).toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<SearchHit>>> categoriesSec = applyBudget(
                Section.guard(
                        finances.fetchCategorizationRules(userId)
                                .thenApply(list -> list.stream()
                                        .filter(c -> {
                                            String name = String.valueOf(c.getOrDefault("categoryName", "")).toLowerCase(Locale.ROOT);
                                            return q.isEmpty() || name.contains(q.toLowerCase(Locale.ROOT));
                                        })
                                        .map(c -> {
                                            String id = String.valueOf(c.getOrDefault("categoryId", ""));
                                            String label = String.valueOf(c.getOrDefault("categoryName", ""));
                                            String sublabel = "Categoría";
                                            String href = "/categories?category=" + id;
                                            return new SearchHit(id, label, sublabel, href);
                                        })
                                        .distinct()
                                        .toList()),
                        List.of(), clock),
                List.of());

        return CompletableFuture.allOf(movementsSec, positionsSec, categoriesSec)
                .thenApply(v -> new SearchBffData(
                        movementsSec.join(), positionsSec.join(), categoriesSec.join()));
    }

    private <T> CompletableFuture<Section<T>> applyBudget(CompletableFuture<Section<T>> sectionFuture, T fallback) {
        return sectionFuture.completeOnTimeout(
                Section.unavailable(fallback, ObservedAt.now(clock)),
                budget.total().toMillis(),
                TimeUnit.MILLISECONDS);
    }
}
