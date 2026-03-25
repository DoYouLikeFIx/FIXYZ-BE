package com.fix.common.convention;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.fix.common.entity.BaseTimeEntity;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class CoreCommonModuleConventionArchTest {

  @Test
  void ownClassesShouldUseCoreCommonRootPackage() {
    classes()
        .should().resideInAPackage("com.fix.common..")
        .check(importModuleClasses(BaseTimeEntity.class));
  }

  @Test
  void ownClassesShouldNotDependOnSpring() {
    noClasses()
        .should().dependOnClassesThat().resideInAnyPackage("org.springframework..")
        .check(importModuleClasses(BaseTimeEntity.class));
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
