package com.optiplant.inventory.purchases.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.purchases.application.port.out.PurchaseOrderRepositoryPort;
import com.optiplant.inventory.purchases.domain.exception.DuplicateOrderNumberException;
import com.optiplant.inventory.purchases.domain.exception.ProductNotFoundException;
import com.optiplant.inventory.purchases.domain.exception.SupplierNotFoundException;
import com.optiplant.inventory.purchases.domain.model.BranchRef;
import com.optiplant.inventory.purchases.domain.model.CostHistoryEntry;
import com.optiplant.inventory.purchases.domain.model.DiscountPercent;
import com.optiplant.inventory.purchases.domain.model.Money;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrder;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderStatus;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderSummary;
import com.optiplant.inventory.purchases.domain.model.PurchasePage;
import com.optiplant.inventory.purchases.domain.model.SupplierRef;
import com.optiplant.inventory.purchases.infrastructure.adapter.out.persistence.PurchaseOrderSpringDataRepository.CostHistoryRow;
import com.optiplant.inventory.purchases.infrastructure.adapter.out.persistence.PurchaseReferenceSpringDataRepository.IdExternalIdRow;
import com.optiplant.inventory.purchases.infrastructure.adapter.out.persistence.PurchaseReferenceSpringDataRepository.IdNameRow;
import com.optiplant.inventory.purchases.infrastructure.adapter.out.persistence.PurchaseReferenceSpringDataRepository.SupplierRefRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Implementation of {@link PurchaseOrderRepositoryPort} (design §4, §6.1, §6.2, §6.3, §7).
 */
@Component
public class PurchaseOrderPersistenceAdapter implements PurchaseOrderRepositoryPort {

	private final PurchaseOrderSpringDataRepository orderRepository;
	private final PurchaseReferenceSpringDataRepository referenceRepository;
	private final PurchaseOrderMapper mapper;

	public PurchaseOrderPersistenceAdapter(PurchaseOrderSpringDataRepository orderRepository,
			PurchaseReferenceSpringDataRepository referenceRepository, PurchaseOrderMapper mapper) {
		this.orderRepository = orderRepository;
		this.referenceRepository = referenceRepository;
		this.mapper = mapper;
	}

	@Override
	public PurchaseOrder create(NewPurchaseOrder newOrder) {
		Long branchId = requireBranchId(newOrder.branchExternalId());
		Long supplierId = requireSupplierId(newOrder.supplierExternalId());
		Long userId = requireUserId(newOrder.createdByUserExternalId());

		Map<UUID, Long> productIdsByExternalId = new HashMap<>();
		for (NewPurchaseOrderItem item : newOrder.items()) {
			productIdsByExternalId.computeIfAbsent(item.productExternalId(), this::requireProductId);
		}

		int year = Year.now().getValue();
		orderRepository.allocateAdvisoryLock("purchase_order_number:" + year);
		int sequence = orderRepository.nextSequenceNumber("OC-" + year + "-%");
		String orderNumber = "OC-%d-%04d".formatted(year, sequence);

		Instant now = Instant.now();
		PurchaseOrderJpaEntity entity = mapper.toNewEntity(newOrder, orderNumber, branchId, supplierId, userId,
				productIdsByExternalId, now);
		try {
			PurchaseOrderJpaEntity saved = orderRepository.saveAndFlush(entity);
			Map<Long, UUID> productExternalIdsByProductId = new HashMap<>();
			productIdsByExternalId.forEach((extId, id) -> productExternalIdsByProductId.put(id, extId));
			return mapper.toDomain(saved, newOrder.branchExternalId(), newOrder.supplierExternalId(),
					newOrder.createdByUserExternalId(), productExternalIdsByProductId);
		} catch (DataIntegrityViolationException ex) {
			throw new DuplicateOrderNumberException(orderNumber);
		}
	}

	@Override
	public Optional<PurchaseOrder> lockForUpdate(UUID externalId) {
		return orderRepository.findByExternalId(externalId).map(this::toDomain);
	}

	@Override
	public Optional<PurchaseOrder> findByExternalId(UUID externalId) {
		return orderRepository.findDetailByExternalId(externalId).map(this::toDomain);
	}

	@Override
	public PurchaseOrder save(PurchaseOrder order) {
		PurchaseOrderJpaEntity entity = orderRepository.findDetailByExternalId(order.externalId())
				.orElseThrow(() -> new IllegalStateException("No purchase order found for external id " + order.externalId()));
		Long supplierId = requireSupplierId(order.supplierExternalId());
		mapper.applyState(entity, order, supplierId);
		try {
			PurchaseOrderJpaEntity saved = orderRepository.saveAndFlush(entity);
			return toDomain(saved);
		} catch (DataIntegrityViolationException ex) {
			throw new DuplicateOrderNumberException(order.orderNumber() != null ? order.orderNumber().value() : "");
		}
	}

