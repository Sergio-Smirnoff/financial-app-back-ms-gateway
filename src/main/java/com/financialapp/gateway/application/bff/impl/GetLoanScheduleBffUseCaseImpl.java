package com.financialapp.gateway.application.bff.impl;

import com.financialapp.gateway.application.bff.impl.LoanScheduleSupport.ParsedInstallment;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.BanksGateway;
import com.financialapp.gateway.domain.gateway.InvestmentsGateway;
import com.financialapp.gateway.domain.model.bff.BffDomainModels.InstallmentRow;
import com.financialapp.gateway.domain.model.bff.LoanScheduleBffData;
import com.financialapp.gateway.domain.model.composition.ObservedAt;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import com.financialapp.gateway.domain.model.composition.Section;
import com.financialapp.gateway.domain.model.currency.Currency;
import com.financialapp.gateway.domain.model.currency.CurrencyView;
import com.financialapp.gateway.domain.model.currency.FxRate;
import com.financialapp.gateway.domain.service.BffMoneyConverter;
import com.financialapp.gateway.domain.usecase.bff.GetLoanScheduleBffUseCase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class GetLoanScheduleBffUseCaseImpl implements GetLoanScheduleBffUseCase {

    private final BanksGateway banks;
    private final InvestmentsGateway investments;
    private final PageTimeoutBudget budget;
    private final Clock clock;

    @Autowired
    public GetLoanScheduleBffUseCaseImpl(BanksGateway banks, InvestmentsGateway investments, PageTimeoutBudget budget) {
        this(banks, investments, budget, Clock.systemUTC());
    }

    public GetLoanScheduleBffUseCaseImpl(BanksGateway banks, InvestmentsGateway investments, PageTimeoutBudget budget, Clock clock) {
        this.banks = banks;
        this.investments = investments;
        this.budget = budget != null ? budget : PageTimeoutBudget.fromMillis(5000);
        this.clock = clock;
    }

    @Override
    public CompletableFuture<LoanScheduleBffData> execute(UserId userId, Long loanId, CurrencyView currencyView, String secondary) {
        LocalDate today = LocalDate.now(clock);

        CompletableFuture<Optional<FxRate>> fxRateFuture = currencyView != CurrencyView.ARS ?
                investments.fetchFxRate(currencyView, today) : CompletableFuture.completedFuture(Optional.empty());

        CompletableFuture<Section<List<InstallmentRow>>> installmentsSec = applyBudget(
                Section.guard(
                        banks.fetchLoanInstallments(userId, loanId)
                                .thenApply(LoanScheduleSupport::parse)
                                .thenCombine(fxRateFuture, (schedule, fx) -> schedule.stream()
                                        .map(i -> toInstallmentRow(i, currencyView, secondary, fx))
                                        .toList()),
                        List.of(), clock),
                List.of());

        return installmentsSec.thenApply(LoanScheduleBffData::new);
    }

    private static InstallmentRow toInstallmentRow(ParsedInstallment installment, CurrencyView currencyView, String secondary, Optional<FxRate> fx) {
        return new InstallmentRow(
                installment.id(),
                installment.number(),
                BffMoneyConverter.convert(installment.amount(), Currency.ARS, currencyView, secondary, fx),
                installment.dueDate(),
                installment.paid(),
                installment.paidDate());
    }

    private <T> CompletableFuture<Section<T>> applyBudget(CompletableFuture<Section<T>> sectionFuture, T fallback) {
        return sectionFuture.completeOnTimeout(
                Section.unavailable(fallback, ObservedAt.now(clock)),
                budget.total().toMillis(),
                TimeUnit.MILLISECONDS);
    }
}
