package com.financialapp.gateway.domain.gateway;

import com.financialapp.gateway.domain.common.model.UserId;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface NotificationsGateway {

    CompletableFuture<Map<String, Object>> fetchUnreadCount(UserId userId);

    CompletableFuture<List<Map<String, Object>>> fetchLatest(UserId userId);

    CompletableFuture<List<Map<String, Object>>> fetchNotificationPreferences(UserId userId);

    CompletableFuture<List<Map<String, Object>>> fetchLatestByCategory(UserId userId, String category);
}
