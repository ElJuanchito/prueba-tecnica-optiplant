package com.optiplant.inventory.iam.domain.model;

import java.util.UUID;

/**
 * Domain representation of a branch (branch-administration capability,
 * RF-SEG-03 / CU-SEG-03).
 *
 * <p>Carries {@code external_id} only, never the internal numeric {@code id} —
 * the project's anti-enumeration invariant (CLAUDE.md).
 *
 * @param active {@code true} when active. A disabled branch's users cannot log in.
 */
public record BranchProfile(UUID externalId, String code, String name, String address, String city, String phone,
		boolean active) {
}
