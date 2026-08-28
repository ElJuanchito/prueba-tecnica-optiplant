package com.optiplant.inventory.iam.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.iam.domain.model.UserAccount;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** {@code branchExternalId}/{@code branchActive} come from separate queries (see
 * {@link UserSpringDataRepository}), so they arrive as extra source parameters
 * rather than from the entity itself. */
@Mapper(componentModel = "spring")
public interface UserMapper {

	@Mapping(target = "role", expression = "java(com.optiplant.inventory.shared.security.Role.valueOf(entity.getRole()))")
	@Mapping(target = "active",
			expression = "java(entity.isActive() && (branchExternalId == null || Boolean.TRUE.equals(branchActive)))")
	UserAccount toDomain(UserJpaEntity entity, UUID branchExternalId, Boolean branchActive);
}
