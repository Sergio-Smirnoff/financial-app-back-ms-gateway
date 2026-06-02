package com.financialapp.gateway.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "gateway.timeout")
public class TimeoutProperties {
    private long perCallMs = 3000;
}
