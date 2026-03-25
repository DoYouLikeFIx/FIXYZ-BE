package com.fix.fepsimulator.convention;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.fix.fepsimulator.FepSimulatorApplication;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

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

  @ArchTest
  static final ArchRule globalExceptionHandlerShouldLiveInExceptionPackage =
      classes()
          .that().haveSimpleName("GlobalExceptionHandler")
          .should().resideInAPackage("..exception..");

  @Test
  void ownClassesShouldUseFepSimulatorRootPackage() {
    classes()
        .should().resideInAPackage("com.fix.fepsimulator..")
        .check(importModuleClasses(FepSimulatorApplication.class));
  }

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

  private static JavaClasses importModuleClasses(Class<?> anchorClass) {
    try {
      return new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPath(Paths.get(anchorClass.getProtectionDomain().getCodeSource().getLocation().toURI()));
    } catch (URISyntaxException exception) {
      throw new IllegalStateException("Failed to import classes for " + anchorClass.getName(), exception);
    }
  }
}
