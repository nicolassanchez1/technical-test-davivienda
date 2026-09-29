package io.github.nicolassanchez1.technicaltestdavivienda.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * The ports-and-adapters boundaries only hold if something enforces them. These rules are
 * what keep the search SQL, the storage and the message broker replaceable.
 */
@AnalyzeClasses(packages = HexagonalBoundariesTest.ROOT, importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalBoundariesTest {

    static final String ROOT = "io.github.nicolassanchez1.technicaltestdavivienda";

    @ArchTest
    static final ArchRule domainDependsOnNothingElse = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..application..", "..infrastructure..", "..web..", "org.springframework..")
            .because("the domain must stay framework free and unaware of its adapters")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule applicationDoesNotReachIntoAdapters = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..infrastructure..", "..web..")
            .because("use cases talk to ports, never to a concrete adapter")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule controllersStayInWebPackages = noClasses()
            .that()
            .areAnnotatedWith(org.springframework.web.bind.annotation.RestController.class)
            .should()
            .resideOutsideOfPackages("..web..", "..shared..")
            .because("HTTP concerns belong to the inbound adapter")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule noFieldInjection = noFields()
            .should()
            .beAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class)
            .because("constructor injection makes dependencies explicit and objects testable")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule noPersistenceFramework = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("jakarta.persistence..", "org.hibernate..")
            .because("the search SQL must stay explicit and EXPLAIN-able")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule noLombok = noClasses()
            .should()
            .dependOnClassesThat()
            .resideInAPackage("lombok..")
            .because("generated members hide the real shape of a class")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule mutableStateStaysOutOfSharedConstants = fields().that()
            .areStatic()
            .and()
            .arePublic()
            .should()
            .beFinal()
            .because("shared mutable static state is not safe under virtual threads")
            .allowEmptyShould(true);
}
