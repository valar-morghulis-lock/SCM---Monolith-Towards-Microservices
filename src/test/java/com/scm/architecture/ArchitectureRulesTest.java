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
            .whereLayer("Repositories").mayOnlyBeAccessedByLayers("Services")
            .allowEmptyShould(true); // Prevents builds failing if a domain lacks a layer (like Inventory repositories)

    @ArchTest
    static final ArchRule domains_must_be_isolated_from_each_other = noClasses()
            .that().resideInAPackage("..domains.orders..")
            .should().dependOnClassesThat().resideInAPackage("..domains.inventory..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule transactions_must_not_mix_with_kafka_io = noClasses()
            .that().areAnnotatedWith(Transactional.class)
            .should().dependOnClassesThat().areAssignableTo(KafkaTemplate.class)
            .allowEmptyShould(true);
}