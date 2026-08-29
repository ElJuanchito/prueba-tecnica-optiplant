package com.optiplant.inventory.notifications.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.notifications.application.port.out.AlertRepositoryPort;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Test-source-only configuration imported solely by {@code StockAlertIT} (tasks.md 3.4). Replaces
 * the {@link AlertRepositoryPort} bean the application would otherwise wire with a decorator that
 * can be told to fail on demand — see {@link FailureTogglingAlertRepositoryPort} and
 * {@link AlertFailureToggle}. Importing this class gives the test its own cached Spring context
 * (and its own Testcontainers PostgreSQL instance), fully isolated from every other {@code *IT}
 * that uses the plain {@code TestcontainersConfiguration}.
 */
@TestConfiguration(proxyBeanMethods = false)
public class AlertFailureFixtureConfiguration {

	@Bean
	public AlertFailureToggle alertFailureToggle() {
		return new AlertFailureToggle();
	}

	@Bean
	@Primary
	AlertRepositoryPort failureTogglingAlertRepository(
			@Qualifier("alertPersistenceAdapter") AlertRepositoryPort delegate, AlertFailureToggle toggle) {
		return new FailureTogglingAlertRepositoryPort(delegate, toggle);
	}
}
