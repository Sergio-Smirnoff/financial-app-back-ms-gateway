package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.application.bff.impl.LoanScheduleSupport.LoanWithSchedule;
import com.financialapp.gateway.application.bff.impl.LoanScheduleSupport.ParsedInstallment;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.gateway.UploadGateway;
import com.financialapp.gateway.domain.model.bff.BanksBffData;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.model.currency.FxRate;
import com.financialapp.gateway.domain.service.BffMoneyConverter;
import com.financialapp.gateway.domain.service.Percentages;
import com.financialapp.gateway.domain.usecase.bff.GetBanksBffUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GetBanksBffUseCaseImpl implements GetBanksBffUseCase {

    private static final String ACCOUNTS_SOURCE = "ms-banks accounts";
    private static final String CARDS_SOURCE = "ms-banks cards";
    private static final String LOANS_SOURCE = "ms-banks loans";
    private static final String HISTORY_SOURCE = "ms-upload history";
    private static final long STALE_AFTER_DAYS = 30;

    private final BanksGateway banks;
    private final InvestmentsGateway investments;
    private final UploadGateway upload;
    private final PageTimeoutBudget budget;
    private final Clock clock;

    @Autowired
    public GetBanksBffUseCaseImpl(
            BanksGateway banks, InvestmentsGateway investments,
            UploadGateway upload, PageTimeoutBudget budget) {
        this(banks, investments, upload, budget, Clock.systemUTC());
    }

    public GetBanksBffUseCaseImpl(
            BanksGateway banks, InvestmentsGateway investments,
            UploadGateway upload, PageTimeoutBudget budget, Clock clock) {
        this.banks = banks;
        this.investments = investments;
        this.upload = upload;
        this.budget = budget != null ? budget : PageTimeoutBudget.fromMillis(5000);
        this.clock = clock;
    }

    @Override
    public CompletableFuture<BanksBffData> execute(UserId userId, CurrencyView currencyView, String secondary) {
        LocalDate today = LocalDate.now(clock);
        Instant now = clock.instant();

        CompletableFuture<Optional<FxRate>> fxRateFuture = currencyView != CurrencyView.ARS ?
                investments.fetchFxRate(currencyView, today) : CompletableFuture.completedFuture(Optional.empty());

        CompletableFuture<List<DownstreamPayload>> accountsFuture = banks.fetchAccounts(userId)
                .thenApply(rows -> DownstreamPayload.rows(ACCOUNTS_SOURCE, rows));
        CompletableFuture<List<Map<String, Object>>> cardsFuture = banks.fetchCards(userId);
        CompletableFuture<List<DownstreamPayload>> historyFuture = upload.fetchHistory(userId)
                .thenApply(rows -> DownstreamPayload.rows(HISTORY_SOURCE, rows));
        CompletableFuture<List<LoanWithSchedule>> enrichedLoansFuture = banks.fetchLoans(userId).thenCompose(
                loans -> LoanScheduleSupport.enrich(loans, loanId -> banks.fetchLoanInstallments(userId, loanId)));

        CompletableFuture<Section<BanksKpis>> kpisSec = applyBudget(
                Section.guard(
                        CompletableFuture.allOf(accountsFuture, cardsFuture, enrichedLoansFuture, fxRateFuture)
                                .thenApply(v -> {
                                    Optional<FxRate> fx = fxRateFuture.join();
                                    List<DownstreamPayload> accounts = accountsFuture.join();
                                    BigDecimal totalCash = accounts.stream().map(account -> account.decimalOrZero("balance")).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal cardDebt = cardsFuture.join().stream().map(CardFigures::usedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal loanBalance = enrichedLoansFuture.join().stream()
                                            .map(loan -> LoanScheduleSupport.outstanding(loan.schedule()))
                                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                                    return new BanksKpis(
                                            BffMoneyConverter.convert(totalCash, Currency.ARS, currencyView, secondary, fx),
                                            BffMoneyConverter.convert(cardDebt, Currency.ARS, currencyView, secondary, fx),
                                            BffMoneyConverter.convert(loanBalance, Currency.ARS, currencyView, secondary, fx),
                                            accounts.size());
                                }),
                        BanksKpis.empty(), clock),
                BanksKpis.empty());

        CompletableFuture<Section<List<AccountRow>>> accountsSec = applyBudget(
                Section.guard(
                        accountsFuture.thenCombine(fxRateFuture, (accounts, fx) -> accounts.stream().map(account -> new AccountRow(
                                account.textOr("cbu", ""),
                                AccountLabels.of(account),
                                null,
                                account.textOr("type", ""),
                                BffMoneyConverter.convert(account.decimalOrZero("balance"),
                                        Currency.of(account.textOr("currency", "ARS")), currencyView, secondary, fx)))
                                .toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<CardRow>>> cardsSec = applyBudget(
                Section.guard(
                        cardsFuture.thenCombine(fxRateFuture, (cards, fx) -> cards.stream().map(raw -> {
                            DownstreamPayload card = new DownstreamPayload(CARDS_SOURCE, raw);
                            BigDecimal limit = card.decimalOrZero("creditLimit");
                            return new CardRow(
                                    card.textOr("cardNumber", ""),
                                    card.textOr("brand", ""),
                                    card.textOr("displayName", ""),
                                    limit,
                                    BffMoneyConverter.convert(CardFigures.usedAmount(raw), Currency.ARS, currencyView, secondary, fx),
                                    CardFigures.usedPercent(raw, limit),
                                    card.optionalDate("closingDate").orElse(today),
                                    card.optionalDate("dueDate").orElse(today));
                        }).toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<LoanRow>>> loansSec = applyBudget(
                Section.guard(
                        enrichedLoansFuture.thenCombine(fxRateFuture, (loans, fx) -> loans.stream().map(entry -> {
                            DownstreamPayload loan = new DownstreamPayload(LOANS_SOURCE, entry.loan());
                            Currency currency = Currency.of(loan.textOr("currency", "ARS"));
                            LocalDate nextDate = LoanScheduleSupport.nextUnpaid(entry.schedule()).map(ParsedInstallment::dueDate).orElse(null);
                            int total = loan.optionalLong("totalInstallments").map(Long::intValue).orElse(0);
                            int paid = total - loan.optionalLong("remainingInstallments").map(Long::intValue).orElse(0);
                            return new LoanRow(
                                    loan.optionalLong("id").orElse(null),
                                    loan.textOr("name", ""),
                                    loan.decimalOrZero("principal"),
                                    BffMoneyConverter.convert(LoanScheduleSupport.outstanding(entry.schedule()), currency, currencyView, secondary, fx),
                                    nextDate, paid, total);
                        }).toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<ImportHealthRow>>> importHealthSec = applyBudget(
                Section.guard(
                        accountsFuture.thenCombine(historyFuture, (accounts, history) -> accounts.stream().map(account -> {
                            String cbu = account.textOr("cbu", "");
                            String label = AccountLabels.of(account);
                            Optional<Instant> lastImport = history.stream()
                                    .filter(run -> cbu.equalsIgnoreCase(run.textOr("accountCbu", "")))
                                    .map(run -> run.instant("createdAt"))
                                    .max(Comparator.naturalOrder());
                            if (lastImport.isEmpty()) {
                                return new ImportHealthRow(cbu, label, null, null, ImportStatus.NEVER);
                            }
                            long daysSince = Duration.between(lastImport.get(), now).toDays();
                            ImportStatus status = daysSince <= STALE_AFTER_DAYS ? ImportStatus.OK : ImportStatus.STALE;
                            return new ImportHealthRow(cbu, label, lastImport.get(), daysSince, status);
                        }).toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<CompositionSlice>>> cashDistributionSec = applyBudget(
                Section.guard(
                        accountsFuture.thenCombine(fxRateFuture, (accounts, fx) -> {
                            BigDecimal total = accounts.stream().map(account -> account.decimalOrZero("balance")).reduce(BigDecimal.ZERO, BigDecimal::add);
                            return accounts.stream().map(account -> {
                                BigDecimal balance = account.decimalOrZero("balance");
                                return new CompositionSlice(AccountLabels.of(account),
                                        BffMoneyConverter.convert(balance, Currency.ARS, currencyView, secondary, fx),
                                        Percentages.percentOf(balance, total));
                            }).toList();
                        }),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<CalendarEntry>>> paymentCalendarSec = applyBudget(
                Section.guard(
                        banks.fetchUpcomingPayments(userId, today, today.plusMonths(1))
                                .thenCombine(fxRateFuture, (payments, fx) -> payments.stream().map(payment -> new CalendarEntry(
                                        payment.dueDate() != null ? payment.dueDate() : today,
                                        payment.description() != null ? payment.description() : "",
                                        BffMoneyConverter.convert(DownstreamPayload.amountOrZero(payment.amount()), Currency.ARS, currencyView, secondary, fx),
                                        payment.type() != null ? payment.type() : "BILL")).toList()),
                        List.of(), clock),
                List.of());

        return CompletableFuture.allOf(kpisSec, accountsSec, cardsSec, loansSec, importHealthSec, cashDistributionSec, paymentCalendarSec)
                .thenApply(v -> new BanksBffData(
                        kpisSec.join(), accountsSec.join(), cardsSec.join(), loansSec.join(),
                        importHealthSec.join(), cashDistributionSec.join(), paymentCalendarSec.join()));
    }

    private <T> CompletableFuture<Section<T>> applyBudget(CompletableFuture<Section<T>> sectionFuture, T fallback) {
        return sectionFuture.completeOnTimeout(
                Section.unavailable(fallback, ObservedAt.now(clock)),
                budget.total().toMillis(),
                TimeUnit.MILLISECONDS);
    }
}
