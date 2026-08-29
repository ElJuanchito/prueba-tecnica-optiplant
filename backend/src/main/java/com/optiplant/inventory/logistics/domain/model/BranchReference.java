package com.optiplant.inventory.logistics.domain.model;

import java.util.UUID;

/** A branch as it appears in a route or compliance view. {@code logistics}' own copy — see {@link RoutePriority}. */
public record BranchReference(UUID externalId, String name) {
}
