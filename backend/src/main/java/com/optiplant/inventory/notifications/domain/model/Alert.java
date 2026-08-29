package com.optiplant.inventory.notifications.domain.model;

import com.optiplant.inventory.notifications.domain.exception.AlertAlreadyResolvedException;
import com.optiplant.inventory.shared.alert.AlertSeverity;
import com.optiplant.inventory.shared.alert.AlertType;
import java.time.Instant;
import java.util.UUID;

/**
 * Domain representation of one persisted {@code system_alerts} row (design §4). Immutable — a
 * mutation is a {@code with*}-shaped copy via {@link #resolve}.
 */
public record Alert(UUID externalId, UUID branchExternalId, AlertType alertType, AlertSeverity severity,
		String title, String message, boolean resolved, Instant resolvedAt, UUID resolvedByUserExternalId,
		Instant createdAt) {

	/**
	 * Resolution is an explicit human act — no automatic resolution path exists even when stock
	 * recovers above the threshold (R-22, PA-03).
	 *
	 * @throws AlertAlreadyResolvedException when this alert is already resolved (R-23)
	 */
	public Alert resolve(UUID actorUserExternalId, Instant at) {
		if (resolved) {
			throw new AlertAlreadyResolvedException(externalId);
		}
		return new Alert(externalId, branchExternalId, alertType, severity, title, message, true, at,
				actorUserExternalId, createdAt);
	}
}
