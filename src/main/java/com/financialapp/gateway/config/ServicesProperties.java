package com.financialapp.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "services")
public class ServicesProperties {
    private String financesUrl;
    private String banksUrl;
    private String notificationsUrl;
    private String investmentsUrl;
}
