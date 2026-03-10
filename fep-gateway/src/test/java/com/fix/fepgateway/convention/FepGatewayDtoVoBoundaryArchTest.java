package com.fix.fepgateway.convention;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "com.fix.fepgateway", importOptions = ImportOption.DoNotIncludeTests.class)
class FepGatewayDtoVoBoundaryArchTest {

  @ArchTest
  static final ArchRule controllerShouldNotDependOnRepository =
      noClasses()
          .that().resideInAnyPackage("..controller..", "..controlplane.controller..")
          .should().dependOnClassesThat().resideInAnyPackage("..repository..", "..repository.custom..");

  @ArchTest
  static final ArchRule controllerShouldNotDependOnEntity =
      noClasses()
          .that().resideInAnyPackage("..controller..", "..controlplane.controller..")
          .should().dependOnClassesThat().resideInAPackage("..entity..");

  @ArchTest
  static final ArchRule serviceShouldNotDependOnDto =
      noClasses()
          .that().resideInAPackage("..service..")
          .should().dependOnClassesThat().resideInAPackage("..dto..");

  @ArchTest
  static final ArchRule repositoryShouldNotDependOnDtoOrController =
      noClasses()
          .that().resideInAnyPackage("..repository..", "..repository.custom..")
          .should().dependOnClassesThat().resideInAnyPackage("..dto..", "..controller..", "..controlplane.controller..");

  @ArchTest
  static final ArchRule dtoShouldUseRecord =
      classes()
          .that().resideInAnyPackage("..dto.request..", "..dto.response..")
          .should(beRecord());

  @ArchTest
  static final ArchRule controllerPackagesShouldContainOnlyRestControllers =
      classes()
          .that().resideInAnyPackage("..controller..", "..controlplane.controller..")
          .should().beAnnotatedWith(RestController.class);

  @ArchTest
  static final ArchRule globalExceptionHandlerShouldLiveInExceptionPackage =
      classes()
          .that().haveSimpleName("GlobalExceptionHandler")
          .should().resideInAPackage("..exception..");

  private static ArchCondition<JavaClass> beRecord() {
    return new ArchCondition<>("be a record") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        if (!item.isRecord()) {
          events.add(SimpleConditionEvent.violated(item, item.getName() + " must be record, not class"));
        }
      }
    };
  }
}
