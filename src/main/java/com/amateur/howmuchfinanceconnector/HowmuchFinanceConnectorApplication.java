package com.amateur.howmuchfinanceconnector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@ConfigurationPropertiesScan
@SpringBootApplication
public class HowmuchFinanceConnectorApplication {

    public static void main(String[] args) {
        SpringApplication.run(HowmuchFinanceConnectorApplication.class, args);
    }
}
