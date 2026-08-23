package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.bff.MoneyFigure;
import com.financialapp.gateway.domain.model.bff.TransactionsBffData;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.model.currency.FxRate;
import com.financialapp.gateway.domain.model.dashboard.CurrencySummary;
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
            UserId userId, int page, int size,
            List<String> categories, List<String> accounts,
            LocalDate from, LocalDate to,
            CurrencyView currencyView, String secondary) {

        LocalDate today = LocalDate.now(clock);
        LocalDate summaryFrom = from != null ? from : today.withDayOfMonth(1);
        LocalDate summaryTo = to != null ? to : today;

        CompletableFuture<Optional<FxRate>> fxRateFuture = currencyView != CurrencyView.ARS ?
                investments.fetchFxRate(currencyView, today) : CompletableFuture.completedFuture(Optional.empty());

        CompletableFuture<List<CurrencySummary>> summaryTotalsFuture = finances.fetchSummary(userId, summaryFrom, summaryTo);
        CompletableFuture<Map<String, Object>> summaryWindowFuture =
                finances.fetchTransactions(userId, 0, 1, categories, accounts, summaryFrom, summaryTo);

        CompletableFuture<Section<TransactionsSummary>> summarySec = applyBudget(
                Section.guard(
                        CompletableFuture.allOf(summaryTotalsFuture, summaryWindowFuture, fxRateFuture)
                                .thenApply(v -> {
                                    List<CurrencySummary> summaries = summaryTotalsFuture.join();
                                    Optional<FxRate> fx = fxRateFuture.join();
                                    BigDecimal income = summaries.stream().map(s -> parseDecimal(s.totalIncome())).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal expense = summaries.stream().map(s -> parseDecimal(s.totalExpense())).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal net = income.subtract(expense);
                                    long count = parseLongVal(summaryWindowFuture.join().get("totalElements"), 0L);
                                    return new TransactionsSummary(
                                            BffMoneyConverter.convert(income, Currency.ARS, currencyView, secondary, fx),
                                            BffMoneyConverter.convert(expense, Currency.ARS, currencyView, secondary, fx),
                                            BffMoneyConverter.convert(net, Currency.ARS, currencyView, secondary, fx),
                                            count
                                    );
                                }),
                        TransactionsSummary.empty(), clock),
                TransactionsSummary.empty());

        CompletableFuture<Section<TransactionsPage>> pageSec = applyBudget(
                Section.guard(
                        finances.fetchTransactions(userId, page, size, categories, accounts, from, to)
                                .thenCombine(fxRateFuture, (res, fx) -> {
                                    Object contentObj = res.get("content");
                                    List<Map<String, Object>> contentList = contentObj instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
                                    List<TransactionRow> rows = contentList.stream().map(r -> mapTransactionRow(r, currencyView, secondary, fx)).toList();
                                    int p = parseInt(res.get("number"), page);
                                    int s = parseInt(res.get("size"), size);
                                    long totalEl = parseLongVal(res.get("totalElements"), rows.size());
                                    int totalP = parseInt(res.get("totalPages"), 1);
                                    return new TransactionsPage(rows, p, s, totalEl, totalP);
                                }),
                        TransactionsPage.empty(), clock),
                TransactionsPage.empty());

        CompletableFuture<Section<FilterOptions>> filterOptionsSec = applyBudget(
                Section.guard(
                        banks.fetchAccounts(userId)
                                .thenCombine(finances.fetchCategorizationRules(userId), (accList, rules) -> {
                                    List<AccountOption> accOpts = accList.stream().map(a ->
                                            new AccountOption(String.valueOf(a.getOrDefault("cbu", "")), String.valueOf(a.getOrDefault("alias", "")))
                                    ).toList();
                                    List<CategoryOption> catOpts = rules.stream().map(r ->
                                            new CategoryOption(parseLong(r.get("categoryId")), String.valueOf(r.getOrDefault("categoryName", "")))
                                    ).filter(c -> c.id() != null).distinct().toList();
                                    // Mirror of ms-finances PaymentMethod — there is no discovery endpoint for it.
                                    List<String> methods = List.of("DEBIT_CARD", "CREDIT_CARD", "TRANSFER", "AUTOMATIC_DEBIT", "DEPOSIT", "OTHER");
                                    return new FilterOptions(accOpts, catOpts, methods);
                                }),
                        FilterOptions.empty(), clock),
                FilterOptions.empty());

        CompletableFuture<Section<UncategorisedSummary>> uncategorisedSec = applyBudget(
                Section.guard(
                        finances.fetchUncategorisedCount(userId)
                                .thenApply(map -> new UncategorisedSummary(parseLongVal(map.get("count"), 0L))),
                        new UncategorisedSummary(0L), clock),
                new UncategorisedSummary(0L));

        return CompletableFuture.allOf(summarySec, pageSec, filterOptionsSec, uncategorisedSec)
                .thenApply(v -> new TransactionsBffData(
                        summarySec.join(), pageSec.join(), filterOptionsSec.join(), uncategorisedSec.join()));
    }

    private TransactionRow mapTransactionRow(Map<String, Object> r, CurrencyView currencyView, String secondary, Optional<FxRate> fx) {
        Long id = parseLong(r.get("id"));
        LocalDate date = parseDate(r.get("date"));
        String desc = String.valueOf(r.getOrDefault("description", ""));
        String cbu = String.valueOf(r.getOrDefault("fromCbu", ""));
        String alias = String.valueOf(r.getOrDefault("accountAlias", ""));
        Long catId = parseLong(r.get("categoryId"));
        String catName = String.valueOf(r.getOrDefault("categoryName", ""));
        String method = String.valueOf(r.getOrDefault("paymentMethod", ""));
        String note = String.valueOf(r.getOrDefault("note", ""));
        BigDecimal amount = parseDecimal(r.get("amount"));
        String kindStr = String.valueOf(r.getOrDefault("kind", "EXPENSE"));
        TransactionDirection dir = "INCOME".equalsIgnoreCase(kindStr) ? TransactionDirection.IN : TransactionDirection.OUT;

        String currStr = String.valueOf(r.getOrDefault("currency", "ARS"));
        Currency sourceCurrency = Currency.of(currStr);
        MoneyFigure figure = BffMoneyConverter.convert(amount, sourceCurrency, currencyView, secondary, fx);

        return new TransactionRow(id, date, desc, cbu, alias, catId, catName, method, note, figure, dir);
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

    private static long parseLongVal(Object val, long fallback) {
        if (val == null) return fallback;
        try { return Long.parseLong(val.toString()); } catch (Exception e) { return fallback; }
    }

    private static int parseInt(Object val, int fallback) {
        if (val == null) return fallback;
        try { return Integer.parseInt(val.toString()); } catch (Exception e) { return fallback; }
    }

    private static LocalDate parseDate(Object val) {
        if (val == null) return LocalDate.now();
        try { return LocalDate.parse(val.toString()); } catch (Exception e) { return LocalDate.now(); }
    }
}
