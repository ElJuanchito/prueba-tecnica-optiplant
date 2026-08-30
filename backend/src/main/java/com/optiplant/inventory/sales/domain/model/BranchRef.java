package com.optiplant.inventory.sales.domain.model;

import java.util.UUID;

/**
 * Branch reference descriptor for sale details and listings (contract §6).
 */
public record BranchRef(UUID externalId, String name) {
}
