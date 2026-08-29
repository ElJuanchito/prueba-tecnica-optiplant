package com.optiplant.inventory.notifications.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.optiplant.inventory.notifications.application.port.in.ManageAlertsUseCase;
import com.optiplant.inventory.notifications.application.port.out.AlertRepositoryPort;
import com.optiplant.inventory.notifications.application.port.out.AlertRepositoryPort.AlertFilter;
import com.optiplant.inventory.notifications.application.port.out.AlertRepositoryPort.AlertPage;
import com.optiplant.inventory.notifications.application.port.out.AlertRepositoryPort.NewAlert;
import com.optiplant.inventory.notifications.domain.exception.AlertNotFoundException;
import com.optiplant.inventory.notifications.domain.model.Alert;
import com.optiplant.inventory.notifications.domain.model.AlertDedupKey;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates alert listing, resolution (CU-ALE-02) and raising (CU-ALE-01). {@link #resolve}
 * writes a {@code RESOLVE_ALERT} audit entry (design §7) in the same transaction as the state
 * change (R-23); {@link #raise} has no acting user to audit against — it is invoked by the
 * {@code AFTER_COMMIT} listener, not by a request.
 *
 * <p>{@code @Service} restored in S2 (task 2.14), alongside {@code AlertPersistenceAdapter}: S1
 * shipped this class unannotated because {@code AlertRepositoryPort} had no adapter yet.
 */
@Service
public class AlertService implements ManageAlertsUseCase {

	private final AlertRepositoryPort alertRepository;
	private final AuditWritePort auditWritePort;

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	public AlertService(AlertRepositoryPort alertRepository, AuditWritePort auditWritePort) {
		this.alertRepository = alertRepository;
		this.auditWritePort = auditWritePort;
	}

	@Override
	@Transactional(readOnly = true)
	public AlertPage list(AuthenticatedPrincipal actor, AlertQuery query) {
		UUID branchScope = actor.isCorporate() ? null : actor.branchId();
		return alertRepository.list(new AlertFilter(branchScope, query.resolved(), query.alertType(),
				query.severity(), query.page(), query.size()));
	}

	@Override
	@Transactional
	public Alert resolve(AuthenticatedPrincipal actor, UUID externalId) {
		UUID branchScope = actor.isCorporate() ? null : actor.branchId();
		Alert alert = alertRepository.findByExternalIdVisibleTo(externalId, branchScope)
				.orElseThrow(() -> new AlertNotFoundException(externalId));

		Alert resolved = alert.resolve(actor.userId(), Instant.now());
		Alert saved = alertRepository.markResolved(resolved.externalId(), resolved.resolvedByUserExternalId(),
				resolved.resolvedAt());

		auditWritePort.record(new AuditEntryCommand(actor.userId(), saved.branchExternalId(), "RESOLVE_ALERT",
				"system_alerts", externalId.toString(), serializeAlert(alert), serializeAlert(saved), null));

		return saved;
	}

	@Override
	@Transactional
	public Alert raise(RaiseAlertCommand command) {
		AlertDedupKey key = new AlertDedupKey(command.branchExternalId(), command.alertType(),
				command.subjectToken());
		alertRepository.lockAlertScope(key);

		Optional<Alert> existing = alertRepository.findUnresolvedByDedupKey(key);
		if (existing.isPresent()) {
			return existing.get(); // R-21: dedup hit — nothing written
		}

		return alertRepository.create(new NewAlert(command.branchExternalId(), command.alertType(),
				command.severity(), key.title(), command.message()));
	}

	private String serializeAlert(Alert alert) {
		try {
			return OBJECT_MAPPER.writeValueAsString(
					new AlertAuditPayload(alert.alertType().name(), alert.severity().name(), alert.title(),
							alert.resolved()));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize audit payload", e);
		}
	}

	private record AlertAuditPayload(String alertType, String severity, String title, boolean resolved) {
	}
}
