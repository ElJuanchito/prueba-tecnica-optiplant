package com.optiplant.inventory.inventory.domain.service;

import com.optiplant.inventory.inventory.domain.exception.BranchContextRequiredException;
import com.optiplant.inventory.inventory.domain.exception.CrossBranchAccessDeniedException;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.util.UUID;

/**
 * Resolves the branch a session-scoped mutation applies to (design §3.3, contract §5). RN-14
 * forbids the branch ever arriving as a client parameter, so every mutating use case derives it
 * from the session instead.
 */
public final class BranchScopePolicy {

	private BranchScopePolicy() {
	}

	/**
	 * @throws BranchContextRequiredException {@code actor} is a corporate {@code ADMIN}
	 *     ({@code branchId() == null}) — PA-02
	 */
	public static UUID resolveOwnBranch(AuthenticatedPrincipal actor) {
		if (actor.isCorporate()) {
			throw new BranchContextRequiredException();
		}
		return actor.branchId();
	}

	/**
	 * Defence in depth behind RN-14 (contract §7, {@code cross_branch_access_denied}): asserts a
	 * mutation resolved to {@code actor}'s own branch. The current API surface derives the branch
	 * exclusively from {@code actor}, so this can never fail today — it guards against a future
	 * caller mis-deriving one.
	 *
	 * @throws BranchContextRequiredException {@code actor} is a corporate {@code ADMIN}
	 * @throws CrossBranchAccessDeniedException {@code resolvedBranchExternalId} does not match
	 *     {@code actor}'s own branch
	 */
	public static void assertOwnBranch(AuthenticatedPrincipal actor, UUID resolvedBranchExternalId) {
		UUID ownBranch = resolveOwnBranch(actor);
		if (!ownBranch.equals(resolvedBranchExternalId)) {
			throw new CrossBranchAccessDeniedException();
		}
	}
}
