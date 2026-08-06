package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.UploadGateway;
import com.financialapp.gateway.domain.model.bff.BanksBffData;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.usecase.bff.GetBanksBffUseCase;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GetBanksBffUseCaseImpl implements GetBanksBffUseCase {

    private final BanksGateway banks;
    private final UploadGateway upload;
    private final PageTimeoutBudget budget;
    private final Clock clock;

    public GetBanksBffUseCaseImpl(BanksGateway banks, UploadGateway upload, PageTimeoutBudget budget) {
        this(banks, upload, budget, Clock.systemUTC());
    }

    public GetBanksBffUseCaseImpl(BanksGateway banks, UploadGateway upload, PageTimeoutBudget budget, Clock clock) {
        this.banks = banks;
        this.upload = upload;
        this.budget = budget != null ? budget : PageTimeoutBudget.fromMillis(5000);
        this.clock = clock;
    }

    @Override
    public CompletableFuture<BanksBffData> execute(UserId userId, CurrencyView currencyView, String secondary) {
        LocalDate today = LocalDate.now();

        CompletableFuture<Section<Map<String, Object>>> kpis = applyBudget(
                Section.guard(banks.fetchAccounts(userId).thenApply(a -> Map.of("accountCount", (Object) a.size())), Map.of(), clock), Map.of());

        CompletableFuture<Section<List<Map<String, Object>>>> accounts = applyBudget(
                Section.guard(banks.fetchAccounts(userId), List.of(), clock), List.of());

        CompletableFuture<Section<List<Map<String, Object>>>> cards = applyBudget(
                Section.guard(banks.fetchCards(userId), List.of(), clock), List.of());

        CompletableFuture<Section<List<Map<String, Object>>>> loans = applyBudget(
                Section.guard(banks.fetchActiveLoans(userId).thenApply(l -> List.of(Map.of("loans", (Object) l))), List.of(), clock), List.of());

        CompletableFuture<Section<List<Map<String, Object>>>> importHealth = applyBudget(
                Section.guard(upload.fetchHistory(userId), List.of(), clock), List.of());

        CompletableFuture<Section<List<Map<String, Object>>>> cashDistribution = applyBudget(
                Section.guard(banks.fetchBalanceSnapshots(userId, today.minusMonths(1), today), List.of(), clock), List.of());

        CompletableFuture<Section<List<Map<String, Object>>>> paymentCalendar = applyBudget(
                Section.guard(banks.fetchUpcomingPayments(userId, today, today.plusMonths(1)).thenApply(p -> List.of(Map.of("payments", (Object) p))), List.of(), clock), List.of());

        return CompletableFuture.allOf(kpis, accounts, cards, loans, importHealth, cashDistribution, paymentCalendar)
                .thenApply(v -> new BanksBffData(
                        kpis.join(), accounts.join(), cards.join(), loans.join(),
                        importHealth.join(), cashDistribution.join(), paymentCalendar.join()));
    }

    private <T> CompletableFuture<Section<T>> applyBudget(CompletableFuture<Section<T>> sectionFuture, T fallback) {
        return sectionFuture.completeOnTimeout(
                Section.unavailable(fallback, ObservedAt.now(clock)),
                budget.total().toMillis(),
                TimeUnit.MILLISECONDS);
    }
}
