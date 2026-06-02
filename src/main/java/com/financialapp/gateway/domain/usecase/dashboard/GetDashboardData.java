package com.financialapp.gateway.domain.usecase.dashboard;

import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.model.dashboard.DashboardData;

import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;

public interface GetDashboardData {

    CompletableFuture<DashboardData> execute(
            UserId userId,
            LocalDate yearFrom, LocalDate yearTo,
            LocalDate monthFrom, LocalDate monthTo);
}
