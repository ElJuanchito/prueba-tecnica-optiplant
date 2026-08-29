package com.optiplant.inventory.inventory.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.inventory.domain.exception.BranchContextRequiredException;
import com.optiplant.inventory.inventory.domain.exception.CrossBranchAccessDeniedException;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link BranchScopePolicy} (contract §5, PA-02). */
class BranchScopePolicyTest {

	@Test
	void resolvesTheBranchManagersOwnBranch() {
		UUID branch = UUID.randomUUID();
		AuthenticatedPrincipal manager = new AuthenticatedPrincipal(UUID.randomUUID(), "manager", Role.BRANCH_MANAGER,
				branch);

		assertThat(BranchScopePolicy.resolveOwnBranch(manager)).isEqualTo(branch);
	}

	@Test
	void aCorporateAdminHasNoBranchContext() {
		AuthenticatedPrincipal corporateAdmin = new AuthenticatedPrincipal(UUID.randomUUID(), "admin.corp", Role.ADMIN,
				null);

		assertThatThrownBy(() -> BranchScopePolicy.resolveOwnBranch(corporateAdmin))
				.isInstanceOf(BranchContextRequiredException.class);
	}

	@Test
	void assertOwnBranchAcceptsAMatchingBranch() {
		UUID branch = UUID.randomUUID();
		AuthenticatedPrincipal operator = new AuthenticatedPrincipal(UUID.randomUUID(), "operator", Role.OPERATOR,
				branch);

		BranchScopePolicy.assertOwnBranch(operator, branch);
	}

	@Test
	void assertOwnBranchRejectsAMismatchedBranch() {
		AuthenticatedPrincipal operator = new AuthenticatedPrincipal(UUID.randomUUID(), "operator", Role.OPERATOR,
				UUID.randomUUID());

		assertThatThrownBy(() -> BranchScopePolicy.assertOwnBranch(operator, UUID.randomUUID()))
				.isInstanceOf(CrossBranchAccessDeniedException.class);
	}

	@Test
	void assertOwnBranchStillRejectsACorporateAdmin() {
		AuthenticatedPrincipal corporateAdmin = new AuthenticatedPrincipal(UUID.randomUUID(), "admin.corp", Role.ADMIN,
				null);

		assertThatThrownBy(() -> BranchScopePolicy.assertOwnBranch(corporateAdmin, UUID.randomUUID()))
				.isInstanceOf(BranchContextRequiredException.class);
	}
}
