package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.application.bff.impl.LoanScheduleSupport.LoanWithSchedule;
import com.financialapp.gateway.application.bff.impl.LoanScheduleSupport.ParsedInstallment;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.gateway.UploadGateway;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.*;
import com.financialapp.gateway.domain.model.bff.BanksBffData;
import com.financialapp.gateway.domain.model.bff.MoneyFigure;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.model.currency.FxRate;
import com.financialapp.gateway.domain.model.bff.UpcomingPaymentView;
import com.financialapp.gateway.domain.service.BffMoneyConverter;
import com.financialapp.gateway.domain.usecase.bff.GetBanksBffUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GetBanksBffUseCaseImpl implements GetBanksBffUseCase {

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
        Instant nowInstant = clock.instant();

        CompletableFuture<Optional<FxRate>> fxRateFuture = currencyView != CurrencyView.ARS ?
                investments.fetchFxRate(currencyView, today) : CompletableFuture.completedFuture(Optional.empty());

        // Deduplicated futures
        CompletableFuture<List<Map<String, Object>>> accountsFuture = banks.fetchAccounts(userId);
        CompletableFuture<List<Map<String, Object>>> cardsFuture = banks.fetchCards(userId);
        CompletableFuture<List<Map<String, Object>>> loansFuture = banks.fetchLoans(userId);
        CompletableFuture<List<Map<String, Object>>> uploadHistoryFuture = upload.fetchHistory(userId);
        CompletableFuture<List<LoanWithSchedule>> enrichedLoansFuture = loansFuture.thenCompose(
                loans -> LoanScheduleSupport.enrich(loans, loanId -> banks.fetchLoanInstallments(userId, loanId)));

        CompletableFuture<Section<BanksKpis>> kpisSec = applyBudget(
                Section.guard(
                        CompletableFuture.allOf(accountsFuture, cardsFuture, enrichedLoansFuture, fxRateFuture)
                                .thenApply(v -> {
                                    Optional<FxRate> fx = fxRateFuture.join();
                                    List<Map<String, Object>> accList = accountsFuture.join();
                                    BigDecimal totalCash = accList.stream().map(a -> parseDecimal(a.get("balance"))).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal cardDebt = cardsFuture.join().stream().map(c -> parseDecimal(c.get("usedBalance"))).reduce(BigDecimal.ZERO, BigDecimal::add);
                                    BigDecimal loanBalance = enrichedLoansFuture.join().stream()
                                            .map(e -> LoanScheduleSupport.outstanding(e.schedule()))
                                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                                    return new BanksKpis(
                                            BffMoneyConverter.convert(totalCash, Currency.ARS, currencyView, secondary, fx),
                                            BffMoneyConverter.convert(cardDebt, Currency.ARS, currencyView, secondary, fx),
                                            BffMoneyConverter.convert(loanBalance, Currency.ARS, currencyView, secondary, fx),
                                            accList.size()
                                    );
                                }),
                        BanksKpis.empty(), clock),
                BanksKpis.empty());

        CompletableFuture<Section<List<AccountRow>>> accountsSec = applyBudget(
                Section.guard(
                        accountsFuture.thenCombine(fxRateFuture, (list, fx) -> list.stream().map(a -> {
                            String cbu = String.valueOf(a.getOrDefault("cbu", ""));
                            String alias = String.valueOf(a.getOrDefault("alias", ""));
                            String bankName = String.valueOf(a.getOrDefault("bankName", ""));
                            String type = String.valueOf(a.getOrDefault("type", ""));
                            BigDecimal bal = parseDecimal(a.get("balance"));
                            Currency curr = Currency.of(String.valueOf(a.getOrDefault("currency", "ARS")));
                            return new AccountRow(cbu, alias, bankName, type, BffMoneyConverter.convert(bal, curr, currencyView, secondary, fx));
                        }).toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<CardRow>>> cardsSec = applyBudget(
                Section.guard(
                        cardsFuture.thenCombine(fxRateFuture, (list, fx) -> list.stream().map(c -> {
                            String num = String.valueOf(c.getOrDefault("cardNumber", ""));
                            String brand = String.valueOf(c.getOrDefault("brand", ""));
                            String alias = String.valueOf(c.getOrDefault("alias", ""));
                            BigDecimal limit = parseDecimal(c.get("creditLimit"));
                            BigDecimal used = parseDecimal(c.get("usedBalance"));
                            BigDecimal pct = limit.compareTo(BigDecimal.ZERO) > 0 ? used.divide(limit, 4, RoundingMode.HALF_EVEN).multiply(new BigDecimal("100")) : BigDecimal.ZERO;
                            LocalDate closing = parseDate(c.get("closingDate"));
                            LocalDate due = parseDate(c.get("dueDate"));
                            return new CardRow(num, brand, alias, limit, BffMoneyConverter.convert(used, Currency.ARS, currencyView, secondary, fx), pct, closing, due);
                        }).toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<LoanRow>>> loansSec = applyBudget(
                Section.guard(
                        enrichedLoansFuture.thenCombine(fxRateFuture, (list, fx) -> list.stream().map(e -> {
                            Map<String, Object> l = e.loan();
                            Long id = parseLong(l.get("id"));
                            String label = String.valueOf(l.getOrDefault("name", ""));
                            BigDecimal principal = parseDecimal(l.get("principal"));
                            BigDecimal outstanding = LoanScheduleSupport.outstanding(e.schedule());
                            Currency currency = Currency.of(String.valueOf(l.getOrDefault("currency", "ARS")));
                            LocalDate nextDate = LoanScheduleSupport.nextUnpaid(e.schedule()).map(ParsedInstallment::dueDate).orElse(null);
                            int total = parseInt(l.get("totalInstallments"), 0);
                            int paid = total - parseInt(l.get("remainingInstallments"), 0);
                            return new LoanRow(id, label, principal, BffMoneyConverter.convert(outstanding, currency, currencyView, secondary, fx), nextDate, paid, total);
                        }).toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<ImportHealthRow>>> importHealthSec = applyBudget(
                Section.guard(
                        accountsFuture.thenCombine(uploadHistoryFuture, (accList, history) -> accList.stream().map(a -> {
                            String cbu = String.valueOf(a.getOrDefault("cbu", ""));
                            String alias = String.valueOf(a.getOrDefault("alias", ""));

                            Optional<Map<String, Object>> newestRun = history.stream()
                                    .filter(h -> cbu.equalsIgnoreCase(String.valueOf(h.get("accountCbu"))))
                                    .max((h1, h2) -> parseInstant(h1.get("importedAt")).compareTo(parseInstant(h2.get("importedAt"))));

                            if (newestRun.isEmpty()) {
                                return new ImportHealthRow(cbu, alias, null, null, ImportStatus.NEVER);
                            } else {
                                Instant lastImportAt = parseInstant(newestRun.get().get("importedAt"));
                                long daysSince = Duration.between(lastImportAt, nowInstant).toDays();
                                ImportStatus status = daysSince <= 30 ? ImportStatus.OK : ImportStatus.STALE;
                                return new ImportHealthRow(cbu, alias, lastImportAt, daysSince, status);
                            }
                        }).toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<CompositionSlice>>> cashDistributionSec = applyBudget(
                Section.guard(
                        accountsFuture.thenCombine(fxRateFuture, (accList, fx) -> {
                            BigDecimal total = accList.stream().map(a -> parseDecimal(a.get("balance"))).reduce(BigDecimal.ZERO, BigDecimal::add);
                            return accList.stream().map(a -> {
                                String label = String.valueOf(a.getOrDefault("alias", a.getOrDefault("bankName", "")));
                                BigDecimal bal = parseDecimal(a.get("balance"));
                                BigDecimal pct = total.compareTo(BigDecimal.ZERO) > 0 ? bal.divide(total, 4, RoundingMode.HALF_EVEN).multiply(new BigDecimal("100")) : BigDecimal.ZERO;
                                return new CompositionSlice(label, BffMoneyConverter.convert(bal, Currency.ARS, currencyView, secondary, fx), pct);
                            }).toList();
                        }),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<List<CalendarEntry>>> paymentCalendarSec = applyBudget(
                Section.guard(
                        banks.fetchUpcomingPayments(userId, today, today.plusMonths(1))
                                .thenCombine(fxRateFuture, (payments, fx) -> payments.stream().map(p -> {
                                    LocalDate date = p.dueDate() != null ? p.dueDate() : today;
                                    String label = p.description() != null ? p.description() : "";
                                    BigDecimal amount = parseDecimal(p.amount());
                                    String kind = p.type() != null ? p.type() : "BILL";
                                    return new CalendarEntry(date, label, BffMoneyConverter.convert(amount, Currency.ARS, currencyView, secondary, fx), kind);
                                }).toList()),
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

    private static LocalDate parseDate(Object val) {
        if (val == null) return LocalDate.now();
        try { return LocalDate.parse(val.toString()); } catch (Exception e) { return LocalDate.now(); }
    }

    private static Instant parseInstant(Object val) {
        if (val == null) return Instant.EPOCH;
        try { return Instant.parse(val.toString()); } catch (Exception e) { return Instant.EPOCH; }
    }
}
