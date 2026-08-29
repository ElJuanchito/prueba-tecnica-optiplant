package com.optiplant.inventory.notifications.infrastructure.adapter.in.web;

import com.optiplant.inventory.notifications.application.port.in.ManageAlertsUseCase;
import com.optiplant.inventory.notifications.application.port.in.ManageAlertsUseCase.AlertQuery;
import com.optiplant.inventory.notifications.application.port.out.AlertRepositoryPort.AlertPage;
import com.optiplant.inventory.notifications.domain.model.Alert;
import com.optiplant.inventory.shared.alert.AlertSeverity;
import com.optiplant.inventory.shared.alert.AlertType;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.PrincipalAccessor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code /api/notifications/alerts} — the two endpoints of contract §6 (CU-ALE-02). Scoped to
 * the caller's own branch; a corporate {@code ADMIN} reads any branch (contract §5). Page size
 * is clamped server-side; a size above the cap is refused, not silently clamped (R-00,
 * RNF-PER-04).
 */
@RestController
@RequestMapping("/api/notifications/alerts")
public class AlertController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final ManageAlertsUseCase manageAlertsUseCase;
	private final PrincipalAccessor principalAccessor;

	public AlertController(ManageAlertsUseCase manageAlertsUseCase, PrincipalAccessor principalAccessor) {
		this.manageAlertsUseCase = manageAlertsUseCase;
		this.principalAccessor = principalAccessor;
	}

	@GetMapping
	public AlertPageResponse list(@RequestParam(required = false) Boolean resolved,
			@RequestParam(required = false) AlertType alertType, @RequestParam(required = false) AlertSeverity severity,
			@RequestParam(defaultValue = "0") int page, @RequestParam(required = false) Integer size) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		AlertPage result = manageAlertsUseCase.list(actor,
				new AlertQuery(resolved, alertType, severity, Math.max(page, 0), resolveSize(size)));
		List<AlertResponse> content = result.content().stream().map(AlertController::toResponse).toList();
		return new AlertPageResponse(content, result.totalElements(), result.page(), result.size());
	}

	@PatchMapping("/{externalId}/resolve")
	public AlertResponse resolve(@PathVariable UUID externalId) {
		AuthenticatedPrincipal actor = principalAccessor.require();
		return toResponse(manageAlertsUseCase.resolve(actor, externalId));
	}

	private static int resolveSize(Integer size) {
		if (size == null) {
			return DEFAULT_PAGE_SIZE;
		}
		if (size < 1 || size > MAX_PAGE_SIZE) {
			throw new IllegalArgumentException("size must be between 1 and " + MAX_PAGE_SIZE);
		}
		return size;
	}

	private static AlertResponse toResponse(Alert alert) {
		return new AlertResponse(alert.externalId(), alert.alertType(), alert.severity(), alert.title(),
				alert.message(), alert.resolved(), alert.resolvedAt(), alert.createdAt());
	}

	public record AlertResponse(UUID externalId, AlertType alertType, AlertSeverity severity, String title,
			String message, boolean isResolved, Instant resolvedAt, Instant createdAt) {
	}

	public record AlertPageResponse(List<AlertResponse> content, long totalElements, int page, int size) {
	}
}
