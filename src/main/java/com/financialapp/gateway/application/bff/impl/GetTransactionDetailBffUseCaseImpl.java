package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.gateway.UploadGateway;
import com.financialapp.gateway.domain.model.bff.TransactionDetailBffData;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.usecase.bff.GetTransactionDetailBffUseCase;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GetTransactionDetailBffUseCaseImpl implements GetTransactionDetailBffUseCase {

    private final FinancesGateway finances;
    private final UploadGateway upload;
    private final PageTimeoutBudget budget;
    private final Clock clock;

    public GetTransactionDetailBffUseCaseImpl(FinancesGateway finances, UploadGateway upload, PageTimeoutBudget budget) {
        this(finances, upload, budget, Clock.systemUTC());
    }

    public GetTransactionDetailBffUseCaseImpl(FinancesGateway finances, UploadGateway upload, PageTimeoutBudget budget, Clock clock) {
        this.finances = finances;
        this.upload = upload;
        this.budget = budget != null ? budget : PageTimeoutBudget.fromMillis(5000);
        this.clock = clock;
    }

    @Override
    public CompletableFuture<TransactionDetailBffData> execute(UserId userId, Long transactionId) {
        CompletableFuture<Map<String, Object>> txFuture = finances.fetchTransactionById(userId, transactionId);
        CompletableFuture<Map<String, Object>> uploadRunFuture = upload.fetchRunByTransaction(userId, transactionId);

        CompletableFuture<Map<String, Object>> composedFuture = txFuture.thenCombine(uploadRunFuture, (tx, run) -> {
            Map<String, Object> result = new HashMap<>(tx);
            result.put("importRun", run);
            return result;
        });

        CompletableFuture<Section<Map<String, Object>>> detailSection = applyBudget(
                Section.guard(composedFuture, Map.of(), clock), Map.of());

        return detailSection.thenApply(TransactionDetailBffData::new);
    }

    private <T> CompletableFuture<Section<T>> applyBudget(CompletableFuture<Section<T>> sectionFuture, T fallback) {
        return sectionFuture.completeOnTimeout(
                Section.unavailable(fallback, ObservedAt.now(clock)),
                budget.total().toMillis(),
                TimeUnit.MILLISECONDS);
    }
}
