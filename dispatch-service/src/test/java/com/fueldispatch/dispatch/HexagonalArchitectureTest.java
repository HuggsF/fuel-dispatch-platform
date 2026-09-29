package com.fueldispatch.dispatch;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fueldispatch.dispatch.archfixture.application.service.CleanService;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * DOM-4.1 and DOM-4.2: the hexagonal dependency rules of specs/steering/structure.md.
 *
 * <p>Layers that are still empty would pass any rule, so each rule is also checked against the
 * deliberately broken classes in {@code archfixture} to prove it catches violations.
 */
class HexagonalArchitectureTest {

    /** DOM-4.1: the domain depends on the JDK only. */
    static final ArchRule DOMAIN_DEPENDS_ONLY_ON_JDK =
            classes()
                    .that()
                    .resideInAPackage("..domain..")
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage("java..", "..domain..");

    /** The application layer never reaches outwards to adapters or wiring. */
    static final ArchRule APPLICATION_DOES_NOT_DEPEND_ON_ADAPTERS_OR_CONFIG =
            noClasses()
                    .that()
                    .resideInAPackage("..application..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..adapter..", "..config..")
                    .allowEmptyShould(true);

    /** Ports are framework-free: JDK, domain and application only. */
    static final ArchRule APPLICATION_PORTS_ARE_FRAMEWORK_FREE =
            classes()
                    .that()
                    .resideInAPackage("..application..")
                    .and()
                    .resideOutsideOfPackage("..application.service..")
                    .should()
                    .onlyDependOnClassesThat()
                    .resideInAnyPackage("java..", "..domain..", "..application..")
                    .allowEmptyShould(true);

    /** Services may additionally use {@code @Service} and {@code @Transactional}, nothing else. */
    static final ArchRule APPLICATION_SERVICES_USE_ONLY_ALLOWED_FRAMEWORK_TYPES =
            classes()
                    .that()
                    .resideInAPackage("..application.service..")
                    .should()
                    .onlyDependOnClassesThat(
                            resideInAnyPackage("java..", "..domain..", "..application..")
                                    .or(name("org.springframework.stereotype.Service"))
                                    .or(
                                            name(
                                                    "org.springframework.transaction.annotation.Transactional")))
                    .allowEmptyShould(true);

    /** Adapters talk to the application through its ports only. */
    static final ArchRule ADAPTERS_DO_NOT_DEPEND_ON_SERVICES_OR_CONFIG =
            noClasses()
                    .that()
                    .resideInAPackage("..adapter..")
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage("..application.service..", "..config..")
                    .allowEmptyShould(true);

    /** Each adapter (in.web, out.persistence, ...) is independent of the others. */
    static final ArchRule ADAPTERS_DO_NOT_DEPEND_ON_EACH_OTHER =
            slices().matching("..adapter.(*).(*)..")
                    .should()
                    .notDependOnEachOther()
                    .allowEmptyShould(true);

    private static final JavaClasses PRODUCTION_CLASSES =
            new ClassFileImporter()
                    .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                    .importPackages("com.fueldispatch.dispatch");

    @Test
    void domain_dependsOnlyOnJdk() {
        DOMAIN_DEPENDS_ONLY_ON_JDK.check(PRODUCTION_CLASSES);
    }

    @Test
    void application_doesNotDependOnAdaptersOrConfig() {
        APPLICATION_DOES_NOT_DEPEND_ON_ADAPTERS_OR_CONFIG.check(PRODUCTION_CLASSES);
    }

    @Test
    void applicationPorts_areFrameworkFree() {
        APPLICATION_PORTS_ARE_FRAMEWORK_FREE.check(PRODUCTION_CLASSES);
    }

    @Test
    void applicationServices_useOnlyAllowedFrameworkTypes() {
        APPLICATION_SERVICES_USE_ONLY_ALLOWED_FRAMEWORK_TYPES.check(PRODUCTION_CLASSES);
    }

    @Test
    void adapters_doNotDependOnServicesOrConfig() {
        ADAPTERS_DO_NOT_DEPEND_ON_SERVICES_OR_CONFIG.check(PRODUCTION_CLASSES);
    }

    @Test
    void adapters_doNotDependOnEachOther() {
        ADAPTERS_DO_NOT_DEPEND_ON_EACH_OTHER.check(PRODUCTION_CLASSES);
    }

    /** Each rule must reject the matching deliberate violation in {@code archfixture}. */
    @Nested
    class RulesCatchViolations {

        private static final JavaClasses FIXTURES =
                new ClassFileImporter().importPackages("com.fueldispatch.dispatch.archfixture");

        @Test
        void domainRule_rejectsSpringInDomain() {
            assertThatThrownBy(() -> DOMAIN_DEPENDS_ONLY_ON_JDK.check(FIXTURES))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("SpringAwareEntity");
        }

        @Test
        void applicationRule_rejectsServiceDependingOnAdapter() {
            assertThatThrownBy(
                            () -> APPLICATION_DOES_NOT_DEPEND_ON_ADAPTERS_OR_CONFIG.check(FIXTURES))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("LeakyService");
        }

        @Test
        void portsRule_rejectsFrameworkAnnotationOnPort() {
            assertThatThrownBy(() -> APPLICATION_PORTS_ARE_FRAMEWORK_FREE.check(FIXTURES))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("FrameworkPort");
        }

        @Test
        void servicesRule_rejectsServiceDependingOnAdapter() {
            assertThatThrownBy(
                            () ->
                                    APPLICATION_SERVICES_USE_ONLY_ALLOWED_FRAMEWORK_TYPES.check(
                                            FIXTURES))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("LeakyService");
        }

        @Test
        void servicesRule_allowsServiceAnnotation() {
            JavaClasses cleanService = new ClassFileImporter().importClasses(CleanService.class);

            assertThatCode(
                            () ->
                                    APPLICATION_SERVICES_USE_ONLY_ALLOWED_FRAMEWORK_TYPES.check(
                                            cleanService))
                    .doesNotThrowAnyException();
        }

        @Test
        void adapterRule_rejectsAdapterDependingOnService() {
            assertThatThrownBy(() -> ADAPTERS_DO_NOT_DEPEND_ON_SERVICES_OR_CONFIG.check(FIXTURES))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("WebAdapter");
        }

        @Test
        void slicesRule_rejectsAdapterDependingOnAnotherAdapter() {
            assertThatThrownBy(() -> ADAPTERS_DO_NOT_DEPEND_ON_EACH_OTHER.check(FIXTURES))
                    .isInstanceOf(AssertionError.class)
                    .hasMessageContaining("WebAdapter")
                    .hasMessageContaining("PersistenceAdapter");
        }
    }
}
