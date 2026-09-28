package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.application.bff.impl.LoanScheduleSupport.LoanWithSchedule;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.FinancesGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.gateway.NotificationsGateway;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.bff.CurrencySummary;
import com.financialapp.gateway.domain.model.bff.MoneyFigure;
import com.financialapp.gateway.domain.model.bff.OverviewBffData;
import com.financialapp.gateway.domain.model.bff.PortfolioSummary;
import com.financialapp.gateway.domain.model.bff.TransactionQuery;
import com.financialapp.gateway.domain.model.bff.UpcomingPaymentView;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.model.currency.FxRate;
import com.financialapp.gateway.domain.service.BffMoneyConverter;
import com.financialapp.gateway.domain.service.Percentages;
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
import java.util.stream.Collectors;

@Service
public class GetOverviewBffUseCaseImpl implements GetOverviewBffUseCase {

    private static final String ACCOUNTS_SOURCE = "ms-banks accounts";
    private static final String FLOW_SOURCE = "ms-finances monthly flow";
    private static final String SPEND_SOURCE = "ms-finances category spend";
    private static final String TRANSACTIONS_SOURCE = "ms-finances transactions page";
    private static final String SAVINGS = "SAVINGS";
    private static final int LATEST_MOVEMENTS = 10;

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

        CompletableFuture<PortfolioSummary> portfolioFuture = investments.fetchPortfolioSummary(userId)
                .thenApply(PortfolioFigures::summary);
        CompletableFuture<Optional<FxRate>> portfolioRateFuture = portfolioFuture.thenCompose(portfolio ->
                PortfolioFigures.usdRate(portfolio.needsUsdRate(), currencyView, fxRateFuture, investments, today));
        CompletableFuture<List<UpcomingPaymentView>> upcomingFuture =
                banks.fetchUpcomingPayments(userId, today, today.plusMonths(12));
        CompletableFuture<List<DownstreamPayload>> accountsFuture = banks.fetchAccounts(userId)
                .thenApply(rows -> DownstreamPayload.rows(ACCOUNTS_SOURCE, rows));
        CompletableFuture<List<Map<String, Object>>> cardsFuture = banks.fetchCards(userId);
        CompletableFuture<List<LoanWithSchedule>> enrichedLoansFuture = banks.fetchLoans(userId).thenCompose(
                loans -> LoanScheduleSupport.enrich(loans, loanId -> banks.fetchLoanInstallments(userId, loanId)));
        CompletableFuture<List<CurrencySummary>> summaryFuture = finances.fetchSummary(userId, yearStart, today);
        CompletableFuture<Map<String, Object>> latestFuture = finances.fetchTransactions(
                userId, new TransactionQuery(0, LATEST_MOVEMENTS, List.of(), List.of(), null, null, null, null));

        CompletableFuture<Section<OverviewKpis>> kpisSec = applyBudget(
                Section.guard(
                        CompletableFuture.allOf(summaryFuture, upcomingFuture, fxRateFuture)
                                .thenApply(v -> {
                                    List<CurrencySummary> summaries = summaryFuture.join();
                                    Optional<FxRate> fx = fxRateFuture.join();
                                    BigDecimal income = summaries.stream().map(s -> DownstreamPayload.amountOrZero(s.totalIncome())).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal expense = summaries.stream().map(s -> DownstreamPayload.amountOrZero(s.totalExpense())).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal balance = summaries.stream().map(s -> DownstreamPayload.amountOrZero(s.balance())).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal committed = upcomingFuture.join().stream().map(u -> DownstreamPayload.amountOrZero(u.amount())).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    return new OverviewKpis(
                                            BffMoneyConverter.convert(balance, Currency.ARS, currencyView, secondary, fx),
                                            BffMoneyConverter.convert(income, Currency.ARS, currencyView, secondary, fx),
                                            BffMoneyConverter.convert(expense, Currency.ARS, currencyView, secondary, fx),
                                            BffMoneyConverter.convert(committed, Currency.ARS, currencyView, secondary, fx));
                                }),
                        OverviewKpis.empty(), clock),
                OverviewKpis.empty());

        CompletableFuture<Section<NetWorth>> netWorthSec = applyBudget(
                Section.guard(
                        portfolioFuture.thenCombine(portfolioRateFuture, (portfolio, rate) -> {
                            MoneyFigure value = BffMoneyConverter.convert(
                                    BffMoneyConverter.toArs(portfolio.marketValue(), rate),
                                    Currency.ARS, currencyView, secondary, rate);
                            return new NetWorth(List.of(new NetWorthPoint(today, value)),
                                    new NetWorthDelta(value, BigDecimal.ZERO), true);
                        }),
                        NetWorth.empty(), clock),
                NetWorth.empty());

