package com.financialapp.gateway.domain.usecase.bff;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.bff.SettingsBffData;

import java.util.concurrent.CompletableFuture;

public interface GetSettingsBffUseCase {
    CompletableFuture<SettingsBffData> execute(UserId userId);
}
