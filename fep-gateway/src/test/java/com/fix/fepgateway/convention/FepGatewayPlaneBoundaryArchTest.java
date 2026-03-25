package com.fix.fepgateway.convention;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.fix.fepgateway.FepGatewayApplication;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

@AnalyzeClasses(packages = "com.fix.fepgateway")
class FepGatewayPlaneBoundaryArchTest {

  @ArchTest
  static final ArchRule controlPlaneControllerShouldNotDependOnDataPlane =
      noClasses()
          .that().resideInAPackage("..controlplane.controller..")
          .should().dependOnClassesThat().resideInAPackage("..dataplane..");

  @ArchTest
  static final ArchRule dataPlaneShouldNotDependOnController =
      noClasses()
          .that().resideInAPackage("..dataplane..")
          .should().dependOnClassesThat().resideInAnyPackage("..controller..", "..controlplane.controller..");

  @Test
  void ownClassesShouldUseFepGatewayRootPackage() {
    classes()
        .should().resideInAPackage("com.fix.fepgateway..")
        .check(importModuleClasses(FepGatewayApplication.class));
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