        CompletableFuture<Section<Breakdown>> breakdownSec = applyBudget(
                Section.guard(
                        CompletableFuture.allOf(portfolioFuture, portfolioRateFuture, accountsFuture, cardsFuture, enrichedLoansFuture, fxRateFuture)
                                .thenApply(v -> {
                                    Optional<FxRate> fx = fxRateFuture.join();
                                    Optional<FxRate> rate = portfolioRateFuture.join();
                                    BigDecimal invested = BffMoneyConverter.toArs(portfolioFuture.join().marketValue(), rate);
                                    List<DownstreamPayload> accounts = accountsFuture.join();
                                    BigDecimal savings = accounts.stream()
                                            .filter(account -> SAVINGS.equalsIgnoreCase(account.textOr("type", "")))
                                            .map(account -> account.decimalOrZero("balance"))
                                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal cash = accounts.stream()
                                            .filter(account -> !SAVINGS.equalsIgnoreCase(account.textOr("type", "")))
                                            .map(account -> account.decimalOrZero("balance"))
                                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal cardDebt = cardsFuture.join().stream().map(CardFigures::usedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal loanDebt = enrichedLoansFuture.join().stream()
                                            .map(loan -> LoanScheduleSupport.outstanding(loan.schedule()))
                                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                                    return new Breakdown(
                                            BffMoneyConverter.convert(invested, Currency.ARS, currencyView, secondary, rate),
                                            BffMoneyConverter.convert(cash, Currency.ARS, currencyView, secondary, fx),
                                            BffMoneyConverter.convert(cardDebt.add(loanDebt), Currency.ARS, currencyView, secondary, fx),
                                            BffMoneyConverter.convert(savings, Currency.ARS, currencyView, secondary, fx));
                                }),
                        Breakdown.empty(), clock),
                Breakdown.empty());

        CompletableFuture<Section<List<FlowPoint>>> flowSec = applyBudget(
                Section.guard(
                        finances.fetchMonthlyFlow(userId, today.minusMonths(12), today)
                                .thenCombine(fxRateFuture, (rows, fx) -> DownstreamPayload.rows(FLOW_SOURCE, rows).stream()
                                        .map(month -> new FlowPoint(
                                                month.textOr("month", ""),
                                                BffMoneyConverter.convert(month.decimalOrZero("income"), Currency.ARS, currencyView, secondary, fx),
                                                BffMoneyConverter.convert(month.decimalOrZero("expense"), Currency.ARS, currencyView, secondary, fx)))
                                        .toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<CommittedPoint>>> committedSec = applyBudget(
                Section.guard(
                        upcomingFuture.thenCombine(fxRateFuture, (list, fx) -> list.stream().map(u -> {
                            String month = u.dueDate() != null ? u.dueDate().toString().substring(0, 7) : "";
                            return new CommittedPoint(month, BffMoneyConverter.convert(
                                    DownstreamPayload.amountOrZero(u.amount()), Currency.ARS, currencyView, secondary, fx));
                        }).toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<UpcomingPayment>>> upcomingSec = applyBudget(
                Section.guard(
                        upcomingFuture.thenCombine(fxRateFuture, (list, fx) -> list.stream().map(u -> new UpcomingPayment(
                                u.id() != null ? u.id().toString() : "",
                                u.description() != null ? u.description() : "",
                                u.dueDate() != null ? u.dueDate() : today,
                                BffMoneyConverter.convert(DownstreamPayload.amountOrZero(u.amount()), Currency.ARS, currencyView, secondary, fx),
                                u.type() != null ? u.type() : "BILL")).toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<CategorySpend>>> spendSec = applyBudget(
                Section.guard(
                        finances.fetchSpendByCategory(userId, today.withDayOfMonth(1), today, "EXPENSE")
                                .thenCombine(fxRateFuture, (rows, fx) -> {
                                    List<DownstreamPayload> spends = DownstreamPayload.rows(SPEND_SOURCE, rows);
                                    Map<String, BigDecimal> totalByCurrency = spends.stream().collect(Collectors.groupingBy(
                                            spend -> spend.text("currency"),
                                            Collectors.reducing(BigDecimal.ZERO, spend -> spend.decimal("total"), BigDecimal::add)));
                                    return spends.stream().map(spend -> {
                                        BigDecimal total = spend.decimal("total");
                                        String currency = spend.text("currency");
                                        return new CategorySpend(
                                                spend.optionalLong("categoryId").orElse(null),
                                                spend.textOr("categoryName", ""),
                                                BffMoneyConverter.convert(total, Currency.of(currency), currencyView, secondary, fx),
                                                Percentages.percentOf(total, totalByCurrency.get(currency)));
                                    }).toList();
                                }),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<TransactionRow>>> movementsSec = applyBudget(
                Section.guard(
                        CompletableFuture.allOf(latestFuture, accountsFuture, fxRateFuture)
                                .thenApply(v -> {
                                    Map<String, String> labels = AccountLabels.byCbu(accountsFuture.join());
                                    Optional<FxRate> fx = fxRateFuture.join();
                                    return new DownstreamPayload(TRANSACTIONS_SOURCE, latestFuture.join()).list("content").stream()
                                            .map(transaction -> TransactionRows.from(transaction, labels, figure ->
                                                    BffMoneyConverter.convert(figure.amount(), figure.currency(), currencyView, secondary, fx)))
                                            .toList();
                                }),
                        List.of(), clock),
                List.of());

        return CompletableFuture.allOf(kpisSec, netWorthSec, breakdownSec, flowSec, committedSec, upcomingSec, spendSec, movementsSec)
                .thenApply(v -> new OverviewBffData(
                        kpisSec.join(), netWorthSec.join(), breakdownSec.join(), flowSec.join(),
                        committedSec.join(), upcomingSec.join(), spendSec.join(), movementsSec.join()));
    }

    private <T> CompletableFuture<Section<T>> applyBudget(CompletableFuture<Section<T>> sectionFuture, T fallback) {
        return sectionFuture.completeOnTimeout(
                Section.unavailable(fallback, ObservedAt.now(clock)),
                budget.total().toMillis(),
                TimeUnit.MILLISECONDS);
    }
}
