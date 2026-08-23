package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.application.bff.impl.LoanScheduleSupport.LoanWithSchedule;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.gateway.NotificationsGateway;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.bff.MoneyFigure;
import com.financialapp.gateway.domain.model.bff.OverviewBffData;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.model.currency.FxRate;
import com.financialapp.gateway.domain.model.bff.CurrencySummary;
import com.financialapp.gateway.domain.model.bff.UpcomingPaymentView;
import com.financialapp.gateway.domain.service.BffMoneyConverter;
import com.financialapp.gateway.domain.usecase.bff.GetOverviewBffUseCase;
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
public class GetOverviewBffUseCaseImpl implements GetOverviewBffUseCase {

    private final FinancesGateway finances;
    private final BanksGateway banks;
    private final InvestmentsGateway investments;
    private final NotificationsGateway notifications;
    private final PageTimeoutBudget budget;
    private final Clock clock;

    @Autowired
    public GetOverviewBffUseCaseImpl(
            FinancesGateway finances, BanksGateway banks,
            InvestmentsGateway investments, NotificationsGateway notifications,
            PageTimeoutBudget budget) {
        this(finances, banks, investments, notifications, budget, Clock.systemUTC());
    }

    public GetOverviewBffUseCaseImpl(
            FinancesGateway finances, BanksGateway banks,
            InvestmentsGateway investments, NotificationsGateway notifications,
            PageTimeoutBudget budget, Clock clock) {
        this.finances = finances;
        this.banks = banks;
        this.investments = investments;
        this.notifications = notifications;
        this.budget = budget != null ? budget : PageTimeoutBudget.fromMillis(5000);
        this.clock = clock;
    }

