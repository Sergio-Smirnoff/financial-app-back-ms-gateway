package com.financialapp.gateway.infrastructure.config;

import com.financialapp.gateway.domain.common.model.TimeoutPolicy;
import com.financialapp.gateway.domain.model.composition.PageTimeoutBudget;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class ResilienceConfig {

    @Bean
    TimeoutPolicy timeoutPolicy(TimeoutProperties properties) {
        return new TimeoutPolicy(Duration.ofMillis(properties.getPerCallMs()));
    }

    @Bean
    PageTimeoutBudget pageTimeoutBudget(TimeoutProperties properties) {
        return PageTimeoutBudget.fromMillis(properties.getPageBudgetMs());
    }
}
