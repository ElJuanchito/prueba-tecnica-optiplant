package com.optiplant.inventory.purchases.application.port.out;

import com.optiplant.inventory.purchases.domain.model.CostHistoryEntry;
import com.optiplant.inventory.purchases.domain.model.DiscountPercent;
import com.optiplant.inventory.purchases.domain.model.Money;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrder;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderNotes;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderStatus;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderSummary;
import com.optiplant.inventory.purchases.domain.model.PurchasePage;
import com.optiplant.inventory.purchases.domain.model.PurchaseQuantity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Secondary port for purchase order persistence (design §4). {@code create} allocates the
 * {@code OC-<yyyy>-<nnnn>} number under the advisory-lock technique (F-9, S2, design §6.2);
 * {@code lockForUpdate} is the {@code SELECT … FOR UPDATE} of F-5 / T-02 taken before every
 * transition and reception.
 */
public interface PurchaseOrderRepositoryPort {

	PurchaseOrder create(NewPurchaseOrder newOrder);

	/** {@code @Lock(PESSIMISTIC_WRITE)} on the {@code purchase_orders} row (F-5, T-02). */
	Optional<PurchaseOrder> lockForUpdate(UUID externalId);

	/** Unlocked read for the query side (T-05, RN-09). */
	Optional<PurchaseOrder> findByExternalId(UUID externalId);

	PurchaseOrder save(PurchaseOrder order);

	/** R-10 — replaces the item set atomically and stores the recomputed total (R-06). */
	PurchaseOrder replaceItems(PurchaseOrder order, List<NewPurchaseOrderItem> items, Money totalAmount);

	PurchasePage<PurchaseOrderSummary> list(PurchaseOrderFilter filter);

	PurchasePage<CostHistoryEntry> costHistory(CostHistoryFilter filter);

	record NewPurchaseOrder(UUID branchExternalId, UUID supplierExternalId, UUID createdByUserExternalId,
			String paymentTerms, PurchaseOrderNotes notes, Money totalAmount, List<NewPurchaseOrderItem> items) {
	}

	record NewPurchaseOrderItem(UUID productExternalId, PurchaseQuantity orderedQuantity, Money unitCost,
			DiscountPercent discountPercent, Money subtotal) {
	}

	record PurchaseOrderFilter(UUID callerBranchExternalId, UUID supplierExternalId, UUID productExternalId,
			PurchaseOrderStatus status, Instant from, Instant to, String sort, int page, int size) {
	}

	record CostHistoryFilter(UUID callerBranchExternalId, UUID productExternalId, UUID supplierExternalId,
			Instant from, Instant to, int page, int size) {
	}
}
