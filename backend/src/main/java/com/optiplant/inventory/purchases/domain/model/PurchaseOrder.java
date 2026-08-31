package com.optiplant.inventory.purchases.domain.model;

import com.optiplant.inventory.purchases.domain.service.PurchaseOrderStateMachine;
import com.optiplant.inventory.purchases.domain.service.PurchaseReceptionPolicy.ReceptionLine;
import com.optiplant.inventory.purchases.domain.service.PurchaseReceptionPolicy.ReceptionPlan;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Domain representation of one {@code purchase_orders} row plus its items (design §3.2). Immutable
 * — every mutator returns a new instance after consulting {@link PurchaseOrderStateMachine} first
 * (R-10, R-11, R-14), so the five-state machine cannot be bypassed by reaching for a setter —
 * there are none. {@code items} is copied defensively.
 */
public record PurchaseOrder(UUID externalId, OrderNumber orderNumber, UUID branchExternalId,
		UUID supplierExternalId, UUID createdByUserExternalId, PurchaseOrderStatus status, String paymentTerms,
		Money totalAmount, PurchaseOrderNotes notes, Instant receivedAt, Instant createdAt, Instant updatedAt,
		List<PurchaseOrderItem> items) {

	public PurchaseOrder {
		if (externalId == null) {
			throw new IllegalArgumentException("externalId must not be null");
		}
		if (branchExternalId == null) {
			throw new IllegalArgumentException("branchExternalId must not be null");
		}
		if (supplierExternalId == null) {
			throw new IllegalArgumentException("supplierExternalId must not be null");
		}
		if (status == null) {
			throw new IllegalArgumentException("status must not be null");
		}
		if (totalAmount == null) {
			throw new IllegalArgumentException("totalAmount must not be null");
		}
		notes = notes == null ? PurchaseOrderNotes.empty() : notes;
		items = items == null ? List.of() : List.copyOf(items);
	}

	/** R-12 &rarr; {@code APPROVED}. */
	public PurchaseOrder approve(Instant at) {
		PurchaseOrderStateMachine.require(status, PurchaseOrderTransition.APPROVE);
		return copy(PurchaseOrderStatus.APPROVED, notes, receivedAt, items, at);
	}

	/**
	 * R-13 &rarr; {@code CANCELLED}. Records the F-3 token in {@code notes}; already-received stock
	 * is not reversed and no Kardex row is written or deleted (RN-12, PA-08).
	 *
	 * @throws com.optiplant.inventory.purchases.domain.exception.CancellationReasonRequiredException
	 *     {@code reason} is blank
	 */
	public PurchaseOrder cancel(String reason, Instant at) {
		PurchaseOrderStateMachine.require(status, PurchaseOrderTransition.CANCEL);
		return copy(PurchaseOrderStatus.CANCELLED, notes.withCancellationReason(reason), receivedAt, items, at);
	}

	/** R-10 &rarr; stays {@code PENDING}; replaces the item set and recomputed total. */
	public PurchaseOrder withItems(List<PurchaseOrderItem> newItems, Money newTotal, Instant at) {
		PurchaseOrderStateMachine.require(status, PurchaseOrderTransition.EDIT);
		return new PurchaseOrder(externalId, orderNumber, branchExternalId, supplierExternalId, createdByUserExternalId,
				status, paymentTerms, newTotal, notes, receivedAt, createdAt, at, List.copyOf(newItems));
	}

	/**
	 * R-19 &rarr; {@code plan.targetStatus()}. Accumulates {@code received_quantity} per named line
	 * and stamps {@code received_at}. Lines the plan does not name keep their stored quantity.
	 */
	public PurchaseOrder withReception(ReceptionPlan plan, Instant at) {
		PurchaseOrderStateMachine.require(status, PurchaseOrderTransition.RECEIVE);
		Map<UUID, BigDecimal> receivedByItem = new HashMap<>();
		for (ReceptionLine line : plan.lines()) {
			receivedByItem.merge(line.itemExternalId(), line.receivedQuantity(), BigDecimal::add);
		}
		List<PurchaseOrderItem> updatedItems = new ArrayList<>();
		for (PurchaseOrderItem item : items) {
			BigDecimal delta = receivedByItem.get(item.externalId());
			updatedItems.add(delta == null ? item : item.withAdditionalReceived(delta));
		}
		return copy(plan.targetStatus(), notes, at, updatedItems, at);
	}

	/** {@code true} when {@code branchId} is this order's branch. */
	public boolean belongsTo(UUID branchId) {
		return branchId != null && branchExternalId.equals(branchId);
	}

	private PurchaseOrder copy(PurchaseOrderStatus newStatus, PurchaseOrderNotes newNotes, Instant newReceivedAt,
			List<PurchaseOrderItem> newItems, Instant at) {
		return new PurchaseOrder(externalId, orderNumber, branchExternalId, supplierExternalId, createdByUserExternalId,
				newStatus, paymentTerms, totalAmount, newNotes, newReceivedAt, createdAt, at, List.copyOf(newItems));
	}
}
