package com.optiplant.inventory.transfers.domain.model;

import java.util.UUID;

/** A branch as it appears in a transfer view (contract §6: {@code { externalId, name }}). */
public record BranchReference(UUID externalId, String name) {
}
