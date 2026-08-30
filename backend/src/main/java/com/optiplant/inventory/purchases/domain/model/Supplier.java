package com.optiplant.inventory.purchases.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Supplier aggregate (design §3.2, R-01..R-04). Corporate data — no {@code branch_id} (R-02).
 * Immutable; {@link #disable()} / {@link #enable()} are logical (R-03), never a physical delete.
 */
public record Supplier(UUID externalId, SupplierTaxId taxId, SupplierName name, SupplierContact contact,
		boolean active, Instant createdAt, Instant updatedAt) {

	public Supplier {
		if (taxId == null) {
			throw new IllegalArgumentException("supplier taxId must not be null");
		}
		if (name == null) {
			throw new IllegalArgumentException("supplier name must not be null");
		}
		contact = contact == null ? SupplierContact.empty() : contact;
	}

	public static Supplier create(UUID externalId, SupplierTaxId taxId, SupplierName name, SupplierContact contact,
			Instant now) {
		return new Supplier(externalId != null ? externalId : UUID.randomUUID(), taxId, name,
				contact != null ? contact : SupplierContact.empty(), true, now, now);
	}

	public Supplier withDetails(SupplierName newName, SupplierContact newContact, Instant now) {
		return new Supplier(externalId, taxId, newName != null ? newName : name,
				newContact != null ? newContact : SupplierContact.empty(), active, createdAt, now);
	}

	public Supplier disable(Instant now) {
		return new Supplier(externalId, taxId, name, contact, false, createdAt, now);
	}

	public Supplier enable(Instant now) {
		return new Supplier(externalId, taxId, name, contact, true, createdAt, now);
	}
}
