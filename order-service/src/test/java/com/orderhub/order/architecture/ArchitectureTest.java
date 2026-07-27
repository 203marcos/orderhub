package com.orderhub.order.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * The architecture rules described in {@code docs/ARCHITECTURE.md}, as tests.
 *
 * <p>Prose in a README describes the architecture someone intended. These fail the build the
 * moment the code stops matching, which is the only version that stays true.
 */
@DisplayName("order-service architecture")
class ArchitectureTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.orderhub.order");
    }

    @Nested
    @DisplayName("service boundaries")
    class ServiceBoundaries {

        @Test
        @DisplayName("never reaches into another service's package")
        void shouldNotDependOnAnotherServicesPackage() {
            // Each service owns its schema and its model. Talking to payment-service means an
            // HTTP call or an event — never a compile-time dependency on its classes, which
            // would quietly turn two deployables into one.
            noClasses()
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.orderhub.payment..",
                            "com.orderhub.catalog..",
                            "com.orderhub.auth..",
                            "com.orderhub.notification..",
                            "com.orderhub.gateway..")
                    .check(classes);
        }

        @Test
        @DisplayName("has no package cycles")
        void shouldBeFreeOfPackageCycles() {
            slices()
                    .matching("com.orderhub.order.(*)..")
                    .should().beFreeOfCycles()
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("layering")
    class Layering {

        @Test
        @DisplayName("requests flow controller to service to repository, never backwards")
        void shouldRespectLayerDependencies() {
            Architectures.layeredArchitecture()
                    .consideringOnlyDependenciesInLayers()
                    .layer("Controller").definedBy("com.orderhub.order.controller..")
                    .layer("Service").definedBy("com.orderhub.order.service..")
                    .layer("Persistence").definedBy("com.orderhub.order.repository..")

                    .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
                    .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
                    .whereLayer("Persistence").mayOnlyBeAccessedByLayers("Service")
                    .check(classes);
        }

        @Test
        @DisplayName("controllers never touch repositories directly")
        void shouldKeepControllersOutOfPersistence() {
            // Skipping the service layer is how transaction boundaries and business rules get
            // bypassed one "quick fix" at a time.
            noClasses()
                    .that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..repository..")
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("persistence")
    class Persistence {

        @Test
        @DisplayName("JPA entities never leave the service layer")
        void shouldNotExposeEntitiesFromControllers() {
            // Returning an entity from a controller leaks the database schema into the API and
            // makes every column rename a breaking change for clients.
            noClasses()
                    .that().resideInAPackage("..controller..")
                    .should().dependOnClassesThat().resideInAPackage("..entity..")
                    .check(classes);
        }

        @Test
        @DisplayName("repositories are Spring Data interfaces")
        void shouldOnlyHaveSpringDataRepositories() {
            classes()
                    .that().resideInAPackage("..repository..")
                    .should().beInterfaces()
                    .check(classes);
        }
    }

    @Nested
    @DisplayName("events")
    class Events {

        @Test
        @DisplayName("only the outbox publishes to Kafka")
        void shouldNotSendToKafkaOutsideTheOutbox() {
            // The whole point of the outbox is that no business code can send an event without
            // a transaction behind it. A stray KafkaTemplate injection would reopen the dual
            // write this design exists to close.
            noClasses()
                    .that().resideInAnyPackage("..service..", "..controller..")
                    .should().dependOnClassesThat()
                    .haveFullyQualifiedName("org.springframework.kafka.core.KafkaTemplate")
                    .check(classes);
        }
    }
}
