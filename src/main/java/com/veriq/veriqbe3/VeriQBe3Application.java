package com.veriq.veriqbe3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class VeriQBe3Application {

    public static void main(String[] args) {
        SpringApplication.run(VeriQBe3Application.class, args);
    }

}
