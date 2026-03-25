package com.fix.corebank.convention;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.fix.corebank.entity.Account;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.net.URISyntaxException;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class CorebankDomainModuleConventionArchTest {

  @Test
  void ownClassesShouldUseCorebankDomainPackages() {
    classes()
        .should().resideInAnyPackage("com.fix.corebank.domain..", "com.fix.corebank.entity..")
        .check(importModuleClasses(Account.class));
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
