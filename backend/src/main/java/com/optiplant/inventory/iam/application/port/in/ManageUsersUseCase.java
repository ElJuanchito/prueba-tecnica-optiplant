package com.optiplant.inventory.iam.application.port.in;

import com.optiplant.inventory.iam.application.port.out.UserRepositoryPort.UserPage;
import com.optiplant.inventory.iam.domain.model.UserAccount;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.util.UUID;

/**
 * Create, edit, disable, and query users — {@code ADMIN} manages any user in
 * any branch; {@code BRANCH_MANAGER} may only create/edit/disable/list {@code
 * OPERATOR} users within their own session branch (enforced by {@code
 * SecurityConfig}'s {@code /api/admin/users/**} matcher admitting both roles,
 * plus per-call scoping in {@code UserAdminService}). {@code OPERATOR} never
 * reaches this port. Every mutation writes an audit entry in the same
 * transaction (CLAUDE.md's synchronous-effects invariant).
 */
public interface ManageUsersUseCase {

	/**
	 * @throws com.optiplant.inventory.iam.domain.exception.DuplicateUsernameException
	 *     on a duplicate {@code username} or {@code email}
	 * @throws IllegalArgumentException when a non-{@code ADMIN} role is created
	 *     with no {@code branchExternalId}
	 * @throws com.optiplant.inventory.iam.domain.exception.CrossBranchMutationException
	 *     when a {@code BRANCH_MANAGER} targets a role other than {@code
	 *     OPERATOR} or a branch other than their own
	 */
	UserAccount create(AuthenticatedPrincipal actor, CreateUserCommand command);

	/**
	 * @throws com.optiplant.inventory.iam.domain.exception.UserNotFoundException
	 *     when {@code externalId} names no user
	 * @throws com.optiplant.inventory.iam.domain.exception.DuplicateUsernameException
	 *     when {@code command.email()} belongs to a different user
	 * @throws IllegalArgumentException when a non-{@code ADMIN} role is edited
	 *     to have no {@code branchExternalId}
	 * @throws com.optiplant.inventory.iam.domain.exception.CrossBranchMutationException
	 *     when a {@code BRANCH_MANAGER} targets a user, or a requested new
	 *     role/branch, outside their own {@code OPERATOR}-in-own-branch scope
	 */
	UserAccount edit(AuthenticatedPrincipal actor, UUID externalId, EditUserCommand command);

	/**
	 * Sets {@code is_active = false} and revokes every one of the user's live
	 * refresh tokens, in the same transaction (P2/P4).
	 *
	 * @throws com.optiplant.inventory.iam.domain.exception.UserNotFoundException
	 *     when {@code externalId} names no user
	 * @throws com.optiplant.inventory.iam.domain.exception.CrossBranchMutationException
	 *     when a {@code BRANCH_MANAGER} targets a user outside their own
	 *     {@code OPERATOR}-in-own-branch scope
	 */
	void disable(AuthenticatedPrincipal actor, UUID externalId);

	/** A {@code BRANCH_MANAGER}'s {@code query} is forced to their own branch
	 * and {@code role=OPERATOR}, regardless of what they submit. */
	UserPage list(AuthenticatedPrincipal actor, UserQuery query);

	record CreateUserCommand(String username, String email, String password, String fullName, Role role,
			UUID branchExternalId) {
	}

	/** {@code username}/password are absent — edit never changes either. */
	record EditUserCommand(String email, String fullName, Role role, UUID branchExternalId) {
	}

	record UserQuery(Boolean active, Role role, UUID branchExternalId, int page, int size) {
	}
}
