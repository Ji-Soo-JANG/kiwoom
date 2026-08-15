package com.example.kiwoom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class KiwoomApplication {

    public static void main(String[] args) {
        SpringApplication.run(
                KiwoomApplication.class,
                args
        );
    }
}
