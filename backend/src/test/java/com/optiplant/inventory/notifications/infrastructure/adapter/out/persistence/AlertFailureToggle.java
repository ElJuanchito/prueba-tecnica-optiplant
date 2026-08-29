package com.optiplant.inventory.notifications.infrastructure.adapter.out.persistence;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Test-source-only switch letting {@code StockAlertIT} force {@link AlertPersistenceAdapter#create}
 * to fail on demand, simulating P-10's "the listener's own transaction fails" without touching any
 * production code. Confined to {@code src/test}.
 */
public class AlertFailureToggle {

	private final AtomicBoolean shouldFail = new AtomicBoolean(false);

	public void enable() {
		shouldFail.set(true);
	}

	public void disable() {
		shouldFail.set(false);
	}

	public boolean isEnabled() {
		return shouldFail.get();
	}
}
