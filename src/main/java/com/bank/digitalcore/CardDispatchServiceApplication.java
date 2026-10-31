package com.bank.digitalcore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Starts the card dispatch tracking API and its partner adapters.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CardDispatchServiceApplication {
    /**
     * Boots the Spring application.
     */
    public static void main(String[] args) {
        SpringApplication.run(CardDispatchServiceApplication.class, args);
    }
}
