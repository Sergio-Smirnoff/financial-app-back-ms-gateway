package com.financialapp.gateway.domain.usecase.bff;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.bff.SearchBffData;

import java.util.concurrent.CompletableFuture;

public interface GetSearchBffUseCase {
    CompletableFuture<SearchBffData> execute(UserId userId, String query);
}
