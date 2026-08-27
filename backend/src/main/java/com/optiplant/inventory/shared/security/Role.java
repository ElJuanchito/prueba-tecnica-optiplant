package com.optiplant.inventory.shared.security;

/**
 * The three strings persisted by {@code users.role} ({@code 01-init-schema.sql:38}).
 *
 * <p>No {@code ROLE_} prefix: the value in the database, the token claim and the
 * Spring authority are the same string. The {@code CHECK} constraint on
 * {@code users.role} rejects any other value, so callers must use
 * {@code hasAuthority()} against these bare names, never {@code hasRole()} (which
 * prepends {@code ROLE_}).
 */
public enum Role {
	ADMIN, BRANCH_MANAGER, OPERATOR
}