	@Override
	public PurchaseOrder replaceItems(PurchaseOrder order, List<NewPurchaseOrderItem> items, Money totalAmount) {
		PurchaseOrderJpaEntity entity = orderRepository.findDetailByExternalId(order.externalId())
				.orElseThrow(() -> new IllegalStateException("No purchase order found for external id " + order.externalId()));

		Long supplierId = requireSupplierId(order.supplierExternalId());
		entity.setSupplierId(supplierId);
		entity.setPaymentTerms(order.paymentTerms());
		entity.setNotes(order.notes() != null ? order.notes().render() : null);
		entity.setTotalAmount(totalAmount.value());
		entity.setUpdatedAt(order.updatedAt() != null ? order.updatedAt() : Instant.now());

		Map<UUID, Long> productIdsByExternalId = new HashMap<>();
		for (NewPurchaseOrderItem item : items) {
			productIdsByExternalId.computeIfAbsent(item.productExternalId(), this::requireProductId);
		}

		entity.getItems().clear();
		for (NewPurchaseOrderItem item : items) {
			PurchaseOrderItemJpaEntity itemEntity = new PurchaseOrderItemJpaEntity();
			itemEntity.setExternalId(UUID.randomUUID());
			itemEntity.setProductId(productIdsByExternalId.get(item.productExternalId()));
			itemEntity.setOrderedQuantity(item.orderedQuantity().value());
			itemEntity.setReceivedQuantity(BigDecimal.ZERO);
			itemEntity.setUnitCost(item.unitCost().value());
			itemEntity.setDiscountPercent(item.discountPercent().value());
			itemEntity.setSubtotal(item.subtotal().value());
			entity.addItem(itemEntity);
		}

		PurchaseOrderJpaEntity saved = orderRepository.saveAndFlush(entity);
		return toDomain(saved);
	}

	@Override
	public PurchasePage<PurchaseOrderSummary> list(PurchaseOrderFilter filter) {
		Long branchId = filter.callerBranchExternalId() == null ? null
				: resolveBranchIdOrSentinel(filter.callerBranchExternalId());
		Long supplierId = filter.supplierExternalId() == null ? null
				: resolveSupplierIdOrSentinel(filter.supplierExternalId());
		Long productId = filter.productExternalId() == null ? null
				: resolveProductIdOrSentinel(filter.productExternalId());
		String status = filter.status() == null ? null : filter.status().name();

		PageRequest pageRequest = PageRequest.of(filter.page(), filter.size());
		Page<PurchaseOrderJpaEntity> page = "totalAmount".equals(filter.sort())
				? orderRepository.searchOrderByTotalAmount(branchId, supplierId, productId, status, filter.from(), filter.to(), pageRequest)
				: orderRepository.searchOrderByCreatedAt(branchId, supplierId, productId, status, filter.from(), filter.to(), pageRequest);

		Set<Long> branchIds = new HashSet<>();
		Set<Long> supplierIds = new HashSet<>();
		for (PurchaseOrderJpaEntity entity : page.getContent()) {
			branchIds.add(entity.getBranchId());
			supplierIds.add(entity.getSupplierId());
		}

		Map<Long, UUID> branchExtIds = resolveExternalIds(branchIds, referenceRepository::findBranchExternalIds);
		Map<Long, String> branchNames = resolveNames(branchIds, referenceRepository::findBranchNamesByIds);
		Map<Long, SupplierRef> supplierRefs = resolveSupplierRefs(supplierIds);

		List<PurchaseOrderSummary> content = page.getContent().stream().map(entity -> {
			UUID bExt = branchExtIds.get(entity.getBranchId());
			String bName = branchNames.get(entity.getBranchId());
			SupplierRef supRef = supplierRefs.get(entity.getSupplierId());
			return new PurchaseOrderSummary(
					entity.getExternalId(),
					entity.getOrderNumber(),
					PurchaseOrderStatus.valueOf(entity.getStatus()),
					new BranchRef(bExt, bName),
					supRef,
					entity.getTotalAmount(),
					entity.getCreatedAt(),
					entity.getReceivedAt()
			);
		}).toList();

		return new PurchasePage<>(content, page.getTotalElements(), filter.page(), filter.size());
	}

