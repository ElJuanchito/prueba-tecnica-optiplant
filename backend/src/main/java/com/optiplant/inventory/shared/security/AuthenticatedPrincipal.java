package com.optiplant.inventory.shared.security;

import java.util.UUID;

/**
 * The identity of the authenticated caller, carried by every request after the
 * bearer filter validates the access token.
 *
 * <p>Only {@code external_id} values ever reach this record — never a numeric
 * {@code id}, per the project's anti-enumeration rule. {@code branchId} is
 * {@code null} for a corporate {@link Role#ADMIN}, mirroring the nullable
 * {@code users.branch_id} column ({@code 01-init-schema.sql:33}).
 */
public record AuthenticatedPrincipal(UUID userId, String username, Role role, UUID branchId) {

	/** {@code true} when this principal is a corporate admin with no branch assignment. */
	public boolean isCorporate() {
		return branchId == null;
	}

	/**
	 * RN-14 + RN-08: mutations are confined to the caller's own branch. An
	 * {@link Role#ADMIN} may mutate any branch, corporate-wide.
	 */
	public boolean mayMutateBranch(UUID target) {
		return role == Role.ADMIN || (branchId != null && branchId.equals(target));
	}
}
