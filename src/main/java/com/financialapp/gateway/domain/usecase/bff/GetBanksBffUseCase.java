package com.financialapp.gateway.domain.usecase.bff;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.bff.BanksBffData;
import com.financialapp.gateway.domain.model.currency.CurrencyView;

import java.util.concurrent.CompletableFuture;

public interface GetBanksBffUseCase {
    CompletableFuture<BanksBffData> execute(UserId userId, CurrencyView currencyView, String secondary);
}
