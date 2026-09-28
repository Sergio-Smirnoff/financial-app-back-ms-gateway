package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.bff.CurrencySummary;
import com.financialapp.gateway.domain.model.bff.TransactionQuery;
import com.financialapp.gateway.domain.model.bff.TransactionsBffData;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.model.currency.FxRate;
import com.financialapp.gateway.domain.service.BffMoneyConverter;
import com.financialapp.gateway.domain.usecase.bff.GetTransactionsBffUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GetTransactionsBffUseCaseImpl implements GetTransactionsBffUseCase {

    private static final String PAGE_SOURCE = "ms-finances transactions page";
    private static final String ACCOUNTS_SOURCE = "ms-banks accounts";
    private static final String UNCATEGORISED_SOURCE = "ms-finances uncategorised count";
    private static final List<String> PAYMENT_METHODS =
            List.of("DEBIT_CARD", "CREDIT_CARD", "TRANSFER", "AUTOMATIC_DEBIT", "DEPOSIT", "OTHER");

    private final FinancesGateway finances;
    private final BanksGateway banks;
    private final InvestmentsGateway investments;
    private final PageTimeoutBudget budget;
    private final Clock clock;

    @Autowired
    public GetTransactionsBffUseCaseImpl(
            FinancesGateway finances, BanksGateway banks,
            InvestmentsGateway investments, PageTimeoutBudget budget) {
        this(finances, banks, investments, budget, Clock.systemUTC());
    }

    public GetTransactionsBffUseCaseImpl(
            FinancesGateway finances, BanksGateway banks,
            InvestmentsGateway investments, PageTimeoutBudget budget, Clock clock) {
        this.finances = finances;
        this.banks = banks;
        this.investments = investments;
        this.budget = budget != null ? budget : PageTimeoutBudget.fromMillis(5000);
        this.clock = clock;
    }

    @Override
    public CompletableFuture<TransactionsBffData> execute(
            UserId userId, TransactionQuery query, CurrencyView currencyView, String secondary) {

        LocalDate today = LocalDate.now(clock);
        LocalDate summaryFrom = query.from() != null ? query.from() : today.withDayOfMonth(1);
        LocalDate summaryTo = query.to() != null ? query.to() : today;

        CompletableFuture<Optional<FxRate>> fxRateFuture = currencyView != CurrencyView.ARS ?
                investments.fetchFxRate(currencyView, today) : CompletableFuture.completedFuture(Optional.empty());

        CompletableFuture<List<CurrencySummary>> summaryTotalsFuture = finances.fetchSummary(userId, summaryFrom, summaryTo);
        CompletableFuture<Map<String, Object>> summaryWindowFuture = finances.fetchTransactions(
                userId,
                new TransactionQuery(0, 1, query.categories(), query.accounts(), query.method(), query.query(), summaryFrom, summaryTo));
        CompletableFuture<Map<String, Object>> pageFuture = finances.fetchTransactions(userId, query);
        CompletableFuture<List<DownstreamPayload>> accountsFuture = banks.fetchAccounts(userId)
                .thenApply(rows -> DownstreamPayload.rows(ACCOUNTS_SOURCE, rows));

        CompletableFuture<Section<TransactionsSummary>> summarySec = applyBudget(
                Section.guard(
                        CompletableFuture.allOf(summaryTotalsFuture, summaryWindowFuture, fxRateFuture)
                                .thenApply(v -> {
                                    List<CurrencySummary> summaries = summaryTotalsFuture.join();
                                    Optional<FxRate> fx = fxRateFuture.join();
                                    BigDecimal income = summaries.stream().map(s -> DownstreamPayload.amountOrZero(s.totalIncome())).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal expense = summaries.stream().map(s -> DownstreamPayload.amountOrZero(s.totalExpense())).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    long count = new DownstreamPayload(PAGE_SOURCE, summaryWindowFuture.join())
                                            .optionalLong("totalElements").orElse(0L);
                                    return new TransactionsSummary(
                                            BffMoneyConverter.convert(income, Currency.ARS, currencyView, secondary, fx),
                                            BffMoneyConverter.convert(expense, Currency.ARS, currencyView, secondary, fx),
                                            BffMoneyConverter.convert(income.subtract(expense), Currency.ARS, currencyView, secondary, fx),
                                            count);
                                }),
                        TransactionsSummary.empty(), clock),
                TransactionsSummary.empty());

        CompletableFuture<Section<TransactionsPage>> pageSec = applyBudget(
                Section.guard(
                        CompletableFuture.allOf(pageFuture, accountsFuture, fxRateFuture)
                                .thenApply(v -> {
                                    DownstreamPayload page = new DownstreamPayload(PAGE_SOURCE, pageFuture.join());
                                    Map<String, String> labels = AccountLabels.byCbu(accountsFuture.join());
                                    Optional<FxRate> fx = fxRateFuture.join();
                                    List<TransactionRow> rows = page.list("content").stream()
                                            .map(transaction -> TransactionRows.from(transaction, labels, figure ->
                                                    BffMoneyConverter.convert(figure.amount(), figure.currency(), currencyView, secondary, fx)))
                                            .toList();
                                    long totalElements = page.optionalLong("totalElements").orElse((long) rows.size());
                                    int totalPages = (int) Math.ceil((double) totalElements / query.size());
                                    return new TransactionsPage(rows, query.page(), query.size(), totalElements, Math.max(totalPages, 1));
                                }),
                        TransactionsPage.empty(), clock),
                TransactionsPage.empty());

        CompletableFuture<Section<FilterOptions>> filterOptionsSec = applyBudget(
                Section.guard(
                        accountsFuture.thenCombine(finances.fetchCategories(userId), (accounts, categories) -> new FilterOptions(
                                accounts.stream()
                                        .map(account -> new AccountOption(account.textOr("cbu", ""), AccountLabels.of(account)))
                                        .toList(),
                                CategoryTree.options(categories),
                                PAYMENT_METHODS)),
                        FilterOptions.empty(), clock),
                FilterOptions.empty());

        CompletableFuture<Section<UncategorisedSummary>> uncategorisedSec = applyBudget(
                Section.guard(
                        finances.fetchUncategorisedCount(userId)
                                .thenApply(count -> new UncategorisedSummary(
                                        new DownstreamPayload(UNCATEGORISED_SOURCE, count).optionalLong("count").orElse(0L))),
                        new UncategorisedSummary(0L), clock),
                new UncategorisedSummary(0L));

        return CompletableFuture.allOf(summarySec, pageSec, filterOptionsSec, uncategorisedSec)
                .thenApply(v -> new TransactionsBffData(
                        summarySec.join(), pageSec.join(), filterOptionsSec.join(), uncategorisedSec.join()));
    }

    private <T> CompletableFuture<Section<T>> applyBudget(CompletableFuture<Section<T>> sectionFuture, T fallback) {
        return sectionFuture.completeOnTimeout(
                Section.unavailable(fallback, ObservedAt.now(clock)),
                budget.total().toMillis(),
                TimeUnit.MILLISECONDS);
    }
}
