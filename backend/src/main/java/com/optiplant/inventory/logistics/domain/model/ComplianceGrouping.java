package com.optiplant.inventory.logistics.domain.model;

/**
 * The compliance report's {@code groupBy} query parameter (contract §6). {@code BRANCH} groups
 * by the <strong>destination</strong> branch (D-6).
 */
public enum ComplianceGrouping {
	ROUTE, BRANCH
}
