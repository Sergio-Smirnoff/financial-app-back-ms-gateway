package com.financialapp.gateway.domain.usecase.bff;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.bff.CategoriesBffData;
import com.financialapp.gateway.domain.model.currency.CurrencyView;

import java.util.concurrent.CompletableFuture;

public interface GetCategoriesBffUseCase {
    CompletableFuture<CategoriesBffData> execute(UserId userId, CurrencyView currencyView, String secondary);
}
