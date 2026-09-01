package com.financialapp.gateway.infrastructure.gateway.Impl;

import com.financialapp.gateway.domain.common.model.TimeoutPolicy;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.UploadGateway;
import com.financialapp.gateway.infrastructure.config.ServicesProperties;
import com.financialapp.gateway.infrastructure.gateway.dto.GatewayApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class UploadGatewayImpl implements UploadGateway {

    private static final ParameterizedTypeReference<GatewayApiResponse<Map<String, Object>>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<GatewayApiResponse<List<Map<String, Object>>>> LIST_MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final String uploadUrl;
    private final TimeoutPolicy timeoutPolicy;

    public UploadGatewayImpl(WebClient internalWebClient, ServicesProperties services, TimeoutPolicy timeoutPolicy) {
        this.webClient = internalWebClient;
        this.uploadUrl = services.getUploadUrl();
        this.timeoutPolicy = timeoutPolicy;
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> fetchHistory(UserId userId) {
        return webClient.get()
                .uri(uploadUrl + "/api/v1/upload/history")
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(LIST_MAP_TYPE)
                .map(r -> r.data() != null ? r.data() : List.<Map<String, Object>>of())
                .onErrorReturn(List.of())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    @Override
    public CompletableFuture<Map<String, Object>> fetchRunDetail(UserId userId, Long runId) {
        return webClient.get()
                .uri(uploadUrl + "/api/v1/upload/runs/{runId}", runId)
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .map(r -> r.data() != null ? r.data() : Map.<String, Object>of())
                .onErrorReturn(Map.of())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    @Override
    public CompletableFuture<Map<String, Object>> fetchRunByTransaction(UserId userId, Long transactionId) {
        return webClient.get()
                .uri(uploadUrl + "/api/v1/upload/runs/by-transaction/{transactionId}", transactionId)
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .map(r -> r.data() != null ? r.data() : Map.<String, Object>of())
                .onErrorReturn(Map.of())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }
}
