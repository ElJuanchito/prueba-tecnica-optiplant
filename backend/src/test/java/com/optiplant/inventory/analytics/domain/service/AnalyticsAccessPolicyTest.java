package com.optiplant.inventory.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.analytics.domain.exception.BranchContextRequiredException;
import com.optiplant.inventory.analytics.domain.exception.CrossBranchAccessDeniedException;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AnalyticsAccessPolicyTest {

	private static final UUID OWN_BRANCH = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final UUID REQUESTED_BRANCH = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

	private static AuthenticatedPrincipal principal(Role role, UUID branchId) {
		return new AuthenticatedPrincipal(UUID.randomUUID(), "test-user", role, branchId);
	}

	@Test
	@DisplayName("R-02 step 1: non-ADMIN sending branchExternalId throws CrossBranchAccessDeniedException before any lookup")
	void nonAdminWithBranchParamThrowsCrossBranchDenied() {
		for (Role role : new Role[] { Role.BRANCH_MANAGER, Role.OPERATOR }) {
			AuthenticatedPrincipal actor = principal(role, OWN_BRANCH);
			assertThatThrownBy(() -> AnalyticsAccessPolicy.resolveBranch(actor, REQUESTED_BRANCH))
					.isInstanceOf(CrossBranchAccessDeniedException.class);
		}
	}

	@Test
	@DisplayName("R-02 step 2: non-ADMIN omitting branchExternalId resolves to actor's session branchId")
	void nonAdminWithoutBranchParamResolvesToOwnBranch() {
		for (Role role : new Role[] { Role.BRANCH_MANAGER, Role.OPERATOR }) {
			AuthenticatedPrincipal actor = principal(role, OWN_BRANCH);
			UUID resolved = AnalyticsAccessPolicy.resolveBranch(actor, null);
			assertThat(resolved).isEqualTo(OWN_BRANCH);
		}
	}

	@Test
	@DisplayName("R-02 step 3: ADMIN naming branchExternalId resolves to requested branch")
	void adminWithRequestedBranchResolvesToRequested() {
		AuthenticatedPrincipal corporateAdmin = principal(Role.ADMIN, null);
		assertThat(AnalyticsAccessPolicy.resolveBranch(corporateAdmin, REQUESTED_BRANCH)).isEqualTo(REQUESTED_BRANCH);

		AuthenticatedPrincipal branchAdmin = principal(Role.ADMIN, OWN_BRANCH);
		assertThat(AnalyticsAccessPolicy.resolveBranch(branchAdmin, REQUESTED_BRANCH)).isEqualTo(REQUESTED_BRANCH);
	}

	@Test
	@DisplayName("R-02 step 4: branch-assigned ADMIN omitting branchExternalId resolves to session branch")
	void branchAssignedAdminWithoutParamResolvesToOwnBranch() {
		AuthenticatedPrincipal branchAdmin = principal(Role.ADMIN, OWN_BRANCH);
		assertThat(AnalyticsAccessPolicy.resolveBranch(branchAdmin, null)).isEqualTo(OWN_BRANCH);
	}

	@Test
	@DisplayName("R-02 step 5: corporate ADMIN omitting branchExternalId throws BranchContextRequiredException")
	void corporateAdminWithoutParamThrowsBranchContextRequired() {
		AuthenticatedPrincipal corporateAdmin = principal(Role.ADMIN, null);
		assertThatThrownBy(() -> AnalyticsAccessPolicy.resolveBranch(corporateAdmin, null))
				.isInstanceOf(BranchContextRequiredException.class);
	}

	@Test
	@DisplayName("Null actor throws BranchContextRequiredException")
	void nullActorThrowsBranchContextRequired() {
		assertThatThrownBy(() -> AnalyticsAccessPolicy.resolveBranch(null, REQUESTED_BRANCH))
				.isInstanceOf(BranchContextRequiredException.class);
	}
}
