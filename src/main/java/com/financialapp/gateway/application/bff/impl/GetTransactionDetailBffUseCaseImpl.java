package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.exception.ResourceNotFoundException;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.gateway.UploadGateway;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.bff.TransactionDetailBffData;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.usecase.bff.GetTransactionDetailBffUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.UnaryOperator;

@Service
public class GetTransactionDetailBffUseCaseImpl implements GetTransactionDetailBffUseCase {

    private static final String TRANSACTION_SOURCE = "ms-finances transaction";
    private static final String IMPORT_RUN_SOURCE = "ms-upload import run";
    private static final String ACCOUNTS_SOURCE = "ms-banks accounts";

    private final FinancesGateway finances;
    private final UploadGateway upload;
    private final BanksGateway banks;
    private final PageTimeoutBudget budget;
    private final Clock clock;

    @Autowired
    public GetTransactionDetailBffUseCaseImpl(
            FinancesGateway finances, UploadGateway upload, BanksGateway banks, PageTimeoutBudget budget) {
        this(finances, upload, banks, budget, Clock.systemUTC());
    }

    public GetTransactionDetailBffUseCaseImpl(
            FinancesGateway finances, UploadGateway upload, BanksGateway banks, PageTimeoutBudget budget, Clock clock) {
        this.finances = finances;
        this.upload = upload;
        this.banks = banks;
        this.budget = budget != null ? budget : PageTimeoutBudget.fromMillis(5000);
        this.clock = clock;
    }

    @Override
    public CompletableFuture<TransactionDetailBffData> execute(UserId userId, Long transactionId) {
        CompletableFuture<Map<String, Object>> transactionFuture = finances.fetchTransactionById(userId, transactionId);
        CompletableFuture<Map<String, Object>> runFuture = upload.fetchRunByTransaction(userId, transactionId);
        CompletableFuture<List<Map<String, Object>>> accountsFuture = banks.fetchAccounts(userId);

        CompletableFuture<Section<TransactionDetailData>> detailSec = applyBudget(
                Section.guard(
                        CompletableFuture.allOf(transactionFuture, runFuture, accountsFuture)
                                .thenApply(v -> new TransactionDetailData(
                                        TransactionRows.from(
                                                new DownstreamPayload(TRANSACTION_SOURCE, transactionFuture.join()),
                                                AccountLabels.byCbu(DownstreamPayload.rows(ACCOUNTS_SOURCE, accountsFuture.join())),
                                                UnaryOperator.identity()),
                                        originOf(new DownstreamPayload(IMPORT_RUN_SOURCE, runFuture.join())))),
                        TransactionDetailData.empty(), clock),
                TransactionDetailData.empty());

        return transactionFuture
                .exceptionallyCompose(GetTransactionDetailBffUseCaseImpl::failOnlyWhenMissing)
                .thenCompose(ignored -> detailSec)
                .thenApply(TransactionDetailBffData::new);
    }

    private static CompletableFuture<Map<String, Object>> failOnlyWhenMissing(Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
        return cause instanceof ResourceNotFoundException
                ? CompletableFuture.failedFuture(cause)
                : CompletableFuture.completedFuture(Map.of());
    }

    private static TransactionOrigin originOf(DownstreamPayload run) {
        if (run.isEmpty()) {
            return null;
        }
        boolean reconciled = run.optionalObject("reconciliation")
                .map(reconciliation -> reconciliation.flag("matches"))
                .orElse(false);
        return new TransactionOrigin(run.longValue("id"), null, run.instant("createdAt"), reconciled);
    }

    private <T> CompletableFuture<Section<T>> applyBudget(CompletableFuture<Section<T>> sectionFuture, T fallback) {
        return sectionFuture.completeOnTimeout(
                Section.unavailable(fallback, ObservedAt.now(clock)),
                budget.total().toMillis(),
                TimeUnit.MILLISECONDS);
    }
}
