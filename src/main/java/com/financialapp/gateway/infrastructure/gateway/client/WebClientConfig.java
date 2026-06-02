package com.financialapp.gateway.infrastructure.gateway.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    WebClient internalWebClient(@Value("${INTERNAL_AUTH_TOKEN:}") String internalToken) {
        return WebClient.builder()
                .defaultHeader("X-Internal-Token", internalToken)
                .build();
    }
}
