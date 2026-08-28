package com.optiplant.inventory.iam.application.port.in;

import com.optiplant.inventory.iam.application.port.out.UserRepositoryPort.UserPage;
import com.optiplant.inventory.iam.domain.model.UserAccount;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.util.UUID;

/**
 * Create, edit, disable, and query users — {@code ADMIN}-only (enforced by
 * {@code SecurityConfig}'s {@code /api/admin/users/**} matcher, slice 3;
 * mirrors {@code QueryAuditLogUseCase}'s reuse of its output port's page
 * record for the same reason). Every mutation writes an audit entry in the
 * same transaction (CLAUDE.md's synchronous-effects invariant).
 */
public interface ManageUsersUseCase {

	/**
	 * @throws com.optiplant.inventory.iam.domain.exception.DuplicateUsernameException
	 *     on a duplicate {@code username} or {@code email}
	 * @throws IllegalArgumentException when a non-{@code ADMIN} role is created
	 *     with no {@code branchExternalId}
	 */
	UserAccount create(AuthenticatedPrincipal actor, CreateUserCommand command);

	/**
	 * @throws com.optiplant.inventory.iam.domain.exception.UserNotFoundException
	 *     when {@code externalId} names no user
	 * @throws com.optiplant.inventory.iam.domain.exception.DuplicateUsernameException
	 *     when {@code command.email()} belongs to a different user
	 * @throws IllegalArgumentException when a non-{@code ADMIN} role is edited
	 *     to have no {@code branchExternalId}
	 */
	UserAccount edit(AuthenticatedPrincipal actor, UUID externalId, EditUserCommand command);

	/**
	 * Sets {@code is_active = false} and revokes every one of the user's live
	 * refresh tokens, in the same transaction (P2/P4).
	 *
	 * @throws com.optiplant.inventory.iam.domain.exception.UserNotFoundException
	 *     when {@code externalId} names no user
	 */
	void disable(AuthenticatedPrincipal actor, UUID externalId);

	UserPage list(UserQuery query);

	record CreateUserCommand(String username, String email, String password, String fullName, Role role,
			UUID branchExternalId) {
	}

	/** {@code username}/password are absent — edit never changes either. */
	record EditUserCommand(String email, String fullName, Role role, UUID branchExternalId) {
	}

	record UserQuery(Boolean active, Role role, UUID branchExternalId, int page, int size) {
	}
}
