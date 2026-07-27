package com.orderhub.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The shared outbox lives outside this service's base package, so component scanning is
 * widened to reach it. Entity and repository scanning are widened too — in
 * {@code PersistenceConfig}, so that web slice tests are not dragged into needing a database.
 */
@SpringBootApplication(scanBasePackages = {"com.orderhub.payment", "com.orderhub.common"})
@EnableScheduling // drives the outbox relay — see OutboxPublisher
public class PaymentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
