package com.financialapp.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "services")
public class ServicesProperties {
    private String financesUrl;
    private String cardsUrl;
    private String notificationsUrl;
    private String investmentsUrl;
}
