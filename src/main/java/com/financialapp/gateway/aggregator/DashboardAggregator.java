package com.financialapp.gateway.aggregator;

import com.financialapp.gateway.config.ServicesProperties;
import com.financialapp.gateway.model.dto.DashboardSummaryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Collections;

@Slf4j
@Component
public class DashboardAggregator {

    private final WebClient webClient;
    private final ServicesProperties services;

    public DashboardAggregator(WebClient.Builder webClientBuilder, ServicesProperties services) {
        this.webClient = webClientBuilder.build();
        this.services  = services;
    }

    public Mono<DashboardSummaryResponse> getDashboardSummary(Long userId) {
        String userIdStr = userId.toString();

        Mono<Object> financeSummaryMono = webClient.get()
                .uri(services.getFinancesUrl() + "/api/v1/finances/transactions/summary")
                .header("X-User-Id", userIdStr)
                .retrieve()
                .bodyToMono(Object.class)
                .onErrorResume(ex -> {
                    log.warn("Finances service unavailable for dashboard: {}", ex.getMessage());
                    return Mono.just(Collections.emptyList());
                });

        Mono<Object> cardsMono = webClient.get()
                .uri(services.getCardsUrl() + "/api/v1/cards?active=true")
                .header("X-User-Id", userIdStr)
                .retrieve()
                .bodyToMono(Object.class)
                .onErrorResume(ex -> {
                    log.warn("Cards service unavailable for dashboard: {}", ex.getMessage());
                    return Mono.just(Collections.emptyList());
                });

        Mono<Object> notificationsMono = webClient.get()
                .uri(services.getNotificationsUrl() + "/api/v1/notifications?read=false&size=5")
                .header("X-User-Id", userIdStr)
                .retrieve()
                .bodyToMono(Object.class)
                .onErrorResume(ex -> {
                    log.warn("Notifications service unavailable for dashboard: {}", ex.getMessage());
                    return Mono.just(Collections.emptyList());
                });

        return Mono.zip(financeSummaryMono, cardsMono, notificationsMono)
                .map(tuple -> DashboardSummaryResponse.builder()
                        .financeSummary(tuple.getT1())
                        .cards(tuple.getT2())
                        .recentNotifications(tuple.getT3())
                        .build());
    }
}
