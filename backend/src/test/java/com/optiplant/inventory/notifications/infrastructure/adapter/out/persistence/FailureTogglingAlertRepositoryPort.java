package com.optiplant.inventory.notifications.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.notifications.application.port.out.AlertRepositoryPort;
import com.optiplant.inventory.notifications.domain.model.Alert;
import com.optiplant.inventory.notifications.domain.model.AlertDedupKey;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Test-source-only decorator around the real {@link AlertPersistenceAdapter}: delegates every call
 * unchanged except {@link #create}, which throws while {@link AlertFailureToggle#isEnabled()} is
 * {@code true}. Used solely by {@code StockAlertIT} to prove P-10 — the {@code
 * OperationalAlertListener}'s failure must not roll back the movement that triggered it, and must
 * not leak past the listener's own {@code try/catch}. Confined to {@code src/test}.
 */
class FailureTogglingAlertRepositoryPort implements AlertRepositoryPort {

	private final AlertRepositoryPort delegate;
	private final AlertFailureToggle toggle;

	FailureTogglingAlertRepositoryPort(AlertRepositoryPort delegate, AlertFailureToggle toggle) {
		this.delegate = delegate;
		this.toggle = toggle;
	}

	@Override
	public Optional<Alert> findUnresolvedByDedupKey(AlertDedupKey key) {
		return delegate.findUnresolvedByDedupKey(key);
	}

	@Override
	public Alert create(NewAlert newAlert) {
		if (toggle.isEnabled()) {
			throw new AlertFixtureFailure("Deliberate StockAlertIT failure inside AlertRepositoryPort.create");
		}
		return delegate.create(newAlert);
	}

	@Override
	public Alert markResolved(UUID externalId, UUID actorUserExternalId, Instant resolvedAt) {
		return delegate.markResolved(externalId, actorUserExternalId, resolvedAt);
	}

	@Override
	public Optional<Alert> findByExternalIdVisibleTo(UUID externalId, UUID branchExternalId) {
		return delegate.findByExternalIdVisibleTo(externalId, branchExternalId);
	}

	@Override
	public AlertPage list(AlertFilter filter) {
		return delegate.list(filter);
	}

	@Override
	public void lockAlertScope(AlertDedupKey key) {
		delegate.lockAlertScope(key);
	}

	/** Deliberately unchecked — {@code OperationalAlertListener} catches {@code RuntimeException}
	 *  and only logs, per P-10. */
	static class AlertFixtureFailure extends RuntimeException {
		AlertFixtureFailure(String message) {
			super(message);
		}
	}
}
