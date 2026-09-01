package com.financialapp.gateway.domain.usecase.bff;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.bff.InvestmentsBffData;
import com.financialapp.gateway.domain.model.currency.CurrencyView;

import java.util.concurrent.CompletableFuture;

public interface GetInvestmentsBffUseCase {
    CompletableFuture<InvestmentsBffData> execute(UserId userId, CurrencyView currencyView, String secondary);
}
