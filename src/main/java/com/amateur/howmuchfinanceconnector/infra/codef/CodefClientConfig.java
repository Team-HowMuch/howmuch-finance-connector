package com.amateur.howmuchfinanceconnector.infra.codef;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class CodefClientConfig {

    @Bean
    @Qualifier("codefApiRestClient")
    RestClient codefApiRestClient(RestClient.Builder builder, CodefProperties properties) {
        return builder.clone().baseUrl(properties.baseUrl()).build();
    }

    @Bean
    @Qualifier("codefOAuthRestClient")
    RestClient codefOAuthRestClient(RestClient.Builder builder) {
        return builder.clone().defaultHeader("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE).build();
    }
}
