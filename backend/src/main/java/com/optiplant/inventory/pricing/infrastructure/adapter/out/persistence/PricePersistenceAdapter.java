package com.optiplant.inventory.pricing.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.pricing.application.port.out.PriceRepositoryPort;
import com.optiplant.inventory.pricing.domain.exception.BranchNotFoundException;
import com.optiplant.inventory.pricing.domain.exception.PriceListNotFoundException;
import com.optiplant.inventory.pricing.domain.exception.ProductNotFoundException;
import com.optiplant.inventory.pricing.domain.model.Price;
import com.optiplant.inventory.pricing.infrastructure.adapter.out.persistence.PricingReferenceSpringDataRepository.IdExternalIdRow;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Implementation of {@link PriceRepositoryPort} (design §5, §6.1, §6.2).
 */
@Component
public class PricePersistenceAdapter implements PriceRepositoryPort {

	private final PriceListItemSpringDataRepository repository;
	private final PricingReferenceSpringDataRepository referenceRepository;
	private final PriceListItemMapper mapper;

	public PricePersistenceAdapter(PriceListItemSpringDataRepository repository,
			PricingReferenceSpringDataRepository referenceRepository, PriceListItemMapper mapper) {
		this.repository = repository;
		this.referenceRepository = referenceRepository;
		this.mapper = mapper;
	}

	@Override
	public List<Price> findOpen(UUID priceListExternalId, UUID productExternalId, UUID branchExternalId) {
		Long priceListId = requirePriceListId(priceListExternalId);
		Long productId = requireProductId(productExternalId);
		Long branchId = branchExternalId == null ? null : requireBranchId(branchExternalId);

		List<PriceListItemJpaEntity> openEntities = repository.findOpen(priceListId, productId, branchId);
		return openEntities.stream()
				.map(entity -> mapper.toDomain(entity, priceListExternalId, productExternalId, branchExternalId))
				.toList();
	}

	@Override
	public Optional<Price> findByExternalId(UUID externalId) {
		return repository.findByExternalId(externalId).map(this::toDomain);
	}

	@Override
	public Price save(Price price) {
		Optional<PriceListItemJpaEntity> existing = repository.findByExternalId(price.externalId());
		PriceListItemJpaEntity entity;
		if (existing.isPresent()) {
			entity = existing.get();
			mapper.applyState(entity, price);
		} else {
			Long priceListId = requirePriceListId(price.priceListExternalId());
			Long productId = requireProductId(price.productExternalId());
			Long branchId = price.branchExternalId() == null ? null : requireBranchId(price.branchExternalId());
			entity = mapper.toNewEntity(price, priceListId, productId, branchId);
		}
		PriceListItemJpaEntity saved = repository.save(entity);
		return toDomain(saved);
	}

	@Override
	public PricePage list(PriceFilter filter) {
		Long priceListId = requirePriceListId(filter.priceListExternalId());
		Long productId = filter.productExternalId() == null ? null : resolveProductIdOrSentinel(filter.productExternalId());
		Long branchId = filter.branchExternalId() == null ? null : resolveBranchIdOrSentinel(filter.branchExternalId());

		PageRequest pageRequest = PageRequest.of(filter.page(), filter.size());
		Page<PriceListItemJpaEntity> page = repository.search(priceListId, productId, branchId, filter.currentOnly(),
				LocalDate.now(), pageRequest);

		List<Price> content = toDomainList(page.getContent());
		return new PricePage(content, page.getTotalElements(), filter.page(), filter.size());
	}

	@Override
	public List<Price> findEligible(UUID priceListExternalId, UUID branchExternalId,
			Collection<UUID> productExternalIds, LocalDate operationDate) {
		Long priceListId = requirePriceListId(priceListExternalId);
		Long branchId = branchExternalId == null ? null : resolveBranchIdOrSentinel(branchExternalId);

		List<IdExternalIdRow> productRows = referenceRepository.findProductIds(productExternalIds);
		if (productRows.isEmpty()) {
			return List.of();
		}
		List<Long> productIds = productRows.stream().map(IdExternalIdRow::getId).toList();

		List<PriceListItemJpaEntity> entities = repository.findEligible(priceListId, branchId, productIds, operationDate);
		return toDomainList(entities);
	}

	private Price toDomain(PriceListItemJpaEntity entity) {
		Map<Long, UUID> priceListExternalIds = resolveExternalIds(Set.of(entity.getPriceListId()),
				referenceRepository::findPriceListExternalIds);
		Map<Long, UUID> productExternalIds = resolveExternalIds(Set.of(entity.getProductId()),
				referenceRepository::findProductExternalIds);
		Map<Long, UUID> branchExternalIds = entity.getBranchId() == null ? Map.of()
				: resolveExternalIds(Set.of(entity.getBranchId()), referenceRepository::findBranchExternalIds);

		return mapper.toDomain(entity, priceListExternalIds.get(entity.getPriceListId()),
				productExternalIds.get(entity.getProductId()),
				entity.getBranchId() == null ? null : branchExternalIds.get(entity.getBranchId()));
	}

	private List<Price> toDomainList(List<PriceListItemJpaEntity> entities) {
		if (entities.isEmpty()) {
			return List.of();
		}
		Set<Long> priceListIds = entities.stream().map(PriceListItemJpaEntity::getPriceListId).collect(Collectors.toSet());
		Set<Long> productIds = entities.stream().map(PriceListItemJpaEntity::getProductId).collect(Collectors.toSet());
		Set<Long> branchIds = entities.stream().map(PriceListItemJpaEntity::getBranchId).filter(b -> b != null)
				.collect(Collectors.toSet());

		Map<Long, UUID> priceListExternalIds = resolveExternalIds(priceListIds, referenceRepository::findPriceListExternalIds);
		Map<Long, UUID> productExternalIds = resolveExternalIds(productIds, referenceRepository::findProductExternalIds);
		Map<Long, UUID> branchExternalIds = resolveExternalIds(branchIds, referenceRepository::findBranchExternalIds);

		return entities.stream().map(entity -> mapper.toDomain(
				entity,
				priceListExternalIds.get(entity.getPriceListId()),
				productExternalIds.get(entity.getProductId()),
				entity.getBranchId() == null ? null : branchExternalIds.get(entity.getBranchId())
		)).toList();
	}

	private Map<Long, UUID> resolveExternalIds(Collection<Long> ids,
			Function<List<Long>, List<IdExternalIdRow>> lookup) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<Long, UUID> result = new HashMap<>();
		for (IdExternalIdRow row : lookup.apply(List.copyOf(ids))) {
			result.put(row.getId(), row.getExternalId());
		}
		return result;
	}

	private Long requirePriceListId(UUID externalId) {
		return referenceRepository.findPriceListIdByExternalId(externalId)
				.orElseThrow(() -> new PriceListNotFoundException(externalId));
	}

	private Long requireProductId(UUID externalId) {
		return referenceRepository.findActiveProductIdByExternalId(externalId)
				.orElseThrow(() -> new ProductNotFoundException(externalId));
	}

	private Long requireBranchId(UUID externalId) {
		return referenceRepository.findActiveBranchIdByExternalId(externalId)
				.orElseThrow(() -> new BranchNotFoundException(externalId));
	}

	private Long resolveProductIdOrSentinel(UUID externalId) {
		return referenceRepository.findActiveProductIdByExternalId(externalId).orElse(-1L);
	}

	private Long resolveBranchIdOrSentinel(UUID externalId) {
		return referenceRepository.findActiveBranchIdByExternalId(externalId).orElse(-1L);
	}
}
