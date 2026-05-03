package com.banking.handoff;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.banking.handoff.config.HandoffProperties;

@SpringBootApplication
@EnableConfigurationProperties(HandoffProperties.class)
public class HandoffGenerationApplication {

    public static void main(String[] args) {
        SpringApplication.run(HandoffGenerationApplication.class, args);
    }
}
