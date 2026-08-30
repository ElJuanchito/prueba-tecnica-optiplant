package com.optiplant.inventory.sales.domain.model;

import com.optiplant.inventory.sales.domain.service.SaleStateMachine;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An aggregate root representing a completed or cancelled commercial sale (design §4).
 *
 * <p>Immutable with no setters; {@link #cancel(CancellationReason)} is the only state mutator
 * and delegates to {@link SaleStateMachine} to enforce R-18.
 */
public record Sale(
		UUID externalId,
		InvoiceNumber invoiceNumber,
		SaleStatus status,
		UUID branchExternalId,
		UUID soldByUserExternalId,
		UUID priceListExternalId,
		UUID customerExternalId,
		CustomerName customerName,
		CustomerTaxId customerTaxId,
		SaleTotals totals,
		SaleNotes notes,
		Instant createdAt,
		List<SaleItem> items
) {

	public Sale {
		if (externalId == null) {
			throw new IllegalArgumentException("externalId must not be null");
		}
		if (invoiceNumber == null) {
			throw new IllegalArgumentException("invoiceNumber must not be null");
		}
		if (status == null) {
			throw new IllegalArgumentException("status must not be null");
		}
		if (branchExternalId == null) {
			throw new IllegalArgumentException("branchExternalId must not be null");
		}
		if (soldByUserExternalId == null) {
			throw new IllegalArgumentException("soldByUserExternalId must not be null");
		}
		if (priceListExternalId == null) {
			throw new IllegalArgumentException("priceListExternalId must not be null");
		}
		if (customerName == null) {
			throw new IllegalArgumentException("customerName must not be null");
		}
		if (totals == null) {
			throw new IllegalArgumentException("totals must not be null");
		}
		if (createdAt == null) {
			throw new IllegalArgumentException("createdAt must not be null");
		}
		if (items == null || items.isEmpty()) {
			throw new IllegalArgumentException("items must not be empty");
		}
		items = List.copyOf(items);
	}

	public Sale cancel(CancellationReason reason) {
		SaleStateMachine.requireCancellable(this.status);
		SaleNotes updatedNotes = this.notes != null ? this.notes.withCancellationReason(reason)
				: new SaleNotes(reason, null);
		return new Sale(
				this.externalId,
				this.invoiceNumber,
				SaleStatus.CANCELLED,
				this.branchExternalId,
				this.soldByUserExternalId,
				this.priceListExternalId,
				this.customerExternalId,
				this.customerName,
				this.customerTaxId,
				this.totals,
				updatedNotes,
				this.createdAt,
				this.items
		);
	}

	public boolean belongsTo(UUID branchId) {
		return branchId != null && this.branchExternalId.equals(branchId);
	}
}
