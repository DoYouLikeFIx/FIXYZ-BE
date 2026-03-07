package com.fix.fepsimulator.convention;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

@AnalyzeClasses(packages = "com.fix.fepsimulator")
class FepSimulatorDtoVoBoundaryArchTest {

  @ArchTest
  static final ArchRule controllerShouldNotDependOnRepository =
      noClasses()
          .that().resideInAPackage("..controller..")
          .should().dependOnClassesThat().resideInAnyPackage("..repository..", "..repository.custom..");

  @ArchTest
  static final ArchRule controllerShouldNotDependOnEntity =
      noClasses()
          .that().resideInAPackage("..controller..")
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
          .should().dependOnClassesThat().resideInAnyPackage("..dto..", "..controller..");

  @ArchTest
  static final ArchRule dtoShouldNotUseRecord =
      classes()
          .that().resideInAnyPackage("..dto.request..", "..dto.response..")
          .should(notBeRecord());

  private static ArchCondition<JavaClass> notBeRecord() {
    return new ArchCondition<>("not be a record") {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        if (item.isRecord()) {
          events.add(SimpleConditionEvent.violated(item, item.getName() + " must be class, not record"));
        }
      }
    };
  }
}
