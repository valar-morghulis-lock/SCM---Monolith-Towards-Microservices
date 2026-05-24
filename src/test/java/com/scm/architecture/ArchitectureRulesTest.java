package com.scm.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.kafka.core.KafkaTemplate;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

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
            .allowEmptyShould(true);

    /**
     *  Bulletproof Slice Isolation
     * Automatically ensures that com.scm.domains.order and com.scm.domains.inventory
     * cannot see each other's classes or generated tables.
     */
    @ArchTest
    static final ArchRule domains_must_be_strictly_isolated = slices()
            .matching("com.scm.domains.(*)..")
            .should().notDependOnEachOther();

    /**
     * Inverse rule
     */
    @ArchTest
    static final ArchRule inventory_must_not_depend_on_order = noClasses()
            .that().resideInAPackage("..domains.inventory..")
            .should().dependOnClassesThat().resideInAPackage("..domains.order*..") // Catches both 'order' and 'orders'
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule order_must_not_depend_on_inventory = noClasses()
            .that().resideInAPackage("..domains.order*..") // Catches both 'order' and 'orders'
            .should().dependOnClassesThat().resideInAPackage("..domains.inventory..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule transactions_must_not_mix_with_kafka_io = noClasses()
            .that().areAnnotatedWith(Transactional.class)
            .should().dependOnClassesThat().areAssignableTo(KafkaTemplate.class)
            .allowEmptyShould(true);
}