package com.optiplant.inventory.pricing.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.pricing.application.port.out.PriceListRepositoryPort;
import com.optiplant.inventory.pricing.domain.model.PriceList;
import com.optiplant.inventory.pricing.domain.model.PriceListCode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Implementation of {@link PriceListRepositoryPort} (design §5, §6.1).
 */
@Component
public class PriceListPersistenceAdapter implements PriceListRepositoryPort {

	private final PriceListSpringDataRepository repository;
	private final PriceListMapper mapper;

	public PriceListPersistenceAdapter(PriceListSpringDataRepository repository, PriceListMapper mapper) {
		this.repository = repository;
		this.mapper = mapper;
	}

	@Override
	public PriceList save(PriceList priceList) {
		Optional<PriceListJpaEntity> existing = repository.findByExternalId(priceList.externalId());
		PriceListJpaEntity entity;
		if (existing.isPresent()) {
			entity = existing.get();
			mapper.applyState(entity, priceList);
		} else {
			entity = mapper.toNewEntity(priceList);
		}
		PriceListJpaEntity saved = repository.save(entity);
		return mapper.toDomain(saved);
	}

	@Override
	public Optional<PriceList> findByExternalId(UUID externalId) {
		return repository.findByExternalId(externalId).map(mapper::toDomain);
	}

	@Override
	public Optional<PriceList> findByCode(PriceListCode code) {
		return repository.findByCode(code.value()).map(mapper::toDomain);
	}

	@Override
	public Optional<PriceList> findActiveDefaultListForBranch(UUID branchExternalId) {
		return repository.findActiveDefaultListForBranch(branchExternalId).map(mapper::toDomain);
	}

	@Override
	public PriceListPage list(PriceListFilter filter) {
		PageRequest pageRequest = PageRequest.of(filter.page(), filter.size());
		Page<PriceListJpaEntity> page = repository.search(filter.active(), pageRequest);
		List<PriceList> content = page.getContent().stream().map(mapper::toDomain).toList();
		return new PriceListPage(content, page.getTotalElements(), filter.page(), filter.size());
	}

	@Override
	public Map<UUID, PriceList> findByExternalIds(Collection<UUID> externalIds) {
		if (externalIds == null || externalIds.isEmpty()) {
			return Map.of();
		}
		return repository.findByExternalIdIn(externalIds).stream()
				.map(mapper::toDomain)
				.collect(Collectors.toMap(PriceList::externalId, Function.identity()));
	}
}
