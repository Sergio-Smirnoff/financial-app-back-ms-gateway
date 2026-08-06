package com.financialapp.gateway.domain.gateway;

import com.financialapp.gateway.domain.common.model.UserId;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface UploadGateway {

    CompletableFuture<List<Map<String, Object>>> fetchHistory(UserId userId);

    CompletableFuture<Map<String, Object>> fetchRunDetail(UserId userId, Long runId);

    CompletableFuture<Map<String, Object>> fetchRunByTransaction(UserId userId, Long transactionId);
}
