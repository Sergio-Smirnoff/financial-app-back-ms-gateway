package com.financialapp.gateway.domain.usecase.bff;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.bff.ImportsBffData;

import java.util.concurrent.CompletableFuture;

public interface GetImportsBffUseCase {
    CompletableFuture<ImportsBffData> execute(UserId userId);
}
