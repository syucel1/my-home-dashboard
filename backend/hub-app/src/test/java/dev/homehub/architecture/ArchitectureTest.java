package dev.homehub.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Module boundaries from design/01-architecture-overview.md section 6, enforced on every build.
 *
 * <pre>
 *   domain  &lt;-  sdk  &lt;-  core  &lt;-  persistence / web  &lt;-  app
 *                 ^
 *              drivers (may only use sdk + domain)
 * </pre>
 */
@AnalyzeClasses(packages = "dev.homehub", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    private static final String DOMAIN = "dev.homehub.domain..";
    private static final String SDK = "dev.homehub.sdk..";
    private static final String CORE = "dev.homehub.core..";
    private static final String PERSISTENCE = "dev.homehub.persistence..";
    private static final String WEB = "dev.homehub.web..";
    private static final String APP = "dev.homehub.app..";
    private static final String DRIVERS = "dev.homehub.driver..";
    private static final String SPRING = "org.springframework..";

    @ArchTest
    static final ArchRule domainIsPlainJava = noClasses()
            .that()
            .resideInAPackage(DOMAIN)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(SDK, CORE, PERSISTENCE, WEB, APP, DRIVERS, SPRING)
            .because("the domain model is the framework-free internal language of the hub");

    @ArchTest
    static final ArchRule sdkDependsOnDomainOnly = noClasses()
            .that()
            .resideInAPackage(SDK)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(CORE, PERSISTENCE, WEB, APP, DRIVERS, SPRING)
            .because("drivers compile against the SDK alone, so it must stay small and stable");

    @ArchTest
    static final ArchRule coreIsIndependentOfInfrastructure = noClasses()
            .that()
            .resideInAPackage(CORE)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(PERSISTENCE, WEB, APP, DRIVERS)
            .because("core services must not know about storage, HTTP or concrete drivers");

    @ArchTest
    static final ArchRule driversUseOnlyTheSdk = noClasses()
            .that()
            .resideInAPackage(DRIVERS)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(CORE, PERSISTENCE, WEB, APP)
            .because("drivers are plug-ins and talk to the hub through the Driver SDK only (ADR-004)");

    @ArchTest
    static final ArchRule webDoesNotTouchPersistence = noClasses()
            .that()
            .resideInAPackage(WEB)
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(PERSISTENCE)
            .because("the API goes through core services, never straight to the database");
}
