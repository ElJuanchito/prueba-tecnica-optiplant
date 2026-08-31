package com.optiplant.inventory.purchases.domain.service;

import static com.optiplant.inventory.purchases.domain.PurchaseOrderFixtures.item;
import static com.optiplant.inventory.purchases.domain.PurchaseOrderFixtures.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.purchases.domain.exception.BranchContextRequiredException;
import com.optiplant.inventory.purchases.domain.exception.PurchaseOrderNotFoundException;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrder;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderStatus;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PurchaseAccessPolicyTest {

	private static final UUID BRANCH_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
	private static final UUID BRANCH_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
	private static final UUID PRODUCT = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID ITEM = UUID.fromString("00000000-0000-0000-0000-00000000a001");

	private static AuthenticatedPrincipal principal(Role role, UUID branchId) {
		return new AuthenticatedPrincipal(UUID.randomUUID(), "user", role, branchId);
	}

	@Test
	@DisplayName("R-07: a corporate ADMIN creating or receiving has no branch to derive -> branch_context_required")
	void corporateAdminHasNoBranchContext() {
		assertThatThrownBy(() -> PurchaseAccessPolicy.resolveActingBranch(principal(Role.ADMIN, null)))
				.isInstanceOf(BranchContextRequiredException.class);
	}

	@Test
	@DisplayName("R-07: a branch-scoped actor's acting branch is its session branch")
	void branchScopedActorResolvesToSessionBranch() {
		assertThat(PurchaseAccessPolicy.resolveActingBranch(principal(Role.OPERATOR, BRANCH_A))).isEqualTo(BRANCH_A);
	}

	@Test
	@DisplayName("R-23/R-25: another branch's order is not found for a branch-scoped actor, never 403")
	void otherBranchOrderIsNotFound() {
		PurchaseOrder order = order(BRANCH_B, PurchaseOrderStatus.APPROVED, item(ITEM, PRODUCT, "10", "0", "5", "0"));

		for (Role role : new Role[] { Role.BRANCH_MANAGER, Role.OPERATOR }) {
			assertThatThrownBy(() -> PurchaseAccessPolicy.assertVisible(principal(role, BRANCH_A), order))
					.isInstanceOf(PurchaseOrderNotFoundException.class);
		}
	}

	@Test
	@DisplayName("R-25: an actor sees its own branch's order; ADMIN reads network-wide")
	void ownBranchAndAdminAreVisible() {
		PurchaseOrder ownOrder = order(BRANCH_A, PurchaseOrderStatus.APPROVED, item(ITEM, PRODUCT, "10", "0", "5", "0"));
		PurchaseOrder otherOrder = order(BRANCH_B, PurchaseOrderStatus.APPROVED, item(ITEM, PRODUCT, "10", "0", "5", "0"));

		assertThatCode(() -> PurchaseAccessPolicy.assertVisible(principal(Role.OPERATOR, BRANCH_A), ownOrder))
				.doesNotThrowAnyException();
		assertThatCode(() -> PurchaseAccessPolicy.assertVisible(principal(Role.ADMIN, null), otherOrder))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("R-25: the listing branch scope is null for ADMIN and the session branch otherwise")
	void listingScopeByRole() {
		assertThat(PurchaseAccessPolicy.listingBranchScope(principal(Role.ADMIN, null))).isNull();
		assertThat(PurchaseAccessPolicy.listingBranchScope(principal(Role.BRANCH_MANAGER, BRANCH_A))).isEqualTo(BRANCH_A);
	}
}
