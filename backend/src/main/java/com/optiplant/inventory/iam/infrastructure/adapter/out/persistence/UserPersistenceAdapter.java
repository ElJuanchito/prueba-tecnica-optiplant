package com.optiplant.inventory.iam.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.iam.application.port.out.UserRepositoryPort;
import com.optiplant.inventory.iam.domain.model.UserAccount;
import com.optiplant.inventory.shared.security.Role;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

	@Override
	public Optional<UserAccount> findByEmail(String email) {
		return userRepository.findByEmail(email).map(this::toDomain);
	}

	@Override
	public UserAccount create(NewUser newUser) {
		UserJpaEntity entity = new UserJpaEntity();
		entity.setUsername(newUser.username());
		entity.setEmail(newUser.email());
		entity.setPasswordHash(newUser.passwordHash());
		entity.setFullName(newUser.fullName());
		entity.setRole(newUser.role().name());
		entity.setActive(true);
		entity.setBranchId(resolveBranchId(newUser.branchExternalId()));
		entity.setCreatedAt(Instant.now());
		entity.setUpdatedAt(Instant.now());
		// external_id keeps UserJpaEntity's own field-initializer default
		// (UUID.randomUUID()) — never assigned here, mirroring
		// RefreshTokenPersistenceAdapter.persist's equivalent create path.
		return toDomain(userRepository.save(entity));
	}

	@Override
	public UserAccount update(UUID externalId, UserUpdate update) {
		UserJpaEntity entity = userRepository.findByExternalId(externalId)
				.orElseThrow(() -> new IllegalStateException("No user found for external id " + externalId));
		// external_id and username are never touched here — "external_id
		// immutable" (task 5a.2) and username is not one of edit's fields
		// (user-administration "User edit updates role, branch, and profile
		// fields" lists role/branch/full name/email only).
		entity.setEmail(update.email());
		entity.setFullName(update.fullName());
		entity.setRole(update.role().name());
		entity.setBranchId(resolveBranchId(update.branchExternalId()));
		entity.setUpdatedAt(Instant.now());
		return toDomain(userRepository.save(entity));
	}

	@Override
	public void disable(UUID externalId) {
		UserJpaEntity entity = userRepository.findByExternalId(externalId)
				.orElseThrow(() -> new IllegalStateException("No user found for external id " + externalId));
		entity.setActive(false);
		entity.setUpdatedAt(Instant.now());
		userRepository.save(entity);
	}

	@Override
	public UserPage list(UserFilter filter) {
		Long branchId = filter.branchExternalId() != null ? resolveBranchId(filter.branchExternalId()) : null;
		String role = filter.role() != null ? filter.role().name() : null;

		Page<UserSpringDataRepository.UserSummaryProjection> page = userRepository.search(filter.active(), role,
				branchId, PageRequest.of(filter.page(), filter.size()));

		List<UserAccount> content = page.getContent().stream().map(this::toDomain).toList();
		return new UserPage(content, page.getTotalElements(), filter.page(), filter.size());
	}

	// Resolves a branch's external_id to its BIGINT foreign key. null in ⇒ null
	// out (a corporate ADMIN has no branch). A non-null external_id that
	// resolves to nothing is a genuine client input error, not an unfiltered
	// query — unlike AuditWriteAdapter's read-path -1 sentinel, this must
	// surface as a 400 (IamExceptionHandler's generic IllegalArgumentException
	// mapping), never silently create/update a branchless row.
	private Long resolveBranchId(UUID branchExternalId) {
		if (branchExternalId == null) {
			return null;
		}
		return userRepository.findBranchIdByExternalId(branchExternalId)
				.orElseThrow(() -> new IllegalArgumentException("No branch found for external id " + branchExternalId));
	}

	private UserAccount toDomain(UserSpringDataRepository.UserSummaryProjection projection) {
		Role role = Role.valueOf(projection.getRole());
		boolean active = projection.isActive()
				&& (projection.getBranchExternalId() == null || Boolean.TRUE.equals(projection.getBranchActive()));
		return new UserAccount(projection.getExternalId(), projection.getUsername(), projection.getEmail(),
				projection.getPasswordHash(), projection.getFullName(), role, projection.getBranchExternalId(), active);
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
