package com.optiplant.inventory.iam.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.iam.domain.exception.CrossBranchMutationException;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure evaluation, no Spring/Docker — mirrors {@link RefreshTokenPolicyTest}'s
 * shape for a domain-service unit test. */
class BranchAccessPolicyTest {

	private static final UUID SUCURSAL_A = UUID.randomUUID();
	private static final UUID SUCURSAL_B = UUID.randomUUID();

	private final BranchAccessPolicy policy = new BranchAccessPolicy();

	@Test
	void unAdminCorporativoPuedeMutarCualquierSucursal() {
		AuthenticatedPrincipal admin = new AuthenticatedPrincipal(UUID.randomUUID(), "admin.corp", Role.ADMIN, null);

		policy.requireMayMutate(admin, SUCURSAL_A);
		policy.requireMayMutate(admin, SUCURSAL_B);
		// No exception thrown for either branch — corporate ADMIN is unrestricted.
	}

	@Test
	void unOperadorPuedeMutarSuPropiaSucursal() {
		AuthenticatedPrincipal operador = new AuthenticatedPrincipal(UUID.randomUUID(), "operador.bogota",
				Role.OPERATOR, SUCURSAL_A);

		policy.requireMayMutate(operador, SUCURSAL_A);
	}

	@Test
	void unOperadorNoPuedeMutarUnaSucursalAjena() {
		AuthenticatedPrincipal operador = new AuthenticatedPrincipal(UUID.randomUUID(), "operador.bogota",
				Role.OPERATOR, SUCURSAL_A);

		assertThatThrownBy(() -> policy.requireMayMutate(operador, SUCURSAL_B))
				.isInstanceOf(CrossBranchMutationException.class);
	}

	@Test
	void unGerenteDeSucursalPuedeMutarSuPropiaSucursalPeroNoOtra() {
		AuthenticatedPrincipal gerente = new AuthenticatedPrincipal(UUID.randomUUID(), "gerente.medellin",
				Role.BRANCH_MANAGER, SUCURSAL_B);

		policy.requireMayMutate(gerente, SUCURSAL_B);
		assertThatThrownBy(() -> policy.requireMayMutate(gerente, SUCURSAL_A))
				.isInstanceOf(CrossBranchMutationException.class);
	}

	@Test
	void laExcepcionNoRevelaLaSucursalObjetivoEnElMensaje() {
		AuthenticatedPrincipal operador = new AuthenticatedPrincipal(UUID.randomUUID(), "operador.bogota",
				Role.OPERATOR, SUCURSAL_A);

		assertThatThrownBy(() -> policy.requireMayMutate(operador, SUCURSAL_B))
				.isInstanceOf(CrossBranchMutationException.class)
				.hasMessageNotContaining(SUCURSAL_B.toString());
	}

	@Test
	void mayMutateBranchYRequireMayMutateCoincidenParaElMismoPrincipal() {
		AuthenticatedPrincipal operador = new AuthenticatedPrincipal(UUID.randomUUID(), "operador.bogota",
				Role.OPERATOR, SUCURSAL_A);

		assertThat(operador.mayMutateBranch(SUCURSAL_A)).isTrue();
		assertThat(operador.mayMutateBranch(SUCURSAL_B)).isFalse();
	}
}
