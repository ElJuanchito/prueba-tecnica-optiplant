package com.optiplant.inventory.notifications.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.notifications.application.port.in.ManageAlertsUseCase.RaiseAlertCommand;
import com.optiplant.inventory.notifications.application.port.out.AlertRepositoryPort;
import com.optiplant.inventory.notifications.domain.exception.AlertAlreadyResolvedException;
import com.optiplant.inventory.notifications.domain.model.Alert;
import com.optiplant.inventory.notifications.domain.model.AlertDedupKey;
import com.optiplant.inventory.shared.alert.AlertSeverity;
import com.optiplant.inventory.shared.alert.AlertType;
import com.optiplant.inventory.shared.audit.AuditEntryCommand;
import com.optiplant.inventory.shared.audit.AuditWritePort;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link AlertService} using hand-written in-memory fakes (no Mockito on
 * classpath, mirroring {@code catalog}'s service tests). Covers R-21 (dedup hit writes nothing)
 * and R-23 (double resolve refused).
 */
class AlertServiceTest {

	private FakeAlertRepositoryPort alertRepository;
	private FakeAuditWritePort auditWritePort;
	private AlertService service;
	private AuthenticatedPrincipal manager;

	@BeforeEach
	void setUp() {
		alertRepository = new FakeAlertRepositoryPort();
		auditWritePort = new FakeAuditWritePort();
		service = new AlertService(alertRepository, auditWritePort);
		manager = new AuthenticatedPrincipal(UUID.randomUUID(), "manager", Role.BRANCH_MANAGER, UUID.randomUUID());
	}

	@Test
	void raiseCreatesANewAlertWhenNoUnresolvedOneExists() {
		RaiseAlertCommand command = new RaiseAlertCommand(UUID.randomUUID(), AlertType.STOCK_MINIMUM,
				AlertSeverity.WARNING, "product-1", "low stock");

		Alert created = service.raise(command);

		assertThat(created.alertType()).isEqualTo(AlertType.STOCK_MINIMUM);
		assertThat(alertRepository.created).hasSize(1);
	}

	@Test
	void raiseIsIdempotentByDedupKeyAndWritesNothingOnASecondCall() {
		UUID branch = UUID.randomUUID();
		RaiseAlertCommand command = new RaiseAlertCommand(branch, AlertType.STOCK_MINIMUM, AlertSeverity.WARNING,
				"product-1", "low stock");

		Alert first = service.raise(command);
		Alert second = service.raise(command);

		assertThat(alertRepository.created).hasSize(1);
		assertThat(second.externalId()).isEqualTo(first.externalId());
	}

	@Test
	void resolveSucceedsAndWritesAnAuditEntry() {
		Alert alert = alertRepository.seed(unresolvedAlert(manager.branchId()));

		Alert resolved = service.resolve(manager, alert.externalId());

		assertThat(resolved.resolved()).isTrue();
		assertThat(resolved.resolvedByUserExternalId()).isEqualTo(manager.userId());
		assertThat(auditWritePort.recorded).extracting(AuditEntryCommand::action).containsExactly("RESOLVE_ALERT");
	}

	@Test
	void aSecondResolveIsRefused() {
		Alert alert = alertRepository.seed(unresolvedAlert(manager.branchId()));

		service.resolve(manager, alert.externalId());

		assertThatThrownBy(() -> service.resolve(manager, alert.externalId()))
				.isInstanceOf(AlertAlreadyResolvedException.class);
		assertThat(auditWritePort.recorded).hasSize(1);
	}

	private static Alert unresolvedAlert(UUID branchExternalId) {
		return new Alert(UUID.randomUUID(), branchExternalId, AlertType.STOCK_MINIMUM, AlertSeverity.WARNING,
				"STOCK_MINIMUM:product-1", "low stock", false, null, null, Instant.now());
	}

	private static final class FakeAlertRepositoryPort implements AlertRepositoryPort {

		private final Map<UUID, Alert> byExternalId = new HashMap<>();
		private final List<Alert> created = new ArrayList<>();

		Alert seed(Alert alert) {
			byExternalId.put(alert.externalId(), alert);
			return alert;
		}

		@Override
		public Optional<Alert> findUnresolvedByDedupKey(AlertDedupKey key) {
			return byExternalId.values().stream()
					.filter(a -> !a.resolved() && a.branchExternalId().equals(key.branchExternalId())
							&& a.alertType() == key.alertType() && a.title().equals(key.title()))
					.findFirst();
		}

		@Override
		public Alert create(NewAlert newAlert) {
			Alert alert = new Alert(UUID.randomUUID(), newAlert.branchExternalId(), newAlert.alertType(),
					newAlert.severity(), newAlert.title(), newAlert.message(), false, null, null, Instant.now());
			byExternalId.put(alert.externalId(), alert);
			created.add(alert);
			return alert;
		}

		@Override
		public Alert markResolved(UUID externalId, UUID actorUserExternalId, Instant resolvedAt) {
			Alert resolved = byExternalId.get(externalId).resolve(actorUserExternalId, resolvedAt);
			byExternalId.put(externalId, resolved);
			return resolved;
		}

		@Override
		public Optional<Alert> findByExternalIdVisibleTo(UUID externalId, UUID branchExternalId) {
			Alert alert = byExternalId.get(externalId);
			if (alert == null) {
				return Optional.empty();
			}
			if (branchExternalId != null && !alert.branchExternalId().equals(branchExternalId)) {
				return Optional.empty();
			}
			return Optional.of(alert);
		}

		@Override
		public AlertPage list(AlertFilter filter) {
			throw new UnsupportedOperationException("not exercised by these unit tests");
		}

		@Override
		public void lockAlertScope(AlertDedupKey key) {
			// no-op — a single-threaded fake needs no advisory lock
		}
	}

	private static final class FakeAuditWritePort implements AuditWritePort {

		private final List<AuditEntryCommand> recorded = new ArrayList<>();

		@Override
		public void record(AuditEntryCommand command) {
			recorded.add(command);
		}
	}
}
