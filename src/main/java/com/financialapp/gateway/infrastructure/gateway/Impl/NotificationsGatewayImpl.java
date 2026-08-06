package com.financialapp.gateway.infrastructure.gateway.Impl;

import com.financialapp.gateway.domain.common.model.TimeoutPolicy;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.domain.gateway.NotificationsGateway;
import com.financialapp.gateway.infrastructure.config.ServicesProperties;
import com.financialapp.gateway.infrastructure.gateway.dto.GatewayApiResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Component
public class NotificationsGatewayImpl implements NotificationsGateway {

    private static final ParameterizedTypeReference<GatewayApiResponse<Map<String, Object>>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<GatewayApiResponse<List<Map<String, Object>>>> LIST_MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    private final WebClient webClient;
    private final String notificationsUrl;
    private final TimeoutPolicy timeoutPolicy;

    public NotificationsGatewayImpl(WebClient internalWebClient, ServicesProperties services, TimeoutPolicy timeoutPolicy) {
        this.webClient = internalWebClient;
        this.notificationsUrl = services.getNotificationsUrl();
        this.timeoutPolicy = timeoutPolicy;
    }

    @Override
    public CompletableFuture<Map<String, Object>> fetchUnreadCount(UserId userId) {
        return webClient.get()
                .uri(notificationsUrl + "/api/v1/notifications/unread-count")
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(MAP_TYPE)
                .map(r -> r.data() != null ? r.data() : Map.<String, Object>of())
                .onErrorReturn(Map.of())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> fetchLatest(UserId userId) {
        return webClient.get()
                .uri(notificationsUrl + "/api/v1/notifications/latest")
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(LIST_MAP_TYPE)
                .map(r -> r.data() != null ? r.data() : List.<Map<String, Object>>of())
                .onErrorReturn(List.of())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }

    @Override
    public CompletableFuture<List<Map<String, Object>>> fetchNotificationPreferences(UserId userId) {
        return webClient.get()
                .uri(notificationsUrl + "/api/v1/notifications/preferences/by-category")
                .header("X-User-Id", userId.value().toString())
                .retrieve()
                .bodyToMono(LIST_MAP_TYPE)
                .map(r -> r.data() != null ? r.data() : List.<Map<String, Object>>of())
                .onErrorReturn(List.of())
                .timeout(timeoutPolicy.perCall())
                .toFuture();
    }
}
