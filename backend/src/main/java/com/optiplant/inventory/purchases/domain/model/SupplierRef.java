package com.optiplant.inventory.purchases.domain.model;

import java.util.UUID;

/**
 * Supplier reference descriptor for order details, listings and cost history (contract §6).
 */
public record SupplierRef(UUID externalId, String taxId, String name) {
}
