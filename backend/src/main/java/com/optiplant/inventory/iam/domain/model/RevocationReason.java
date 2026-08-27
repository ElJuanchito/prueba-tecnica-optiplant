package com.optiplant.inventory.iam.domain.model;

/**
 * Mirrors the {@code revoked_reason} CHECK constraint on {@code refresh_tokens}
 * ({@code 01-init-schema.sql}). The DB column and this enum's constants must stay
 * the same four strings.
 */
public enum RevocationReason {
	LOGOUT, ROTATED, REUSE_DETECTED, USER_DISABLED
}
