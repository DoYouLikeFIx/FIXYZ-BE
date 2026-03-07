package com.fix.fepgateway.convention;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

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
}
