package com.financialapp.gateway.infrastructure.gateway.Impl;

import com.financialapp.gateway.contracts.DownstreamFixtures;
import com.financialapp.gateway.domain.common.model.TimeoutPolicy;
import com.financialapp.gateway.domain.common.model.UserId;
import com.financialapp.gateway.infrastructure.config.ServicesProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationsGatewayImplTest {

    @Test
    void latestOfTypeKeepsOnlyThatNotificationType() {
        WebClient webClient = WebClient.builder()
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .body("{\"status\":200,\"data\":" + DownstreamFixtures.json("notifications/latest.json") + "}")
                        .build()))
                .build();
        ServicesProperties services = new ServicesProperties();
        services.setNotificationsUrl("http://notifications.test");
        NotificationsGatewayImpl gateway = new NotificationsGatewayImpl(
                webClient, services, new TimeoutPolicy(Duration.ofSeconds(5)));

        List<Map<String, Object>> alerts = gateway.fetchLatestOfType(new UserId(1L), "INVESTMENT_THRESHOLD").join();

        assertThat(alerts).extracting(alert -> alert.get("id")).containsExactly(9);
    }
}
