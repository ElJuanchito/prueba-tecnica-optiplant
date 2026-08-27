package com.optiplant.inventory.iam.application.port.out;

import com.optiplant.inventory.iam.domain.model.UserAccount;
import java.util.Optional;
import java.util.UUID;

public interface UserRepositoryPort {

	Optional<UserAccount> findByUsername(String username);

	/** Reloads the user by {@code external_id} — used on refresh so a rotated access
	 * token reflects the caller's current role/branch, not the one at login time. */
	Optional<UserAccount> findByExternalId(UUID externalId);
}
