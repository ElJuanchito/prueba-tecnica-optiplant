package com.optiplant.inventory.shared.alert;

/**
 * The four {@code system_alerts.alert_type} literals ({@code 01-init-schema.sql:419-421}). Only
 * {@code STOCK_MINIMUM} is produced in this change (contract §1); the other three are reserved so
 * {@code transfers} and {@code logistics} can reuse this same transport with no new agreement
 * (contract P-09).
 */
public enum AlertType {
	STOCK_MINIMUM,
	LOGISTIC_DELAY,
	TRANSFER_DISCREPANCY,
	PRICE_CHANGE
}
