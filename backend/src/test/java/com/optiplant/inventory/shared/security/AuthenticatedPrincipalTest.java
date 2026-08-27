package com.optiplant.inventory.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * RN-08 / RN-14 truth table for {@link AuthenticatedPrincipal}, including the
 * corporate {@code branchId == null} case.
 */
class AuthenticatedPrincipalTest {

	private static final UUID OWN_BRANCH = UUID.randomUUID();
	private static final UUID OTHER_BRANCH = UUID.randomUUID();

	@Test
	void unCorporativoNoTieneSucursal() {
		AuthenticatedPrincipal admin = new AuthenticatedPrincipal(UUID.randomUUID(), "admin", Role.ADMIN, null);

		assertThat(admin.isCorporate()).isTrue();
	}

	@Test
	void unUsuarioConSucursalNoEsCorporativo() {
		AuthenticatedPrincipal operador = new AuthenticatedPrincipal(UUID.randomUUID(), "op", Role.OPERATOR,
				OWN_BRANCH);

		assertThat(operador.isCorporate()).isFalse();
	}

	@Test
	void unAdminCorporativoMutaCualquierSucursal() {
		AuthenticatedPrincipal admin = new AuthenticatedPrincipal(UUID.randomUUID(), "admin", Role.ADMIN, null);

		assertThat(admin.mayMutateBranch(OWN_BRANCH)).isTrue();
		assertThat(admin.mayMutateBranch(OTHER_BRANCH)).isTrue();
	}

	@Test
	void unGerenteDeSucursalSoloMutaLaPropia() {
		AuthenticatedPrincipal gerente = new AuthenticatedPrincipal(UUID.randomUUID(), "gerente",
				Role.BRANCH_MANAGER, OWN_BRANCH);

		assertThat(gerente.mayMutateBranch(OWN_BRANCH)).isTrue();
		assertThat(gerente.mayMutateBranch(OTHER_BRANCH)).isFalse();
	}

	@Test
	void unOperadorSoloMutaLaPropiaSucursal() {
		AuthenticatedPrincipal operador = new AuthenticatedPrincipal(UUID.randomUUID(), "op", Role.OPERATOR,
				OWN_BRANCH);

		assertThat(operador.mayMutateBranch(OWN_BRANCH)).isTrue();
		assertThat(operador.mayMutateBranch(OTHER_BRANCH)).isFalse();
	}
}
