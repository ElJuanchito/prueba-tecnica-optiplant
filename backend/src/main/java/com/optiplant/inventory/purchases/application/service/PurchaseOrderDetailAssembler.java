package com.optiplant.inventory.purchases.application.service;

import com.optiplant.inventory.purchases.application.port.out.PurchaseReferencePort;
import com.optiplant.inventory.purchases.application.port.out.PurchaseReferencePort.BranchDescriptor;
import com.optiplant.inventory.purchases.application.port.out.PurchaseReferencePort.ProductDescriptor;
import com.optiplant.inventory.purchases.application.port.out.PurchaseReferencePort.SupplierDescriptor;
import com.optiplant.inventory.purchases.application.port.out.PurchaseReferencePort.UserDescriptor;
import com.optiplant.inventory.purchases.domain.model.BranchRef;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrder;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderDetail;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderItem;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderItemView;
import com.optiplant.inventory.purchases.domain.model.SupplierRef;
import com.optiplant.inventory.purchases.domain.model.UserRef;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Enriches a {@link PurchaseOrder} with descriptors from {@link PurchaseReferencePort} into a
 * {@link PurchaseOrderDetail} (contract §6). Batch lookups only — one call per descriptor kind.
 * The F-3 token never reaches this class: {@code notes.humanNote()} and
 * {@code notes.cancellationReason()} are already parsed.
 */
final class PurchaseOrderDetailAssembler {

	private PurchaseOrderDetailAssembler() {
	}

	static PurchaseOrderDetail toDetail(PurchaseOrder order, PurchaseReferencePort referencePort) {
		Map<UUID, BranchDescriptor> branches = referencePort.findBranches(Set.of(order.branchExternalId()));
		Map<UUID, SupplierDescriptor> suppliers = referencePort.findSuppliers(Set.of(order.supplierExternalId()));
		Map<UUID, UserDescriptor> users = order.createdByUserExternalId() == null ? Map.of()
				: referencePort.findUsers(Set.of(order.createdByUserExternalId()));

		Set<UUID> productIds = order.items().stream()
				.map(PurchaseOrderItem::productExternalId)
				.collect(Collectors.toSet());
		Map<UUID, ProductDescriptor> products = productIds.isEmpty() ? Map.of()
				: referencePort.findProducts(productIds);

		List<PurchaseOrderItemView> itemViews = order.items().stream()
				.map(item -> {
					ProductDescriptor product = products.get(item.productExternalId());
					return new PurchaseOrderItemView(
							item.externalId(),
							item.productExternalId(),
							product != null ? product.sku() : null,
							product != null ? product.name() : null,
							item.orderedQuantity().value(),
							item.receivedQuantity(),
							item.pendingQuantity(),
							item.unitCost().value(),
							item.discountPercent().value(),
							item.effectiveUnitCost().value(),
							item.subtotal().value());
				})
				.toList();

		BranchDescriptor branch = branches.get(order.branchExternalId());
		BranchRef branchRef = new BranchRef(order.branchExternalId(), branch != null ? branch.name() : null);

		SupplierDescriptor supplier = suppliers.get(order.supplierExternalId());
		SupplierRef supplierRef = new SupplierRef(order.supplierExternalId(),
				supplier != null ? supplier.taxId() : null, supplier != null ? supplier.name() : null);

		UserDescriptor user = users.get(order.createdByUserExternalId());
		UserRef userRef = new UserRef(order.createdByUserExternalId(), user != null ? user.username() : null);

		return new PurchaseOrderDetail(
				order.externalId(),
				order.orderNumber() != null ? order.orderNumber().value() : null,
				order.status(),
				branchRef,
				supplierRef,
				userRef,
				order.paymentTerms(),
				order.totalAmount().value(),
				order.notes().humanNote(),
				order.notes().cancellationReason(),
				order.createdAt(),
				order.updatedAt(),
				order.receivedAt(),
				itemViews);
	}
}
