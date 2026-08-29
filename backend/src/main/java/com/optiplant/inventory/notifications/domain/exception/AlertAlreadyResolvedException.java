package com.optiplant.inventory.notifications.domain.exception;

import java.util.UUID;

/**
 * Thrown by {@link com.optiplant.inventory.notifications.domain.model.Alert#resolve} when the
 * alert is already resolved (R-23). No auto-resolution path exists (R-22, PA-03), so this is the
 * only way an already-resolved alert is touched again. The web layer maps this to
 * {@code 409 alert_already_resolved}.
 */
public class AlertAlreadyResolvedException extends RuntimeException {

	public AlertAlreadyResolvedException(UUID externalId) {
		super("Alert " + externalId + " is already resolved");
	}
}
