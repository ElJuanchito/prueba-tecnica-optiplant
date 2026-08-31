package com.optiplant.inventory.purchases.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.purchases.application.port.out.PurchaseOrderRepositoryPort.NewPurchaseOrder;
import com.optiplant.inventory.purchases.application.port.out.PurchaseOrderRepositoryPort.NewPurchaseOrderItem;
import com.optiplant.inventory.purchases.domain.model.DiscountPercent;
import com.optiplant.inventory.purchases.domain.model.Money;
import com.optiplant.inventory.purchases.domain.model.OrderNumber;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrder;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderItem;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderNotes;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderStatus;
import com.optiplant.inventory.purchases.domain.model.PurchaseQuantity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Entity &harr; domain mapping for {@code purchase_orders} / {@code purchase_order_items} (design §6.1).
 * The sole reader and writer of the F-3 cancellation token ({@link PurchaseOrderNotes#render()} /
 * {@link PurchaseOrderNotes#parse(String)}).
 */
@Component
public class PurchaseOrderMapper {

	public PurchaseOrder toDomain(PurchaseOrderJpaEntity entity, UUID branchExternalId,
			UUID supplierExternalId, UUID userExternalId,
			Map<Long, UUID> productExternalIdsByProductId) {
		if (entity == null) {
			return null;
		}
		List<PurchaseOrderItem> items = entity.getItems().stream()
				.map(item -> toDomainItem(item, productExternalIdsByProductId.get(item.getProductId())))
				.toList();
		return new PurchaseOrder(
				entity.getExternalId(),
				new OrderNumber(entity.getOrderNumber()),
				branchExternalId,
				supplierExternalId,
				userExternalId,
				PurchaseOrderStatus.valueOf(entity.getStatus()),
				entity.getPaymentTerms(),
				new Money(entity.getTotalAmount()),
				PurchaseOrderNotes.parse(entity.getNotes()),
				entity.getReceivedAt(),
				entity.getCreatedAt(),
				entity.getUpdatedAt(),
				items
		);
	}

	public PurchaseOrderJpaEntity toNewEntity(NewPurchaseOrder newOrder, String orderNumber,
			Long branchId, Long supplierId, Long userId,
			Map<UUID, Long> productIdsByExternalId, Instant now) {
		PurchaseOrderJpaEntity entity = new PurchaseOrderJpaEntity();
		entity.setOrderNumber(orderNumber);
		entity.setBranchId(branchId);
		entity.setSupplierId(supplierId);
		entity.setUserId(userId);
		entity.setStatus(PurchaseOrderStatus.PENDING.name());
		entity.setPaymentTerms(newOrder.paymentTerms());
		entity.setTotalAmount(newOrder.totalAmount().value());
		entity.setNotes(newOrder.notes() != null ? newOrder.notes().render() : null);
		entity.setCreatedAt(now);
		entity.setUpdatedAt(now);
		for (NewPurchaseOrderItem item : newOrder.items()) {
			PurchaseOrderItemJpaEntity itemEntity = new PurchaseOrderItemJpaEntity();
			itemEntity.setProductId(productIdsByExternalId.get(item.productExternalId()));
			itemEntity.setOrderedQuantity(item.orderedQuantity().value());
			itemEntity.setReceivedQuantity(BigDecimal.ZERO);
			itemEntity.setUnitCost(item.unitCost().value());
			itemEntity.setDiscountPercent(item.discountPercent().value());
			itemEntity.setSubtotal(item.subtotal().value());
			entity.addItem(itemEntity);
		}
		return entity;
	}

	public void applyState(PurchaseOrderJpaEntity entity, PurchaseOrder order, Long supplierId) {
		entity.setStatus(order.status().name());
		entity.setSupplierId(supplierId);
		entity.setPaymentTerms(order.paymentTerms());
		entity.setTotalAmount(order.totalAmount().value());
		entity.setNotes(order.notes() != null ? order.notes().render() : null);
		entity.setReceivedAt(order.receivedAt());
		entity.setUpdatedAt(order.updatedAt() != null ? order.updatedAt() : Instant.now());

		Map<UUID, PurchaseOrderItemJpaEntity> byExternalId = entity.getItems().stream()
				.collect(Collectors.toMap(PurchaseOrderItemJpaEntity::getExternalId, Function.identity()));
		for (PurchaseOrderItem item : order.items()) {
			PurchaseOrderItemJpaEntity itemEntity = byExternalId.get(item.externalId());
			if (itemEntity != null) {
				itemEntity.setReceivedQuantity(item.receivedQuantity());
			}
		}
	}

	private PurchaseOrderItem toDomainItem(PurchaseOrderItemJpaEntity entity, UUID productExternalId) {
		return new PurchaseOrderItem(
				entity.getExternalId(),
				productExternalId,
				new PurchaseQuantity(entity.getOrderedQuantity()),
				entity.getReceivedQuantity(),
				new Money(entity.getUnitCost()),
				new DiscountPercent(entity.getDiscountPercent()),
				new Money(entity.getSubtotal())
		);
	}
}
