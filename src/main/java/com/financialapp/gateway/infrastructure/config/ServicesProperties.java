package com.financialapp.gateway.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "services")
public class ServicesProperties {
    private String usersUrl;
    private String financesUrl;
    private String banksUrl;
    private String notificationsUrl;
    private String uploadUrl;
    private String investmentsUrl;
}
