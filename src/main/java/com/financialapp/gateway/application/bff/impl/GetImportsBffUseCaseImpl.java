package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.UploadGateway;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.bff.ImportsBffData;
import com.financialapp.gateway.domain.model.bff.MoneyFigure;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.usecase.bff.GetImportsBffUseCase;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
        // ONE fetchHistory future feeds activeRun, history, and reconciliation
        CompletableFuture<List<Map<String, Object>>> historyFuture = upload.fetchHistory(userId);

        CompletableFuture<Section<ActiveRun>> activeRunSec = applyBudget(
                Section.guard(
                        historyFuture.thenApply(list -> {
                            Optional<Map<String, Object>> activeOpt = list.stream()
                                    .filter(h -> {
                                        String st = String.valueOf(h.getOrDefault("status", "COMPLETED")).toUpperCase();
                                        return !"COMPLETED".equals(st) && !"FAILED".equals(st);
                                    })
                                    .findFirst();

                            if (activeOpt.isEmpty()) return null;
                            Map<String, Object> a = activeOpt.get();
                            Long runId = parseLong(a.get("runId"));
                            String status = String.valueOf(a.getOrDefault("status", "PROCESSING"));
                            String fileName = String.valueOf(a.getOrDefault("fileName", ""));
                            Instant startedAt = parseInstant(a.get("importedAt"));
                            Integer processed = parseInt(a.get("inserted"), 0);
                            Integer total = parseInt(a.get("total"), 0);
                            return new ActiveRun(runId, status, fileName, startedAt, processed, total);
                        }),
                        null, clock),
                null);

        CompletableFuture<Section<List<ImportRunRow>>> historySec = applyBudget(
                Section.guard(
                        historyFuture.thenApply(list -> list.stream().map(h -> {
                            Long runId = parseLong(h.get("runId"));
                            String fileName = String.valueOf(h.getOrDefault("fileName", ""));
                            Instant importedAt = parseInstant(h.get("importedAt"));
                            String accountCbu = String.valueOf(h.getOrDefault("accountCbu", ""));
                            Integer inserted = parseInt(h.get("inserted"), 0);
                            Integer duplicates = parseInt(h.get("duplicates"), 0);
                            Integer failed = parseInt(h.get("failed"), 0);
                            String status = String.valueOf(h.getOrDefault("status", "COMPLETED"));
                            return new ImportRunRow(runId, fileName, importedAt, accountCbu, inserted, duplicates, failed, status);
                        }).toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<ReconciliationRow>>> reconciliationSec = applyBudget(
                Section.guard(
                        historyFuture.thenApply(list -> list.stream().map(h -> {
                            Long runId = parseLong(h.get("runId"));
                            Object expVal = h.get("expectedBalance");
                            Object compVal = h.get("computedBalance");
                            MoneyFigure expMoney = expVal != null ? MoneyFigure.of(parseDecimal(expVal), Currency.ARS) : null;
                            MoneyFigure compMoney = compVal != null ? MoneyFigure.of(parseDecimal(compVal), Currency.ARS) : null;
                            Boolean matches = (expVal == null || compVal == null) ? null : expMoney.amount().compareTo(compMoney.amount()) == 0;
                            return new ReconciliationRow(runId, expMoney, compMoney, matches);
                        }).toList()),
                        List.of(), clock),
                List.of());

        return CompletableFuture.allOf(activeRunSec, historySec, reconciliationSec)
                .thenApply(v -> new ImportsBffData(
                        activeRunSec.join(), historySec.join(), reconciliationSec.join()));
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

    private static int parseInt(Object val, int fallback) {
        if (val == null) return fallback;
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return fallback; }
    }

    private static Instant parseInstant(Object val) {
        if (val == null) return Instant.EPOCH;
        try { return Instant.parse(val.toString()); } catch (Exception e) { return Instant.EPOCH; }
    }
}
