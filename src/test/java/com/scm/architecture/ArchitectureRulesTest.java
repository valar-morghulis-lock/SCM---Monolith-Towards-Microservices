package com.scm.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.core.KafkaTemplate;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.scm")
public class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule layer_dependencies_must_be_respected = layeredArchitecture()
            .consideringAllDependencies()
            .layer("Controllers").definedBy("..controllers..")
            .layer("Services").definedBy("..services..", "..relay..", "..consumers..")
            .layer("Repositories").definedBy("..repositories..")

            .whereLayer("Controllers").mayNotBeAccessedByAnyLayer()
            .whereLayer("Services").mayOnlyBeAccessedByLayers("Controllers", "Services")
            .whereLayer("Repositories").mayOnlyBeAccessedByLayers("Services", "Controllers")
            .allowEmptyShould(true);

    /**
     * Strict Domain Isolation Rule: Inventory Domain
     * Ensures that the entire inventory slice (including generated tables) cannot see orders,
     * but remains free to talk to the superior dashboard package.
     */
    @ArchTest
    static final ArchRule inventory_slice_isolation = noClasses()
            .that().resideInAPackage("com.scm.domains.inventory..")
            .should().dependOnClassesThat().resideInAPackage("com.scm.domains.orders..")
            .allowEmptyShould(true);

    /**
     * Strict Domain Isolation Rule: Orders Domain
     * Ensures that the entire orders slice cannot see inventory,
     * but remains free to talk to the superior dashboard package.
     */
    @ArchTest
    static final ArchRule orders_slice_isolation = noClasses()
            .that().resideInAPackage("com.scm.domains.orders..")
            .should().dependOnClassesThat().resideInAPackage("com.scm.domains.inventory..")
            .allowEmptyShould(true);

    /**
     * Direct Data Integrity Rule: Prevents mixing transactional boundaries with blocking Kafka operations.
     */
    @ArchTest
    static final ArchRule transactions_must_not_mix_with_kafka_io = noClasses()
            .that().areAnnotatedWith(Transactional.class)
            .should().dependOnClassesThat().areAssignableTo(KafkaTemplate.class)
            .allowEmptyShould(true);
}