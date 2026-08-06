package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.UploadGateway;
import com.financialapp.gateway.domain.model.bff.ImportsBffData;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.usecase.bff.GetImportsBffUseCase;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GetImportsBffUseCaseImpl implements GetImportsBffUseCase {

    private final UploadGateway upload;
    private final PageTimeoutBudget budget;
    private final Clock clock;

    public GetImportsBffUseCaseImpl(UploadGateway upload, PageTimeoutBudget budget) {
        this(upload, budget, Clock.systemUTC());
    }

    public GetImportsBffUseCaseImpl(UploadGateway upload, PageTimeoutBudget budget, Clock clock) {
        this.upload = upload;
        this.budget = budget != null ? budget : PageTimeoutBudget.fromMillis(5000);
        this.clock = clock;
    }

    @Override
    public CompletableFuture<ImportsBffData> execute(UserId userId) {
        CompletableFuture<Section<Map<String, Object>>> activeRun = applyBudget(
                Section.guard(upload.fetchHistory(userId).thenApply(h -> h.isEmpty() ? Map.of() : h.get(0)), Map.of(), clock), Map.of());

        CompletableFuture<Section<List<Map<String, Object>>>> history = applyBudget(
                Section.guard(upload.fetchHistory(userId), List.of(), clock), List.of());

        CompletableFuture<Section<Map<String, Object>>> reconciliation = applyBudget(
                Section.guard(upload.fetchHistory(userId).thenApply(h -> Map.of("count", (Object) h.size())), Map.of(), clock), Map.of());

        return CompletableFuture.allOf(activeRun, history, reconciliation)
                .thenApply(v -> new ImportsBffData(activeRun.join(), history.join(), reconciliation.join()));
    }

    private <T> CompletableFuture<Section<T>> applyBudget(CompletableFuture<Section<T>> sectionFuture, T fallback) {
        return sectionFuture.completeOnTimeout(
                Section.unavailable(fallback, ObservedAt.now(clock)),
                budget.total().toMillis(),
                TimeUnit.MILLISECONDS);
    }
}
