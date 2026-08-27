package com.optiplant.inventory;

import static com.tngtech.archunit.base.DescribedPredicate.alwaysTrue;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * Verificación de las fronteras arquitectónicas — RNF-MAN-02.
 *
 * <p>Análisis estático puro: sin contexto de Spring y sin Docker, de modo que
 * {@code ./mvnw package} siga funcionando en un clon limpio sin demonio Docker.
 *
 * <p>Las reglas se declaran acá de forma explícita en lugar de derivarse de una
 * convención de framework. Cada una corresponde a una afirmación concreta de la
 * sección 5 del documento de decisiones de arquitectura.
 *
 * <p><b>Sobre {@code allowEmptyShould}:</b> mientras los diez paquetes de módulo no
 * existan, varias reglas no encuentran ninguna clase que evaluar. ArchUnit falla ante
 * un conjunto vacío por defecto —para que una regla mal escrita no pase inadvertida—,
 * y acá el conjunto vacío es el estado legítimo del proyecto, no un error de la regla.
 * Cada módulo que aparezca entra automáticamente bajo estas comprobaciones.
 */
class ModuleBoundariesTest {

	private static final String BASE = "com.optiplant.inventory";

	/** Los diez módulos de negocio de la sección 2.4 del documento de arquitectura. */
	private static final String[] MODULOS = {
			"iam", "catalog", "pricing", "inventory", "purchases",
			"sales", "transfers", "logistics", "notifications", "analytics"
	};

	private static final JavaClasses CLASES = new ClassFileImporter()
			.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages(BASE);

	@Test
	void elDominioNoConoceInfraestructuraNiFramework() {
		noClasses()
				.that().resideInAPackage(BASE + "..domain..")
				.should().dependOnClassesThat()
				.resideInAnyPackage("org.springframework..", "jakarta.persistence..",
						"..application..", "..infrastructure..")
				.because("el núcleo debe poder probarse sin levantar Spring ni base de datos")
				.allowEmptyShould(true)
				.check(CLASES);
	}

	@Test
	void laCapaDeAplicacionNoConoceSusAdaptadores() {
		noClasses()
				.that().resideInAPackage(BASE + "..application..")
				.should().dependOnClassesThat().resideInAPackage("..infrastructure..")
				.because("los casos de uso dependen de puertos, nunca de la implementación que los cumple")
				.allowEmptyShould(true)
				.check(CLASES);
	}

	@Test
	void ningunModuloEntraAlInteriorDeOtro() {
		slices()
				.matching(BASE + ".(*)..")
				.should().notDependOnEachOther()
				// shared es el único paquete que todos pueden importar: son los tipos
				// transversales del dominio y los eventos base.
				.ignoreDependency(alwaysTrue(), resideInAPackage(BASE + ".shared.."))
				.because("un módulo se comunica con otro por su API pública, no alcanzando sus clases internas")
				.allowEmptyShould(true)
				.check(CLASES);
	}

	@Test
	void noHayCiclosEntreModulos() {
		slices()
				.matching(BASE + ".(*)..")
				.should().beFreeOfCycles()
				.because("un ciclo entre módulos convierte dos fronteras en una sola")
				.allowEmptyShould(true)
				.check(CLASES);
	}

	@Test
	void sharedEsUnaHoja() {
		noClasses()
				.that().resideInAPackage(BASE + ".shared..")
				.should().dependOnClassesThat().resideInAnyPackage(paquetesDeModulo())
				.because("si shared importa un módulo, el desacoplamiento por puertos se rompe por la puerta de atrás")
				.allowEmptyShould(true)
				.check(CLASES);
	}

	private static String[] paquetesDeModulo() {
		return Arrays.stream(MODULOS).map(m -> BASE + "." + m + "..").toArray(String[]::new);
	}
}
