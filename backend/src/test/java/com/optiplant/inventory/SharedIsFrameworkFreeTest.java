package com.optiplant.inventory;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * {@code shared} must stay framework-free: no class in it may import
 * {@code org.springframework..} or {@code jakarta.persistence..}. This is
 * stricter than {@code ModuleBoundariesTest#sharedEsUnaHoja}, which only
 * forbids importing the ten business modules.
 */
class SharedIsFrameworkFreeTest {

	private static final String BASE = "com.optiplant.inventory";

	private static final JavaClasses CLASES = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages(BASE);

	@Test
	void sharedNoImportaSpringNiJpa() {
		noClasses()
				.that().resideInAPackage(BASE + ".shared..")
				.should().dependOnClassesThat()
				.resideInAnyPackage("org.springframework..", "jakarta.persistence..")
				.because("shared es la única API que todo módulo puede importar; si arrastra un framework, "
						+ "cada consumidor hereda esa dependencia por la puerta de atrás")
				.allowEmptyShould(true)
				.check(CLASES);
	}
}
