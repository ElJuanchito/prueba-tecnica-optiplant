package com.optiplant.inventory.inventory.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.inventory.application.port.out.BranchInventoryRepositoryPort;
import com.optiplant.inventory.inventory.domain.exception.InventoryRecordNotFoundException;
import com.optiplant.inventory.inventory.domain.exception.ProductNotFoundException;
import com.optiplant.inventory.inventory.domain.model.BranchAvailability;
import com.optiplant.inventory.inventory.domain.model.BranchInventory;
import com.optiplant.inventory.inventory.domain.model.StockLine;
import com.optiplant.inventory.inventory.domain.model.StockPage;
import com.optiplant.inventory.inventory.infrastructure.adapter.out.persistence.ForeignKeyResolverSpringDataRepository.ActiveBranchRow;
import com.optiplant.inventory.inventory.infrastructure.adapter.out.persistence.ForeignKeyResolverSpringDataRepository.IdExternalIdRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * The single {@link BranchInventoryRepositoryPort} implementation and the only class besides
 * {@link KardexPersistenceAdapter} that touches {@code branch_inventories}'s internal numeric
 * {@code id} — every returned value is an {@code external_id} UUID or a domain record (design
 * §6.1, §6.2).
 *
 * <p>{@link #createZeroed} sets {@code min_stock_threshold} explicitly to {@code 0} (F-3, design
 * §8) — never inheriting the schema default of {@code 10.0000}, which would make a brand-new
 * product fire {@code STOCK_MINIMUM} on its first movement. {@link #save} re-fetches the entity
 * by its own {@code external_id} rather than by branch/product, so it never re-acquires the
 * pessimistic lock a caller already holds in the same transaction.
 */
@Component
public class BranchInventoryPersistenceAdapter implements BranchInventoryRepositoryPort {

	private final BranchInventorySpringDataRepository branchInventoryRepository;
	private final ForeignKeyResolverSpringDataRepository foreignKeyResolver;
	private final BranchInventoryMapper mapper;

	public BranchInventoryPersistenceAdapter(BranchInventorySpringDataRepository branchInventoryRepository,
			ForeignKeyResolverSpringDataRepository foreignKeyResolver, BranchInventoryMapper mapper) {
		this.branchInventoryRepository = branchInventoryRepository;
		this.foreignKeyResolver = foreignKeyResolver;
		this.mapper = mapper;
	}

	@Override
	public Optional<BranchInventory> lockForUpdate(UUID branchExternalId, UUID productExternalId) {
		Long branchId = requireBranchId(branchExternalId);
		Long productId = requireProductId(productExternalId);
		return branchInventoryRepository.findByBranchIdAndProductId(branchId, productId)
				.map(entity -> mapper.toDomain(entity, branchExternalId, productExternalId));
	}

	@Override
	public BranchInventory createZeroed(UUID branchExternalId, UUID productExternalId) {
		Long branchId = requireBranchId(branchExternalId);
		Long productId = requireProductId(productExternalId);

		BranchInventoryJpaEntity entity = new BranchInventoryJpaEntity();
		entity.setBranchId(branchId);
		entity.setProductId(productId);
		BigDecimal zero = BigDecimal.ZERO.setScale(4);
		entity.setCurrentStock(zero);
		entity.setReservedStock(zero);
		entity.setInTransitStock(zero);
		// F-3: explicit zero, never the column's DEFAULT 10.0000.
		entity.setMinStockThreshold(zero);
		entity.setAverageCost(zero);
		entity.setLastUpdatedAt(Instant.now());

		BranchInventoryJpaEntity saved = branchInventoryRepository.save(entity);
		return mapper.toDomain(saved, branchExternalId, productExternalId);
	}

	@Override
	public BranchInventory save(BranchInventory inventory) {
		BranchInventoryJpaEntity entity = branchInventoryRepository.findByExternalId(inventory.externalId())
				.orElseThrow(() -> new InventoryRecordNotFoundException(inventory.branchExternalId(),
						inventory.productExternalId()));
		entity.setCurrentStock(inventory.currentStock().value());
		entity.setReservedStock(inventory.reservedStock().value());
		entity.setInTransitStock(inventory.inTransitStock().value());
		entity.setMinStockThreshold(inventory.minStockThreshold().value());
		entity.setAverageCost(inventory.averageCost().value());
		entity.setLastUpdatedAt(inventory.lastUpdatedAt());

		BranchInventoryJpaEntity saved = branchInventoryRepository.save(entity);
		return mapper.toDomain(saved, inventory.branchExternalId(), inventory.productExternalId());
	}

	@Override
	public StockPage list(StockFilter filter) {
		Long branchId = requireBranchId(filter.branchExternalId());
		Long productId = filter.productExternalId() == null ? null
				: resolveProductIdOrSentinel(filter.productExternalId());
		// "product" has no name/sku column on this table to sort by (design §6.1 forbids a
		// cross-module @Entity), so it sorts by the stable productId proxy; "currentStock"
		// sorts on the real column. Documented deviation — contract §6 names the sort key,
		// not its exact ordering semantics.
		String property = "currentStock".equals(filter.sort()) ? "currentStock" : "productId";
		Sort sort = Sort.by(Sort.Direction.ASC, property);

		Page<BranchInventoryJpaEntity> page = branchInventoryRepository.search(branchId, productId,
				filter.belowThresholdOnly(), PageRequest.of(filter.page(), filter.size(), sort));

		List<Long> productIds = page.getContent().stream().map(BranchInventoryJpaEntity::getProductId).distinct()
				.toList();
		Map<Long, UUID> productExternalIds = resolveProductExternalIds(productIds);

		List<StockLine> content = page.getContent().stream()
				.map(entity -> toStockLine(entity, productExternalIds.get(entity.getProductId()))).toList();
		return new StockPage(content, page.getTotalElements(), filter.page(), filter.size());
	}

	@Override
	public List<BranchAvailability> findAcrossActiveBranches(UUID productExternalId) {
		Long productId = requireProductId(productExternalId);
		List<ActiveBranchRow> activeBranches = foreignKeyResolver.findActiveBranches();
		List<Long> branchIds = activeBranches.stream().map(ActiveBranchRow::getId).toList();

		Map<Long, BranchInventoryJpaEntity> byBranch = new HashMap<>();
		for (BranchInventoryJpaEntity entity : branchInventoryRepository.findByProductIdAndBranchIdIn(productId,
				branchIds)) {
			byBranch.put(entity.getBranchId(), entity);
		}

		BigDecimal zero = BigDecimal.ZERO.setScale(4);
		return activeBranches.stream().map(row -> {
			BranchInventoryJpaEntity entity = byBranch.get(row.getId());
			BigDecimal current = entity == null ? zero : entity.getCurrentStock();
			BigDecimal reserved = entity == null ? zero : entity.getReservedStock();
			BigDecimal inTransit = entity == null ? zero : entity.getInTransitStock();
			// isOwnBranch is left null here; StockQueryService.networkAvailability marks it
			// against the caller's own branch (R-04).
			return new BranchAvailability(row.getExternalId(), row.getName(), current, reserved, inTransit,
					current.subtract(reserved), null);
		}).toList();
	}

	@Override
	public boolean hasAnyBalance(UUID productExternalId) {
		Long productId = resolveProductIdOrSentinel(productExternalId);
		return branchInventoryRepository.existsAnyNonZeroBalance(productId);
	}

	private StockLine toStockLine(BranchInventoryJpaEntity entity, UUID productExternalId) {
		BigDecimal current = entity.getCurrentStock();
		BigDecimal reserved = entity.getReservedStock();
		return new StockLine(productExternalId, null, null, current, reserved, entity.getInTransitStock(),
				current.subtract(reserved), entity.getMinStockThreshold(), entity.getAverageCost(),
				entity.getLastUpdatedAt());
	}

	private Map<Long, UUID> resolveProductExternalIds(List<Long> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<Long, UUID> result = new HashMap<>();
		for (IdExternalIdRow row : foreignKeyResolver.findProductExternalIds(ids)) {
			result.put(row.getId(), row.getExternalId());
		}
		return result;
	}

	// Write path: the branch/product an actor's own session or command names must exist —
	// its absence is a data-integrity problem for the branch (mirrors AuditWriteAdapter), and
	// a genuine client-facing 404 product_not_found for the product (contract §7).
	private Long requireBranchId(UUID externalId) {
		return foreignKeyResolver.findBranchIdByExternalId(externalId)
				.orElseThrow(() -> new IllegalStateException("No branch found for external id " + externalId));
	}

	private Long requireProductId(UUID externalId) {
		return foreignKeyResolver.findProductIdByExternalId(externalId)
				.orElseThrow(() -> new ProductNotFoundException(externalId));
	}

	// Read/filter path: an external id that matches nothing must yield an empty result, not an
	// error — mirrors AuditWriteAdapter's -1L sentinel (GENERATED ALWAYS AS IDENTITY never
	// assigns it).
	private Long resolveProductIdOrSentinel(UUID externalId) {
		return foreignKeyResolver.findProductIdByExternalId(externalId).orElse(-1L);
	}
}
