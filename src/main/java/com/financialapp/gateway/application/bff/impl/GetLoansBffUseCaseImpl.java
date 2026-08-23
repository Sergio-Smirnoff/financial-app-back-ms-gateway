package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.application.bff.impl.LoanScheduleSupport.ParsedInstallment;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.AccountOption;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.LoanDetailRow;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.LoansKpis;
import com.financialapp.gateway.domain.model.bff.LoansBffData;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.model.currency.FxRate;
import com.financialapp.gateway.domain.service.BffMoneyConverter;
import com.financialapp.gateway.domain.usecase.bff.GetLoansBffUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GetLoansBffUseCaseImpl implements GetLoansBffUseCase {

    private final BanksGateway banks;
    private final InvestmentsGateway investments;
    private final PageTimeoutBudget budget;
    private final Clock clock;

    @Autowired
    public GetLoansBffUseCaseImpl(BanksGateway banks, InvestmentsGateway investments, PageTimeoutBudget budget) {
        this(banks, investments, budget, Clock.systemUTC());
    }

    public GetLoansBffUseCaseImpl(BanksGateway banks, InvestmentsGateway investments, PageTimeoutBudget budget, Clock clock) {
        this.banks = banks;
        this.investments = investments;
        this.budget = budget != null ? budget : PageTimeoutBudget.fromMillis(5000);
        this.clock = clock;
    }

    @Override
    public CompletableFuture<LoansBffData> execute(UserId userId, CurrencyView currencyView, String secondary) {
        LocalDate today = LocalDate.now(clock);

        CompletableFuture<Optional<FxRate>> fxRateFuture = currencyView != CurrencyView.ARS ?
                investments.fetchFxRate(currencyView, today) : CompletableFuture.completedFuture(Optional.empty());

        CompletableFuture<List<Map<String, Object>>> loansFuture = banks.fetchLoans(userId);
        CompletableFuture<List<Map<String, Object>>> accountsFuture = banks.fetchAccounts(userId);

        CompletableFuture<List<LoanWithSchedule>> enrichedFuture = enrich(userId, loansFuture);

        CompletableFuture<Section<List<LoanDetailRow>>> loansSec = applyBudget(
                Section.guard(
                        enrichedFuture.thenCombine(fxRateFuture, (enriched, fx) -> enriched.stream()
                                .map(e -> toDetailRow(e, currencyView, secondary, fx))
                                .toList()),
                        List.of(), clock),
                List.of());

        CompletableFuture<Section<LoansKpis>> kpisSec = applyBudget(
                Section.guard(
                        enrichedFuture.thenCombine(fxRateFuture, (enriched, fx) -> toKpis(enriched, currencyView, secondary, fx)),
                        LoansKpis.empty(), clock),
                LoansKpis.empty());

        CompletableFuture<Section<List<AccountOption>>> payFromAccountsSec = applyBudget(
                Section.guard(
                        accountsFuture.thenApply(list -> list.stream()
                                .map(a -> new AccountOption(
                                        String.valueOf(a.getOrDefault("cbu", "")),
                                        String.valueOf(a.getOrDefault("alias", ""))))
                                .toList()),
                        List.of(), clock),
                List.of());

        return CompletableFuture.allOf(kpisSec, loansSec, payFromAccountsSec)
                .thenApply(v -> new LoansBffData(kpisSec.join(), loansSec.join(), payFromAccountsSec.join()));
    }

    private record LoanWithSchedule(Map<String, Object> loan, List<ParsedInstallment> schedule) {}

    private CompletableFuture<List<LoanWithSchedule>> enrich(UserId userId, CompletableFuture<List<Map<String, Object>>> loansFuture) {
        return loansFuture.thenCompose(loans -> {
            List<CompletableFuture<LoanWithSchedule>> perLoan = loans.stream()
                    .map(l -> banks.fetchLoanInstallments(userId, parseLong(l.get("id")))
                            .thenApply(raw -> new LoanWithSchedule(l, LoanScheduleSupport.parse(raw))))
                    .toList();
            return CompletableFuture.allOf(perLoan.toArray(CompletableFuture[]::new))
                    .thenApply(v -> perLoan.stream().map(CompletableFuture::join).toList());
        });
    }

    private static LoanDetailRow toDetailRow(LoanWithSchedule enriched, CurrencyView currencyView, String secondary, Optional<FxRate> fx) {
        Map<String, Object> loan = enriched.loan();
        Currency currency = Currency.of(String.valueOf(loan.getOrDefault("currency", "ARS")));
        Optional<ParsedInstallment> next = LoanScheduleSupport.nextUnpaid(enriched.schedule());
        int total = parseInt(loan.get("totalInstallments"), 0);
        int remaining = parseInt(loan.get("remainingInstallments"), 0);

        return new LoanDetailRow(
                parseLong(loan.get("id")),
                String.valueOf(loan.getOrDefault("name", "")),
                String.valueOf(loan.getOrDefault("bankNumber", "")),
                BffMoneyConverter.convert(parseDecimal(loan.get("principal")), currency, currencyView, secondary, fx),
                BffMoneyConverter.convert(LoanScheduleSupport.outstanding(enriched.schedule()), currency, currencyView, secondary, fx),
                parseDecimal(loan.get("interestRate")),
                total - remaining,
                total,
                next.map(ParsedInstallment::dueDate).orElse(null),
                next.map(i -> BffMoneyConverter.convert(i.amount(), currency, currencyView, secondary, fx)).orElse(null),
                Boolean.TRUE.equals(loan.get("active")));
    }

    private static LoansKpis toKpis(List<LoanWithSchedule> enriched, CurrencyView currencyView, String secondary, Optional<FxRate> fx) {
        List<LoanWithSchedule> active = enriched.stream()
                .filter(e -> Boolean.TRUE.equals(e.loan().get("active")))
                .toList();

        BigDecimal totalOutstanding = active.stream()
                .map(e -> LoanScheduleSupport.outstanding(e.schedule()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ParsedInstallment> nextInstallments = active.stream()
                .map(e -> LoanScheduleSupport.nextUnpaid(e.schedule()))
                .flatMap(Optional::stream)
                .toList();

        BigDecimal monthlyPayment = nextInstallments.stream()
                .map(ParsedInstallment::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate nextDueDate = nextInstallments.stream()
                .map(ParsedInstallment::dueDate)
                .min(Comparator.naturalOrder())
                .orElse(null);

        return new LoansKpis(
                BffMoneyConverter.convert(totalOutstanding, Currency.ARS, currencyView, secondary, fx),
                BffMoneyConverter.convert(monthlyPayment, Currency.ARS, currencyView, secondary, fx),
                active.size(),
                nextDueDate);
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
}
