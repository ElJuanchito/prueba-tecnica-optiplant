package com.optiplant.inventory.analytics.domain.service;

import com.optiplant.inventory.analytics.domain.exception.BranchContextRequiredException;
import com.optiplant.inventory.analytics.domain.exception.CrossBranchAccessDeniedException;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.util.UUID;

/**
 * Pure authorization and branch resolution policy for analytics queries (contract §5, R-02,
 * PA-01, design §6).
 *
 * <p>Five ordered steps, and the order is the security property:
 * <ol>
 * <li>{@code actor.role() != ADMIN} and {@code requested != null} &rArr; {@link CrossBranchAccessDeniedException}
 *     (403 cross_branch_access_denied), <strong>first</strong> before any lookup.</li>
 * <li>{@code actor.role() != ADMIN} &rArr; return {@code actor.branchId()}.</li>
 * <li>{@code ADMIN} with {@code requested != null} &rArr; return {@code requested} (caller validates existence).</li>
 * <li>{@code ADMIN} with {@code requested == null} and {@code actor.branchId() != null} &rArr; return {@code actor.branchId()}.</li>
 * <li>{@code ADMIN} with {@code requested == null} and {@code branchId == null} &rArr; {@link BranchContextRequiredException}
 *     (403 branch_context_required).</li>
 * </ol>
 */
public final class AnalyticsAccessPolicy {

	private AnalyticsAccessPolicy() {
	}

	public static UUID resolveBranch(AuthenticatedPrincipal actor, UUID requestedBranchExternalId) {
		if (actor == null) {
			throw new BranchContextRequiredException();
		}
		if (actor.role() != Role.ADMIN) {
			if (requestedBranchExternalId != null) {
				throw new CrossBranchAccessDeniedException();
			}
			return actor.branchId();
		}
		if (requestedBranchExternalId != null) {
			return requestedBranchExternalId;
		}
		if (actor.branchId() != null) {
			return actor.branchId();
		}
		throw new BranchContextRequiredException();
	}
}
