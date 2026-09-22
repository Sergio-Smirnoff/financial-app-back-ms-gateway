package com.financialapp.gateway.domain.usecase.bff;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.bff.TransactionQuery;
import com.financialapp.gateway.domain.model.bff.TransactionsBffData;
import com.financialapp.gateway.domain.model.currency.CurrencyView;

import java.util.concurrent.CompletableFuture;

public interface GetTransactionsBffUseCase {
    CompletableFuture<TransactionsBffData> execute(
            UserId userId, TransactionQuery query, CurrencyView currencyView, String secondary);
}
