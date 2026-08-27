package com.optiplant.inventory.iam.domain.model;

import com.optiplant.inventory.shared.security.Role;
import java.util.UUID;

/**
 * The subset of {@code users} needed to authenticate a login attempt.
 *
 * <p>Carries {@code external_id} only, never the internal numeric {@code id} — the
 * project's anti-enumeration rule. The persistence adapter resolves the numeric id
 * internally whenever it needs one for a foreign key (e.g. {@code refresh_tokens.user_id}).
 *
 * @param active {@code true} only when both the user and, if assigned, their branch are
 *               active. A disabled branch disables login for every one of its users.
 */
public record UserAccount(UUID externalId, String username, String passwordHash, Role role,
		UUID branchExternalId, boolean active) {
}
