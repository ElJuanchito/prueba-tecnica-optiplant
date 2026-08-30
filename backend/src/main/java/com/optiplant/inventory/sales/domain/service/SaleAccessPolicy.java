package com.optiplant.inventory.sales.domain.service;

import com.optiplant.inventory.sales.domain.exception.BranchContextRequiredException;
import com.optiplant.inventory.sales.domain.exception.CrossBranchAccessDeniedException;
import com.optiplant.inventory.sales.domain.exception.SaleNotFoundException;
import com.optiplant.inventory.sales.domain.model.Sale;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.util.UUID;

/**
 * Access policy and visibility rules for sales (contract §5, R-02, R-22, R-25, RNF-SEC-03).
 *
 * <p>Three ordered questions:
 * <ol>
 *   <li><strong>Branch context</strong>: registering with {@code branchId == null} throws {@link BranchContextRequiredException} (403).</li>
 *   <li><strong>Visibility</strong>: {@code ADMIN}, or actor's branch equals the sale's branch; otherwise throws {@link SaleNotFoundException} (404, never 403, so existence does not leak).</li>
 *   <li><strong>Mutation</strong>: void additionally requires {@code ADMIN} or {@code BRANCH_MANAGER}; {@code OPERATOR} throws {@link CrossBranchAccessDeniedException} (403).</li>
 * </ol>
 */
public final class SaleAccessPolicy {

	private SaleAccessPolicy() {
	}

	/**
	 * Resolves the branch for registering a new sale (R-02).
	 *
	 * @throws BranchContextRequiredException if the actor is a corporate ADMIN with no branch context
	 */
	public static UUID resolveRegistrationBranch(AuthenticatedPrincipal actor) {
		if (actor == null || actor.isCorporate()) {
			throw new BranchContextRequiredException();
		}
		return actor.branchId();
	}

	/**
	 * Asserts that the given sale is visible to the actor (R-25).
	 *
	 * @throws SaleNotFoundException if the sale belongs to another branch and actor is not an ADMIN
	 */
	public static void assertVisible(AuthenticatedPrincipal actor, Sale sale) {
		if (actor.role() == Role.ADMIN) {
			return;
		}
		if (actor.branchId() == null || !sale.belongsTo(actor.branchId())) {
			throw new SaleNotFoundException(sale.externalId());
		}
	}

	/**
	 * Asserts that a sale with the given external ID and branch ID is visible to the actor (R-25).
	 *
	 * @throws SaleNotFoundException if the sale belongs to another branch and actor is not an ADMIN
	 */
	public static void assertVisible(AuthenticatedPrincipal actor, UUID saleExternalId, UUID saleBranchExternalId) {
		if (actor.role() == Role.ADMIN) {
			return;
		}
		if (actor.branchId() == null || !actor.branchId().equals(saleBranchExternalId)) {
			throw new SaleNotFoundException(saleExternalId);
		}
	}

	/**
	 * Asserts that the actor is authorized to void the sale (R-22, §5).
	 * Visibility is asserted first so that unauthorized access to another branch's sale yields 404 rather than 403.
	 *
	 * @throws SaleNotFoundException if the sale is not visible to the caller
	 * @throws CrossBranchAccessDeniedException if an OPERATOR attempts to void a sale
	 */
	public static void assertCanVoid(AuthenticatedPrincipal actor, Sale sale) {
		assertVisible(actor, sale);
		if (actor.role() == Role.OPERATOR) {
			throw new CrossBranchAccessDeniedException();
		}
	}
}
