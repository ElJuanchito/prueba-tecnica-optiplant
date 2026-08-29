package com.optiplant.inventory.notifications.application.port.out;

import com.optiplant.inventory.notifications.domain.model.Alert;
import com.optiplant.inventory.notifications.domain.model.AlertDedupKey;
import com.optiplant.inventory.shared.alert.AlertSeverity;
import com.optiplant.inventory.shared.alert.AlertType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Secondary port for {@code system_alerts} persistence (design §5.2). */
public interface AlertRepositoryPort {

	Optional<Alert> findUnresolvedByDedupKey(AlertDedupKey key);

	Alert create(NewAlert newAlert);

	Alert markResolved(UUID externalId, UUID actorUserExternalId, Instant resolvedAt);

	/**
	 * @param branchExternalId {@code null} means unrestricted — a corporate {@code ADMIN} reading
	 *                         any branch (contract §5)
	 * @return empty for an unknown alert <strong>or</strong> one of another branch, so the caller
	 *         maps both to {@code 404} and never {@code 403} (R-19, R-24)
	 */
	Optional<Alert> findByExternalIdVisibleTo(UUID externalId, UUID branchExternalId);

	AlertPage list(AlertFilter filter);

	/**
	 * PostgreSQL transaction advisory lock on {@code key}, serializing concurrent alerts for the
	 * same subject without a schema change (design §6.3, DT-09). MUST be the first statement of
	 * the listener's transaction.
	 */
	void lockAlertScope(AlertDedupKey key);

	record NewAlert(UUID branchExternalId, AlertType alertType, AlertSeverity severity, String title,
			String message) {
	}

	/**
	 * @param branchExternalId {@code null} means unrestricted — a corporate {@code ADMIN} reading
	 *                         any branch (contract §5)
	 */
	record AlertFilter(UUID branchExternalId, Boolean resolved, AlertType alertType, AlertSeverity severity,
			int page, int size) {
	}

	/** Ordered by severity then recency (contract §6). */
	record AlertPage(List<Alert> content, long totalElements, int page, int size) {
	}
}
