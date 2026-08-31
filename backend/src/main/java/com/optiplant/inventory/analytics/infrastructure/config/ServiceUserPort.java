package com.optiplant.inventory.analytics.infrastructure.config;

import com.optiplant.inventory.shared.security.Role;
import java.util.Optional;
import java.util.UUID;

/**
 * Secondary read port for resolving service users by external id (design §4 Q-9, §8, D-10).
 * Placed in {@code infrastructure.config} beside its only consumer, the API-key filter.
 */
public interface ServiceUserPort {

	Optional<ServiceUserSubject> findActiveServiceUser(UUID userExternalId);

	record ServiceUserSubject(UUID userExternalId, String username, Role role) {
	}
}