    @Override
    public CompletableFuture<OverviewBffData> execute(UserId userId, CurrencyView currencyView, String secondary) {
        LocalDate today = LocalDate.now(clock);
        LocalDate yearStart = today.withDayOfYear(1);

        CompletableFuture<Optional<FxRate>> fxRateFuture = currencyView != CurrencyView.ARS ?
                investments.fetchFxRate(currencyView, today) : CompletableFuture.completedFuture(Optional.empty());

        // Deduplicated futures
        CompletableFuture<Map<String, Object>> portfolioFuture = investments.fetchPortfolioSummary(userId);
        CompletableFuture<List<UpcomingPaymentView>> upcomingFuture = banks.fetchUpcomingPayments(userId, today, today.plusMonths(12));
        CompletableFuture<List<Map<String, Object>>> accountsFuture = banks.fetchAccounts(userId);
        CompletableFuture<List<Map<String, Object>>> cardsFuture = banks.fetchCards(userId);
        CompletableFuture<List<Map<String, Object>>> loansFuture = banks.fetchLoans(userId);
        CompletableFuture<List<LoanWithSchedule>> enrichedLoansFuture = loansFuture.thenCompose(
                loans -> LoanScheduleSupport.enrich(loans, loanId -> banks.fetchLoanInstallments(userId, loanId)));

        CompletableFuture<List<CurrencySummary>> summaryFuture = finances.fetchSummary(userId, yearStart, today);

        CompletableFuture<Section<OverviewKpis>> kpisSec = applyBudget(
                Section.guard(
                        CompletableFuture.allOf(summaryFuture, upcomingFuture, fxRateFuture)
                                .thenApply(v -> {
                                    List<CurrencySummary> summaries = summaryFuture.join();
                                    Optional<FxRate> fx = fxRateFuture.join();
                                    BigDecimal income = summaries.stream().map(s -> parseDecimal(s.totalIncome())).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal expense = summaries.stream().map(s -> parseDecimal(s.totalExpense())).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal balance = summaries.stream().map(s -> parseDecimal(s.balance())).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal committed = upcomingFuture.join().stream().map(u -> parseDecimal(u.amount())).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    MoneyFigure cashFig = BffMoneyConverter.convert(balance, Currency.ARS, currencyView, secondary, fx);
                                    MoneyFigure incomeFig = BffMoneyConverter.convert(income, Currency.ARS, currencyView, secondary, fx);
                                    MoneyFigure expenseFig = BffMoneyConverter.convert(expense, Currency.ARS, currencyView, secondary, fx);
                                    MoneyFigure committedFig = BffMoneyConverter.convert(committed, Currency.ARS, currencyView, secondary, fx);
                                    return new OverviewKpis(cashFig, incomeFig, expenseFig, committedFig);
                                }),
                        OverviewKpis.empty(), clock),
                OverviewKpis.empty());

        CompletableFuture<Section<NetWorth>> netWorthSec = applyBudget(
                Section.guard(
                        portfolioFuture.thenCombine(fxRateFuture, (pf, fx) -> {
                            BigDecimal totalVal = parseDecimal(pf.get("totalMarketValue"));
                            MoneyFigure valFig = BffMoneyConverter.convert(totalVal, Currency.ARS, currencyView, secondary, fx);
                            NetWorthPoint point = new NetWorthPoint(today, valFig);
                            return new NetWorth(List.of(point), new NetWorthDelta(valFig, BigDecimal.ZERO), true);
                        }),
                        NetWorth.empty(), clock),
                NetWorth.empty());

        CompletableFuture<Section<Breakdown>> breakdownSec = applyBudget(
                Section.guard(
                        CompletableFuture.allOf(portfolioFuture, accountsFuture, cardsFuture, enrichedLoansFuture, fxRateFuture)
                                .thenApply(v -> {
                                    Optional<FxRate> fx = fxRateFuture.join();
                                    BigDecimal inv = parseDecimal(portfolioFuture.join().get("totalMarketValue"));
                                    BigDecimal savings = accountsFuture.join().stream()
                                            .filter(a -> "SAVINGS".equalsIgnoreCase(String.valueOf(a.get("type"))))
                                            .map(a -> parseDecimal(a.get("balance"))).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal cash = accountsFuture.join().stream()
                                            .filter(a -> !"SAVINGS".equalsIgnoreCase(String.valueOf(a.get("type"))))
                                            .map(a -> parseDecimal(a.get("balance"))).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal cardDebt = cardsFuture.join().stream().map(c -> parseDecimal(c.get("usedBalance"))).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal loanDebt = enrichedLoansFuture.join().stream()
                                            .map(e -> LoanScheduleSupport.outstanding(e.schedule()))
                                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                                    return new Breakdown(
                                            BffMoneyConverter.convert(inv, Currency.ARS, currencyView, secondary, fx),
                                            BffMoneyConverter.convert(cash, Currency.ARS, currencyView, secondary, fx),
                                            BffMoneyConverter.convert(cardDebt.add(loanDebt), Currency.ARS, currencyView, secondary, fx),
                                            BffMoneyConverter.convert(savings, Currency.ARS, currencyView, secondary, fx));
                                }),
                        Breakdown.empty(), clock),
                Breakdown.empty());

        CompletableFuture<Section<List<FlowPoint>>> flowSec = applyBudget(
                Section.guard(
                        finances.fetchMonthlyFlow(userId, today.minusMonths(12), today)
                                .thenCombine(fxRateFuture, (list, fx) -> list.stream().map(m -> {
                                    String month = String.valueOf(m.getOrDefault("month", ""));
                                    BigDecimal inc = parseDecimal(m.get("income"));
                                    BigDecimal exp = parseDecimal(m.get("expense"));
                                    return new FlowPoint(month,
                                            BffMoneyConverter.convert(inc, Currency.ARS, currencyView, secondary, fx),
                                            BffMoneyConverter.convert(exp, Currency.ARS, currencyView, secondary, fx));
                                }).toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<CommittedPoint>>> committedSec = applyBudget(
                Section.guard(
                        upcomingFuture.thenCombine(fxRateFuture, (list, fx) -> list.stream().map(u -> {
                            String month = u.dueDate() != null ? u.dueDate().toString().substring(0, 7) : "";
                            BigDecimal amount = parseDecimal(u.amount());
                            return new CommittedPoint(month, BffMoneyConverter.convert(amount, Currency.ARS, currencyView, secondary, fx));
                        }).toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<UpcomingPayment>>> upcomingSec = applyBudget(
                Section.guard(
                        upcomingFuture.thenCombine(fxRateFuture, (list, fx) -> list.stream().map(u -> {
                            String id = u.id() != null ? u.id().toString() : "";
                            String label = u.description() != null ? u.description() : "";
                            LocalDate dueDate = u.dueDate() != null ? u.dueDate() : today;
                            BigDecimal amount = parseDecimal(u.amount());
                            String kind = u.type() != null ? u.type() : "BILL";
                            return new UpcomingPayment(id, label, dueDate, BffMoneyConverter.convert(amount, Currency.ARS, currencyView, secondary, fx), kind);
                        }).toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<CategorySpend>>> spendSec = applyBudget(
                Section.guard(
                        finances.fetchSpendByCategory(userId, today.withDayOfMonth(1), today, "EXPENSE")
                                .thenCombine(fxRateFuture, (list, fx) -> list.stream().map(c -> {
                                    Long catId = parseLong(c.get("categoryId"));
                                    String name = String.valueOf(c.getOrDefault("categoryName", c.getOrDefault("name", "")));
                                    BigDecimal amount = parseDecimal(c.get("amount"));
                                    BigDecimal pct = parseDecimal(c.get("percentage"));
                                    return new CategorySpend(catId, name, BffMoneyConverter.convert(amount, Currency.ARS, currencyView, secondary, fx), pct);
                                }).toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<TransactionRow>>> movementsSec = applyBudget(
                Section.guard(
                        finances.fetchTransactions(userId, 0, 10, null, null, null, null)
                                .thenCombine(fxRateFuture, (res, fx) -> {
                                    Object contentObj = res.get("content");
                                    List<Map<String, Object>> rows = contentObj instanceof List<?> l ? (List<Map<String, Object>>) l : List.of();
                                    return rows.stream().map(r -> mapTransactionRow(r, currencyView, secondary, fx)).toList();
                                }),
                        List.of(), clock),
                List.of());

        return CompletableFuture.allOf(kpisSec, netWorthSec, breakdownSec, flowSec, committedSec, upcomingSec, spendSec, movementsSec)
                .thenApply(v -> new OverviewBffData(
                        kpisSec.join(), netWorthSec.join(), breakdownSec.join(), flowSec.join(),
                        committedSec.join(), upcomingSec.join(), spendSec.join(), movementsSec.join()));
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
        try {
            return new BigDecimal(val.toString());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static Long parseLong(Object val) {
        if (val == null) return null;
        try {
            return Long.parseLong(val.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDate parseDate(Object val) {
        if (val == null) return LocalDate.now();
        try {
            return LocalDate.parse(val.toString());
        } catch (Exception e) {
            return LocalDate.now();
        }
    }
}
