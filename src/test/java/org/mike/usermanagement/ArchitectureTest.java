package org.mike.usermanagement;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.Architectures;
import jakarta.persistence.Entity;
import org.mapstruct.Mapper;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "org.mike.usermanagement", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    // Feature packages (e.g. user.web/domain/persistence, ratelimit.domain/persistence) each carry
    // the layer name in their package path, so a single "..web.." / "..domain.." / "..persistence.."
    // pattern applies uniformly across every feature without per-feature configuration.
    //
    // "Common" is narrowed to common.config/common.exception only — the genuinely layer-agnostic
    // subpackages. common.web (the shared @RestControllerAdvice) is presentation-layer shared code
    // per AGENTS.md, not the neutral commons package, and its own package path already matches
    // "..web.." — a blanket "..common.." pattern would otherwise double-classify it as both Common
    // and Web simultaneously.
    // Scoped to our own base package rather than consideringAllDependencies() — the latter treats
    // every dependency (including java.lang.Object, SLF4J, Spring framework classes) as relevant,
    // which floods a mayNotAccessAnyLayer()/mayOnlyBeAccessedByLayers() rule with false positives
    // for dependencies that have nothing to do with our own layering.
    @ArchTest
    static final ArchRule layers_are_respected = Architectures.layeredArchitecture()
            .consideringOnlyDependenciesInAnyPackage("org.mike.usermanagement..")
            .layer("Web")
            .definedBy("..web..")
            .layer("Domain")
            .definedBy("..domain..")
            .layer("Persistence")
            .definedBy("..persistence..")
            .layer("Common")
            .definedBy("..common.config..", "..common.exception..")
            .whereLayer("Web")
            .mayNotBeAccessedByAnyLayer()
            .whereLayer("Persistence")
            .mayOnlyBeAccessedByLayers("Domain", "Common")
            .whereLayer("Common")
            .mayNotAccessAnyLayer();

    @ArchTest
    static final ArchRule entities_contain_no_business_logic =
            classes().that().areAnnotatedWith(Entity.class).should(haveOnlyAccessorMethods());

    @ArchTest
    static final ArchRule no_classes_are_named_service = noClasses().should().haveSimpleNameEndingWith("Service");

    @ArchTest
    static final ArchRule spring_services_are_use_cases_or_facades = classes()
            .that()
            .areAnnotatedWith(Service.class)
            .should()
            .haveSimpleNameEndingWith("UseCase")
            .orShould()
            .haveSimpleNameEndingWith("Facade");

    @ArchTest
    static final ArchRule rest_controllers_expose_exactly_one_use_case_or_facade =
            classes().that().areAnnotatedWith(RestController.class).should(dependOnExactlyOneUseCaseOrFacade());

    @ArchTest
    static final ArchRule mappers_are_mapstruct_interfaces = classes()
            .that()
            .haveSimpleNameEndingWith("Mapper")
            .should()
            .beInterfaces()
            .andShould()
            .beAnnotatedWith(Mapper.class);

    private static ArchCondition<JavaClass> haveOnlyAccessorMethods() {
        return new ArchCondition<>("have only accessor methods, constructors, and equals/hashCode/toString") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaMethod method : javaClass.getMethods()) {
                    String name = method.getName();
                    boolean isAccessor = name.startsWith("get") || name.startsWith("is") || name.startsWith("set");
                    boolean isObjectOverride =
                            name.equals("equals") || name.equals("hashCode") || name.equals("toString");
                    if (!isAccessor && !isObjectOverride) {
                        events.add(SimpleConditionEvent.violated(
                                method,
                                String.format(
                                        "Entity %s declares business-logic method %s()",
                                        javaClass.getSimpleName(), name)));
                    }
                }
            }
        };
    }

    private static ArchCondition<JavaClass> dependOnExactlyOneUseCaseOrFacade() {
        return new ArchCondition<>("depend on exactly one use case or facade") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                long useCaseOrFacadeDependencies = javaClass.getDirectDependenciesFromSelf().stream()
                        .map(dependency -> dependency.getTargetClass().getSimpleName())
                        .filter(name -> name.endsWith("UseCase") || name.endsWith("Facade"))
                        .distinct()
                        .count();
                if (useCaseOrFacadeDependencies != 1) {
                    events.add(SimpleConditionEvent.violated(
                            javaClass,
                            String.format(
                                    "Controller %s depends on %d use cases/facades, expected exactly 1",
                                    javaClass.getSimpleName(), useCaseOrFacadeDependencies)));
                }
            }
        };
    }
}
