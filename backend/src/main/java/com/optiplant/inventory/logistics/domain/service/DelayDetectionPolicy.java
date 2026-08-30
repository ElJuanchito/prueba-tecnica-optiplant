package com.optiplant.inventory.logistics.domain.service;

import java.time.Instant;

/**
 * One predicate feeding both the monitor's {@code isDelayed} flag (R-25) and the scheduled
 * detector (R-28), so the two can never disagree (design §4). {@code status} stays a plain
 * {@code String} — {@code logistics} reads it through a native projection (P-12) and declares no
 * dependency on {@code transfers}' own {@code TransferStatus} enum.
 */
public final class DelayDetectionPolicy {

	/** The {@code transfers.status} literal this policy checks against — {@code transfers}' own {@code IN_TRANSIT}. */
	public static final String IN_TRANSIT_STATUS = "IN_TRANSIT";

	private DelayDetectionPolicy() {
	}

	public static boolean isDelayed(String status, Instant estimatedArrivalAt, Instant now) {
		return IN_TRANSIT_STATUS.equals(status) && estimatedArrivalAt != null && now.isAfter(estimatedArrivalAt);
	}
}
