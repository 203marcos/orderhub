package com.orderhub.common.outbox;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Guards the one rule that makes a shared library safe between microservices: it must stay
 * generic.
 *
 * <p>A shared module is where coupling creeps in. The moment someone adds an {@code Order} or
 * a {@code Payment} here "just to reuse it", every service that depends on this module is
 * transitively coupled to that service's model, and independent deployment is over. These
 * tests make that failure loud instead of gradual.
 */
@DisplayName("common-outbox stays free of domain")
class SharedModuleBoundaryTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.orderhub.common.outbox");
    }

    @Test
    @DisplayName("knows about no service's package")
    void shouldNotDependOnAnyServicePackage() {
        noClasses()
                .should().dependOnClassesThat()
                .resideInAnyPackage(
                        "com.orderhub.order..",
                        "com.orderhub.payment..",
                        "com.orderhub.catalog..",
                        "com.orderhub.auth..",
                        "com.orderhub.notification..",
                        "com.orderhub.gateway..")
                .check(classes);
    }

    @Test
    @DisplayName("asks nothing of a service beyond the DomainEvent interface")
    void shouldNotReferenceWebOrFeignConcerns() {
        // This module is persistence plus Kafka. Pulling in web or HTTP-client types here
        // would mean it had started doing a service's job.
        noClasses()
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.web..", "feign..")
                .check(classes);
    }
}
