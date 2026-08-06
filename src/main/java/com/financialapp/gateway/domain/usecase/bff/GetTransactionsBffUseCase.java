package com.financialapp.gateway.domain.usecase.bff;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.bff.TransactionsBffData;
import com.financialapp.gateway.domain.model.currency.CurrencyView;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface GetTransactionsBffUseCase {
    CompletableFuture<TransactionsBffData> execute(
            UserId userId, int page, int size, List<String> categories, List<String> accounts,
            LocalDate from, LocalDate to, CurrencyView currencyView, String secondary);
}
