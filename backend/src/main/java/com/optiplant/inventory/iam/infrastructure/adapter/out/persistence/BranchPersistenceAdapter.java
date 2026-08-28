package com.optiplant.inventory.iam.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.iam.application.port.out.BranchRepositoryPort;
import com.optiplant.inventory.iam.domain.model.BranchProfile;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
public class BranchPersistenceAdapter implements BranchRepositoryPort {

	private final BranchSpringDataRepository branchRepository;
	private final BranchMapper branchMapper;

	public BranchPersistenceAdapter(BranchSpringDataRepository branchRepository, BranchMapper branchMapper) {
		this.branchRepository = branchRepository;
		this.branchMapper = branchMapper;
	}

	@Override
	public Optional<BranchProfile> findByCode(String code) {
		return branchRepository.findByCode(code).map(branchMapper::toDomain);
	}

	@Override
	public Optional<BranchProfile> findByExternalId(UUID externalId) {
		return branchRepository.findByExternalId(externalId).map(branchMapper::toDomain);
	}

	@Override
	public BranchProfile create(NewBranch newBranch) {
		BranchJpaEntity entity = new BranchJpaEntity();
		entity.setCode(newBranch.code());
		entity.setName(newBranch.name());
		entity.setAddress(newBranch.address());
		entity.setCity(newBranch.city());
		entity.setPhone(newBranch.phone());
		entity.setActive(true);
		entity.setCreatedAt(Instant.now());
		entity.setUpdatedAt(Instant.now());
		return branchMapper.toDomain(branchRepository.save(entity));
	}

	@Override
	public BranchProfile update(UUID externalId, BranchUpdate update) {
		BranchJpaEntity entity = branchRepository.findByExternalId(externalId)
				.orElseThrow(() -> new IllegalStateException("No branch found for external id " + externalId));
		entity.setName(update.name());
		entity.setAddress(update.address());
		entity.setCity(update.city());
		entity.setPhone(update.phone());
		entity.setUpdatedAt(Instant.now());
		return branchMapper.toDomain(branchRepository.save(entity));
	}

	@Override
	public void disable(UUID externalId) {
		BranchJpaEntity entity = branchRepository.findByExternalId(externalId)
				.orElseThrow(() -> new IllegalStateException("No branch found for external id " + externalId));
		entity.setActive(false);
		entity.setUpdatedAt(Instant.now());
		branchRepository.save(entity);
	}

	@Override
	public BranchPage list(BranchFilter filter) {
		Page<BranchJpaEntity> page = branchRepository.search(filter.active(),
				PageRequest.of(filter.page(), filter.size()));
		List<BranchProfile> content = page.getContent().stream().map(branchMapper::toDomain).toList();
		return new BranchPage(content, page.getTotalElements(), filter.page(), filter.size());
	}
}
