package com.optiplant.inventory.transfers.domain.model;

/**
 * The three priority levels RF-TRA-01 / HU-TRA-01 require (F-1). {@code transfers} has no
 * priority column, so this value is persisted as the first line of {@code transfers.notes}
 * (design §3.5) rather than as its own column.
 */
public enum TransferPriority {
	LOW, STANDARD, URGENT
}
