package com.veriq.veriqgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;




@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})

public class VeriQGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(VeriQGatewayApplication.class, args);
    }


}
