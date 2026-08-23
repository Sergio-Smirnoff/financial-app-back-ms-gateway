package com.financialapp.gateway.domain.usecase.bff;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.bff.LoanScheduleBffData;
import com.financialapp.gateway.domain.model.currency.CurrencyView;

import java.util.concurrent.CompletableFuture;

public interface GetLoanScheduleBffUseCase {
    CompletableFuture<LoanScheduleBffData> execute(UserId userId, Long loanId, CurrencyView currencyView, String secondary);
}
