package com.optiplant.inventory.iam.application.port.out;

import com.optiplant.inventory.iam.domain.model.UserAccount;
import com.optiplant.inventory.shared.security.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {

	Optional<UserAccount> findByUsername(String username);

	/** Reloads the user by {@code external_id} — used on refresh so a rotated access
	 * token reflects the caller's current role/branch, not the one at login time. */
	Optional<UserAccount> findByExternalId(UUID externalId);

	/** Used by user-administration's duplicate-email check (create and edit). */
	Optional<UserAccount> findByEmail(String email);

	/** Persists a brand-new user (user-administration "Successful user creation").
	 * The implementation assigns the {@code external_id}. */
	UserAccount create(NewUser newUser);

	/** Updates role/branch/profile fields of an existing user, identified by its
	 * immutable {@code external_id} (user-administration "User edit updates
	 * role, branch, and profile fields"). Never touches {@code external_id} or
	 * {@code username} — the username is not one of the fields this operation
	 * edits. */
	UserAccount update(UUID externalId, UserUpdate update);

	/** Sets {@code is_active = false}. Never a physical delete
	 * (user-administration "User disable is logical..."). */
	void disable(UUID externalId);

	/** Filtered, paginated read access for admin listing (user-administration
	 * "User query lists users without exposing internal identifiers"). */
	UserPage list(UserFilter filter);

	record NewUser(String username, String email, String passwordHash, String fullName, Role role,
			UUID branchExternalId) {
	}

	/** {@code username} and {@code passwordHash} are deliberately absent — edit
	 * never changes either (user-administration's edit requirement only lists
	 * role, branch, full name, and email). */
	record UserUpdate(String email, String fullName, Role role, UUID branchExternalId) {
	}

	/** Every field except {@code page}/{@code size} is optional (null = unfiltered). */
	record UserFilter(Boolean active, Role role, UUID branchExternalId, int page, int size) {
	}

	record UserPage(List<UserAccount> content, long totalElements, int page, int size) {
	}
}
