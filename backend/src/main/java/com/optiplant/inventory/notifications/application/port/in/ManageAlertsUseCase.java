package com.optiplant.inventory.notifications.application.port.in;

import com.optiplant.inventory.notifications.application.port.out.AlertRepositoryPort.AlertPage;
import com.optiplant.inventory.notifications.domain.model.Alert;
import com.optiplant.inventory.shared.alert.AlertSeverity;
import com.optiplant.inventory.shared.alert.AlertType;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.util.UUID;

/**
 * Manage operational alerts (CU-ALE-01, CU-ALE-02, design §5.1). {@link #list} and
 * {@link #resolve} take an actor and are scoped to the caller's own branch — a corporate
 * {@code ADMIN} reads any branch (contract §5). {@link #raise} takes no actor: it is invoked by
 * the {@code AFTER_COMMIT} listener, not by a request.
 */
public interface ManageAlertsUseCase {

	AlertPage list(AuthenticatedPrincipal actor, AlertQuery query);

	/**
	 * @throws com.optiplant.inventory.notifications.domain.exception.AlertNotFoundException
	 *     when {@code externalId} names no alert visible to {@code actor} (R-19, R-24)
	 * @throws com.optiplant.inventory.notifications.domain.exception.AlertAlreadyResolvedException
	 *     when the alert is already resolved (R-23)
	 */
	Alert resolve(AuthenticatedPrincipal actor, UUID externalId);

	/**
	 * Idempotent by the F-1 dedup key (R-21): a second call for the same still-unresolved subject
	 * writes nothing and returns the existing alert.
	 */
	Alert raise(RaiseAlertCommand command);

	record AlertQuery(Boolean resolved, AlertType alertType, AlertSeverity severity, int page, int size) {
	}

	record RaiseAlertCommand(UUID branchExternalId, AlertType alertType, AlertSeverity severity, String subjectToken,
			String message) {
	}
}
