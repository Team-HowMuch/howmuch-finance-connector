package com.amateur.howmuchfinanceconnector.infra.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("connector.internal")
public record InternalSecurityProperties(String serviceToken) {
    public InternalSecurityProperties {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalArgumentException("connector.internal.service-token is required");
        }
    }
}
