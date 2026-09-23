package com.orderhub.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// KafkaReliabilityConfig lives in the shared module, outside this service's base package.
@SpringBootApplication(scanBasePackages = {"com.orderhub.notification", "com.orderhub.common.kafka"})
public class NotificationServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