	@Override
	public PurchasePage<CostHistoryEntry> costHistory(CostHistoryFilter filter) {
		Long productId = requireProductId(filter.productExternalId());
		Long supplierId = filter.supplierExternalId() == null ? null
				: resolveSupplierIdOrSentinel(filter.supplierExternalId());
		Long branchId = filter.callerBranchExternalId() == null ? null
				: resolveBranchIdOrSentinel(filter.callerBranchExternalId());

		PageRequest pageRequest = PageRequest.of(filter.page(), filter.size());
		Page<CostHistoryRow> page = orderRepository.findCostHistory(productId, supplierId, branchId,
				filter.from(), filter.to(), pageRequest);

		List<CostHistoryEntry> content = page.getContent().stream().map(row -> {
			Money unitCost = new Money(row.getUnitCost());
			DiscountPercent discountPercent = new DiscountPercent(row.getDiscountPercent());
			BigDecimal effectiveUnitCost = unitCost.multiply(discountPercent.complementFactor()).value();
			return new CostHistoryEntry(
					row.getOrderExternalId(),
					row.getOrderNumber(),
					new SupplierRef(row.getSupplierExternalId(), row.getSupplierTaxId(), row.getSupplierName()),
					row.getUnitCost(),
					row.getDiscountPercent(),
					effectiveUnitCost,
					row.getQuantity(),
					row.getOrderedAt(),
					row.getReceivedAt()
			);
		}).toList();

		return new PurchasePage<>(content, page.getTotalElements(), filter.page(), filter.size());
	}

	private PurchaseOrder toDomain(PurchaseOrderJpaEntity entity) {
		Map<Long, UUID> branchExtIds = resolveExternalIds(Set.of(entity.getBranchId()),
				referenceRepository::findBranchExternalIds);
		Map<Long, UUID> supplierExtIds = resolveExternalIds(Set.of(entity.getSupplierId()),
				referenceRepository::findSupplierExternalIds);
		Map<Long, UUID> userExtIds = resolveExternalIds(Set.of(entity.getUserId()),
				referenceRepository::findUserExternalIds);

		Set<Long> productIds = entity.getItems().stream()
				.map(PurchaseOrderItemJpaEntity::getProductId)
				.collect(Collectors.toSet());
		Map<Long, UUID> productExtIds = resolveExternalIds(productIds, referenceRepository::findProductExternalIds);

		return mapper.toDomain(entity, branchExtIds.get(entity.getBranchId()),
				supplierExtIds.get(entity.getSupplierId()), userExtIds.get(entity.getUserId()), productExtIds);
	}

	private Map<Long, SupplierRef> resolveSupplierRefs(Collection<Long> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<Long, SupplierRef> result = new HashMap<>();
		for (SupplierRefRow row : referenceRepository.findSupplierRefs(List.copyOf(ids))) {
			result.put(row.getId(), new SupplierRef(row.getExternalId(), row.getTaxId(), row.getName()));
		}
		return result;
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

	private Map<Long, String> resolveNames(Collection<Long> ids,
			Function<List<Long>, List<IdNameRow>> lookup) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<Long, String> result = new HashMap<>();
		for (IdNameRow row : lookup.apply(List.copyOf(ids))) {
			result.put(row.getId(), row.getName());
		}
		return result;
	}

	private Long requireBranchId(UUID externalId) {
		return referenceRepository.findActiveBranchIdByExternalId(externalId)
				.orElseThrow(() -> new IllegalStateException("No active branch found for external id " + externalId));
	}

	private Long requireSupplierId(UUID externalId) {
		return referenceRepository.findSupplierIdByExternalId(externalId)
				.orElseThrow(() -> new SupplierNotFoundException(externalId));
	}

	private Long requireUserId(UUID externalId) {
		return referenceRepository.findUserIdByExternalId(externalId)
				.orElseThrow(() -> new IllegalStateException("No user found for external id " + externalId));
	}

	private Long requireProductId(UUID externalId) {
		return referenceRepository.findActiveProductIdByExternalId(externalId)
				.orElseThrow(() -> new ProductNotFoundException(externalId));
	}

	private Long resolveBranchIdOrSentinel(UUID externalId) {
		return referenceRepository.findActiveBranchIdByExternalId(externalId).orElse(-1L);
	}

	private Long resolveSupplierIdOrSentinel(UUID externalId) {
		return referenceRepository.findSupplierIdByExternalId(externalId).orElse(-1L);
	}

	private Long resolveProductIdOrSentinel(UUID externalId) {
		return referenceRepository.findProductIdByExternalId(externalId).orElse(-1L);
	}
}
