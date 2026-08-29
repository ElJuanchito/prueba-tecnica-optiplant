package com.optiplant.inventory.notifications.domain.exception;

import java.util.UUID;

/**
 * Thrown when an alert lookup by {@code external_id} names no alert visible to the caller — an
 * unknown alert or one belonging to another branch both raise this (R-19, R-24: "responds as if
 * it did not exist"). The web layer maps this to {@code 404 alert_not_found}.
 */
public class AlertNotFoundException extends RuntimeException {

	public AlertNotFoundException(UUID externalId) {
		super("No alert found for external id " + externalId);
	}
}
