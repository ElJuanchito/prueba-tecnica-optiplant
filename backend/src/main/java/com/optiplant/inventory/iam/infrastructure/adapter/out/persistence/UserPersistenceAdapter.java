package com.optiplant.inventory.iam.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.iam.application.port.out.UserRepositoryPort;
import com.optiplant.inventory.iam.domain.model.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class UserPersistenceAdapter implements UserRepositoryPort {

	private final UserSpringDataRepository userRepository;
	private final UserMapper userMapper;

	public UserPersistenceAdapter(UserSpringDataRepository userRepository, UserMapper userMapper) {
		this.userRepository = userRepository;
		this.userMapper = userMapper;
	}

	@Override
	public Optional<UserAccount> findByUsername(String username) {
		return userRepository.findByUsername(username).map(this::toDomain);
	}

	@Override
	public Optional<UserAccount> findByExternalId(UUID externalId) {
		return userRepository.findByExternalId(externalId).map(this::toDomain);
	}

	private UserAccount toDomain(UserJpaEntity entity) {
		UUID branchExternalId = null;
		Boolean branchActive = null;
		if (entity.getBranchId() != null) {
			branchExternalId = userRepository.findBranchExternalId(entity.getBranchId()).orElse(null);
			branchActive = userRepository.findBranchActive(entity.getBranchId()).orElse(Boolean.FALSE);
		}
		return userMapper.toDomain(entity, branchExternalId, branchActive);
	}
}
