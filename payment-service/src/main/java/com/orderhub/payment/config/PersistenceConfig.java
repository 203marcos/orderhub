package com.orderhub.payment.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Widens entity and repository scanning to take in the shared outbox, which lives outside this
 * service's base package. Spring Boot's defaults only reach {@code com.orderhub.payment}.
 *
 * <p>Deliberately a separate class rather than annotations on {@code PaymentServiceApplication}:
 * on the application class these would be user configuration and so would apply to every
 * slice test too, and a {@code @WebMvcTest} has no {@code EntityManagerFactory} to give them.
 * Here, the slice's type filter leaves the class out and the slice stays lightweight.
 */
@Configuration
@EntityScan({"com.orderhub.payment.entity", "com.orderhub.common.outbox"})
@EnableJpaRepositories({"com.orderhub.payment.repository", "com.orderhub.common.outbox"})
public class PersistenceConfig {
}
