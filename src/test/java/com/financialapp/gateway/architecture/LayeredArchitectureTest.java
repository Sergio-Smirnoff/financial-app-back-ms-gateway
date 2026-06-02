package com.financialapp.gateway.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

/**
 * Enforces DDD / hexagonal layer boundaries for the API Edge context. Dependencies point
 * inward: web -> application -> domain, infrastructure -> (application, domain). The domain
 * is free of framework imports — including Reactor, since async results are modelled with
 * java.util.concurrent.CompletableFuture, not Mono.
 *
 * Only classes inside the four layer packages are analyzed. Legacy flat packages (filter,
 * controller, aggregator, ...) are ignored until drained into the layers.
 */
@AnalyzeClasses(
        packages = "com.financialapp.gateway",
        importOptions = ImportOption.DoNotIncludeTests.class)
class LayeredArchitectureTest {

    @ArchTest
    static final ArchRule layers_respect_inward_dependency_flow = layeredArchitecture()
            .consideringOnlyDependenciesInLayers()
            .layer("Domain").definedBy("..gateway.domain..")
            .layer("Application").definedBy("..gateway.application..")
            .layer("Web").definedBy("..gateway.web..")
            .layer("Infrastructure").definedBy("..gateway.infrastructure..")
            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Web", "Infrastructure")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Web", "Infrastructure")
            .whereLayer("Web").mayNotBeAccessedByAnyLayer()
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer();

    @ArchTest
    static final ArchRule domain_is_free_of_framework_and_outer_layers = noClasses()
            .that().resideInAPackage("..gateway.domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..gateway.web..",
                    "..gateway.infrastructure..",
                    "org.springframework..",
                    "reactor.core..",
                    "jakarta.persistence..")
            .as("Domain must not depend on web, infrastructure, Spring, Reactor, or JPA");

    @ArchTest
    static final ArchRule application_does_not_depend_on_web = noClasses()
            .that().resideInAPackage("..gateway.application..")
            .should().dependOnClassesThat().resideInAPackage("..gateway.web..")
            .as("Application must not depend on the web layer");
}
