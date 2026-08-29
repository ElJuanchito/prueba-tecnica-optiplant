package com.optiplant.inventory.inventory.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.inventory.application.port.out.KardexRepositoryPort;
import com.optiplant.inventory.inventory.domain.exception.ProductNotFoundException;
import com.optiplant.inventory.inventory.domain.model.DateRange;
import com.optiplant.inventory.inventory.domain.model.KardexLine;
import com.optiplant.inventory.inventory.domain.model.KardexMovement;
import com.optiplant.inventory.inventory.domain.model.KardexPage;
import com.optiplant.inventory.inventory.infrastructure.adapter.out.persistence.ForeignKeyResolverSpringDataRepository.IdExternalIdRow;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * The single {@link KardexRepositoryPort} implementation. Insert-only against
 * {@code kardex_movements}: no method here updates or deletes a row (R-17). Batch-resolves
 * product/user {@code external_id}s for a whole page instead of one native query per row
 * (RNF-PER-01).
 */
@Component
public class KardexPersistenceAdapter implements KardexRepositoryPort {

	private final KardexMovementSpringDataRepository kardexRepository;
	private final ForeignKeyResolverSpringDataRepository foreignKeyResolver;
	private final KardexMovementMapper mapper;

	public KardexPersistenceAdapter(KardexMovementSpringDataRepository kardexRepository,
			ForeignKeyResolverSpringDataRepository foreignKeyResolver, KardexMovementMapper mapper) {
		this.kardexRepository = kardexRepository;
		this.foreignKeyResolver = foreignKeyResolver;
		this.mapper = mapper;
	}

	@Override
	public KardexMovement append(NewMovement movement) {
		Long branchId = requireBranchId(movement.branchExternalId());
		Long productId = requireProductId(movement.productExternalId());
		Long userId = movement.userExternalId() == null ? null : requireUserId(movement.userExternalId());

		KardexMovementJpaEntity entity = new KardexMovementJpaEntity();
		entity.setBranchId(branchId);
		entity.setProductId(productId);
		entity.setMovementType(movement.movementType());
		entity.setQuantity(movement.quantity().value());
		entity.setUnitCost(movement.unitCost().value());
		entity.setTotalCost(movement.totalCost());
		entity.setPreviousStock(movement.previousStock());
		entity.setResultingStock(movement.resultingStock());
		entity.setReferenceType(movement.referenceType());
		entity.setReferenceId(movement.referenceId());
		entity.setNotes(movement.notes());
		entity.setUserId(userId);
		entity.setCreatedAt(Instant.now());

		KardexMovementJpaEntity saved = kardexRepository.save(entity);
		return mapper.toDomain(saved, movement.branchExternalId(), movement.productExternalId(),
				movement.userExternalId());
	}

	@Override
	public KardexPage list(KardexFilter filter) {
		Long branchId = filter.branchExternalId() == null ? null : resolveBranchIdOrSentinel(filter.branchExternalId());
		Long productId = filter.productExternalId() == null ? null
				: resolveProductIdOrSentinel(filter.productExternalId());
		DateRange range = filter.range();
		Instant from = range == null ? null : range.from();
		Instant to = range == null ? null : range.to();

		Page<KardexMovementJpaEntity> page = kardexRepository.search(branchId, productId, filter.movementType(), from,
				to, PageRequest.of(filter.page(), filter.size()));

		List<KardexMovementJpaEntity> rows = page.getContent();
		Map<Long, UUID> productExternalIds = resolveExternalIds(
				rows.stream().map(KardexMovementJpaEntity::getProductId).distinct().toList(),
				foreignKeyResolver::findProductExternalIds);
		Map<Long, UUID> userExternalIds = resolveExternalIds(
				rows.stream().map(KardexMovementJpaEntity::getUserId).filter(Objects::nonNull).distinct().toList(),
				foreignKeyResolver::findUserExternalIds);

		List<KardexLine> content = rows.stream()
				.map(row -> mapper.toLine(row, productExternalIds.get(row.getProductId()),
						row.getUserId() == null ? null : userExternalIds.get(row.getUserId())))
				.toList();
		return new KardexPage(content, page.getTotalElements(), filter.page(), filter.size());
	}

	@Override
	public boolean hasAnyMovement(UUID productExternalId) {
		Long productId = resolveProductIdOrSentinel(productExternalId);
		return kardexRepository.existsByProductId(productId);
	}

	private Map<Long, UUID> resolveExternalIds(List<Long> ids,
			Function<List<Long>, List<IdExternalIdRow>> lookup) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<Long, UUID> result = new HashMap<>();
		for (IdExternalIdRow row : lookup.apply(ids)) {
			result.put(row.getId(), row.getExternalId());
		}
		return result;
	}

	private Long requireBranchId(UUID externalId) {
		return foreignKeyResolver.findBranchIdByExternalId(externalId)
				.orElseThrow(() -> new IllegalStateException("No branch found for external id " + externalId));
	}

	private Long requireProductId(UUID externalId) {
		return foreignKeyResolver.findProductIdByExternalId(externalId)
				.orElseThrow(() -> new ProductNotFoundException(externalId));
	}

	private Long requireUserId(UUID externalId) {
		return foreignKeyResolver.findUserIdByExternalId(externalId)
				.orElseThrow(() -> new IllegalStateException("No user found for external id " + externalId));
	}

	private Long resolveBranchIdOrSentinel(UUID externalId) {
		return foreignKeyResolver.findBranchIdByExternalId(externalId).orElse(-1L);
	}

	private Long resolveProductIdOrSentinel(UUID externalId) {
		return foreignKeyResolver.findProductIdByExternalId(externalId).orElse(-1L);
	}
}
