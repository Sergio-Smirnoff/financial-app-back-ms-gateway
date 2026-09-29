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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GetSearchBffUseCaseImpl implements GetSearchBffUseCase {

    private static final String MOVEMENTS_SOURCE = "ms-finances transaction search";
    private static final String POSITIONS_SOURCE = "ms-investments position search";

    private final FinancesGateway finances;
    private final InvestmentsGateway investments;
    private final PageTimeoutBudget budget;
    private final Clock clock;

    @Autowired
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
        String needle = q.toLowerCase(Locale.ROOT);

        CompletableFuture<Section<List<SearchHit>>> movementsSec = applyBudget(
                Section.guard(
                        finances.searchTransactions(userId, q)
                                .thenApply(rows -> DownstreamPayload.rows(MOVEMENTS_SOURCE, rows).stream().map(movement -> {
                                    String id = movement.text("id");
                                    String sublabel = movement.textOr("amount", "") + " " + movement.textOr("currency", "ARS");
                                    return new SearchHit(id, movement.textOr("description", ""), sublabel, "/transactions?id=" + id);
                                }).toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<SearchHit>>> positionsSec = applyBudget(
                Section.guard(
                        investments.searchPositions(userId, q)
                                .thenApply(rows -> DownstreamPayload.rows(POSITIONS_SOURCE, rows).stream().map(position -> {
                                    String id = position.optionalText("holdingId").or(() -> position.optionalText("id")).orElse("");
                                    return new SearchHit(id, position.textOr("ticker", ""), position.textOr("name", ""),
                                            "/investments/holdings/" + id);
                                }).toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<SearchHit>>> categoriesSec = applyBudget(
                Section.guard(
                        finances.fetchCategories(userId)
                                .thenApply(categories -> CategoryTree.options(categories).stream()
                                        .filter(option -> needle.isEmpty() || option.name().toLowerCase(Locale.ROOT).contains(needle))
                                        .map(option -> new SearchHit(String.valueOf(option.id()), option.name(), "Categoría",
                                                "/categories?category=" + option.id()))
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
