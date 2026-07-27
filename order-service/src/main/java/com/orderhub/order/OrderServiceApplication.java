package com.orderhub.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * The shared outbox lives outside this service's base package, so component scanning is
 * widened to reach it. Entity and repository scanning are widened too — in
 * {@code PersistenceConfig}, so that web slice tests are not dragged into needing a database.
 */
@SpringBootApplication(scanBasePackages = {"com.orderhub.order", "com.orderhub.common"})
@EnableFeignClients
@EnableScheduling // drives the outbox relay — see OutboxPublisher
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
