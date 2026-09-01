package com.financialapp.gateway.domain.usecase.bff;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.bff.TransactionDetailBffData;

import java.util.concurrent.CompletableFuture;

public interface GetTransactionDetailBffUseCase {
    CompletableFuture<TransactionDetailBffData> execute(UserId userId, Long transactionId);
}
