package com.optiplant.inventory.logistics.domain.model;

/**
 * The three {@code logistics_routes.priority_level} literals (design §4). {@code logistics}
 * declares its own copy rather than importing {@code transfers}' {@code TransferPriority} —
 * boundary rule 3 forbids the import, even though the values coincide.
 */
public enum RoutePriority {
	LOW, STANDARD, URGENT
}
