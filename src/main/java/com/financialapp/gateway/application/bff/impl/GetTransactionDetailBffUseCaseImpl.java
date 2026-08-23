package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.gateway.UploadGateway;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.bff.MoneyFigure;
import com.financialapp.gateway.domain.model.bff.TransactionDetailBffData;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.usecase.bff.GetTransactionDetailBffUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GetTransactionDetailBffUseCaseImpl implements GetTransactionDetailBffUseCase {

    private final FinancesGateway finances;
    private final UploadGateway upload;
    private final PageTimeoutBudget budget;
    private final Clock clock;

    @Autowired
    public GetTransactionDetailBffUseCaseImpl(
            FinancesGateway finances, UploadGateway upload, PageTimeoutBudget budget) {
        this(finances, upload, budget, Clock.systemUTC());
    }

    public GetTransactionDetailBffUseCaseImpl(
            FinancesGateway finances, UploadGateway upload, PageTimeoutBudget budget, Clock clock) {
        this.finances = finances;
        this.upload = upload;
        this.budget = budget != null ? budget : PageTimeoutBudget.fromMillis(5000);
        this.clock = clock;
    }

    @Override
    public CompletableFuture<TransactionDetailBffData> execute(UserId userId, Long transactionId) {
        CompletableFuture<Map<String, Object>> txFuture = finances.fetchTransactionById(userId, transactionId);
        CompletableFuture<Map<String, Object>> uploadFuture = upload.fetchRunByTransaction(userId, transactionId);

        CompletableFuture<Section<TransactionDetailData>> detailSec = applyBudget(
                Section.guard(
                        txFuture.thenCombine(uploadFuture, (txMap, upMap) -> {
                            TransactionRow row = mapTransactionRow(txMap);
                            TransactionOrigin origin = null;
                            if (upMap != null && !upMap.isEmpty() && upMap.get("runId") != null) {
                                Long runId = parseLong(upMap.get("runId"));
                                String fileName = String.valueOf(upMap.getOrDefault("fileName", ""));
                                Instant importedAt = parseInstant(upMap.get("importedAt"));
                                Boolean reconciled = Boolean.TRUE.equals(upMap.get("reconciled"));
                                origin = new TransactionOrigin(runId, fileName, importedAt, reconciled);
                            }
                            return new TransactionDetailData(row, origin);
                        }),
                        TransactionDetailData.empty(), clock),
                TransactionDetailData.empty());

        return detailSec.thenApply(TransactionDetailBffData::new);
    }

    private TransactionRow mapTransactionRow(Map<String, Object> r) {
        if (r == null || r.isEmpty()) return null;
        Long id = parseLong(r.get("id"));
        LocalDate date = parseDate(r.get("date"));
        String desc = String.valueOf(r.getOrDefault("description", ""));
        String cbu = String.valueOf(r.getOrDefault("accountCbu", ""));
        String alias = String.valueOf(r.getOrDefault("accountAlias", ""));
        Long catId = parseLong(r.get("categoryId"));
        String catName = String.valueOf(r.getOrDefault("categoryName", ""));
        String method = String.valueOf(r.getOrDefault("method", ""));
        String note = String.valueOf(r.getOrDefault("note", ""));
        BigDecimal amount = parseDecimal(r.get("amount"));
        String dirStr = String.valueOf(r.getOrDefault("direction", "OUT"));
        TransactionDirection dir = "IN".equalsIgnoreCase(dirStr) ? TransactionDirection.IN : TransactionDirection.OUT;
        Currency curr = Currency.of(String.valueOf(r.getOrDefault("currency", "ARS")));

        return new TransactionRow(id, date, desc, cbu, alias, catId, catName, method, note, MoneyFigure.of(amount, curr), dir);
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
