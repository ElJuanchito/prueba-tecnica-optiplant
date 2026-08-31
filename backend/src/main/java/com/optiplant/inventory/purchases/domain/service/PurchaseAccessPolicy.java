package com.optiplant.inventory.purchases.domain.service;

import com.optiplant.inventory.purchases.domain.exception.BranchContextRequiredException;
import com.optiplant.inventory.purchases.domain.exception.PurchaseOrderNotFoundException;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrder;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.util.UUID;

/**
 * Two ordered questions, and the order is the security property (contract §5, R-07, R-23, R-25,
 * RNF-SEC-03, design §3.4), as in {@code TransferAccessPolicy}:
 *
 * <ol>
 * <li><strong>Branch context</strong>: a corporate {@code ADMIN} creating an order or receiving
 * goods has no branch to derive &rarr; {@link BranchContextRequiredException}
 * &rarr; {@code 403 branch_context_required}.</li>
 * <li><strong>Visibility</strong>: {@code ADMIN} sees every branch, anyone else only their own;
 * another branch's order &rarr; {@link PurchaseOrderNotFoundException} &rarr; {@code 404},
 * <strong>never {@code 403}</strong>.</li>
 * </ol>
 */
public final class PurchaseAccessPolicy {

	private PurchaseAccessPolicy() {
	}

	/**
	 * Resolves the acting branch for a create or a reception (R-07, R-23).
	 *
	 * @throws BranchContextRequiredException {@code actor} is a corporate {@code ADMIN}
	 */
	public static UUID resolveActingBranch(AuthenticatedPrincipal actor) {
		if (actor == null || actor.isCorporate()) {
			throw new BranchContextRequiredException();
		}
		return actor.branchId();
	}

	/**
	 * Asserts the order is visible to the actor (R-25).
	 *
	 * @throws PurchaseOrderNotFoundException the order belongs to another branch and the actor is
	 *     not an {@code ADMIN}
	 */
	public static void assertVisible(AuthenticatedPrincipal actor, PurchaseOrder order) {
		if (actor.role() == Role.ADMIN) {
			return;
		}
		if (actor.branchId() == null || !order.belongsTo(actor.branchId())) {
			throw new PurchaseOrderNotFoundException(order.externalId());
		}
	}

	/**
	 * Asserts an order with the given branch is visible to the actor (R-25), without the aggregate.
	 *
	 * @throws PurchaseOrderNotFoundException the order belongs to another branch and the actor is
	 *     not an {@code ADMIN}
	 */
	public static void assertVisible(AuthenticatedPrincipal actor, UUID orderExternalId, UUID orderBranchExternalId) {
		if (actor.role() == Role.ADMIN) {
			return;
		}
		if (actor.branchId() == null || !actor.branchId().equals(orderBranchExternalId)) {
			throw new PurchaseOrderNotFoundException(orderExternalId);
		}
	}

	/** The branch scope for a listing or cost history: {@code null} for {@code ADMIN} (network-wide). */
	public static UUID listingBranchScope(AuthenticatedPrincipal actor) {
		return actor.role() == Role.ADMIN ? null : actor.branchId();
	}
}
