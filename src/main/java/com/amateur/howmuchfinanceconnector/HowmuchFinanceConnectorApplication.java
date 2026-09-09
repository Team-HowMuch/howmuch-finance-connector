package com.amateur.howmuchfinanceconnector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@ConfigurationPropertiesScan
@EnableScheduling
@SpringBootApplication
public class HowmuchFinanceConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(HowmuchFinanceConnectorApplication.class, args);
    }
}
