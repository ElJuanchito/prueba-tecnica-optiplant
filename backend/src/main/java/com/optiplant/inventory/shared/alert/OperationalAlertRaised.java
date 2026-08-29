package com.optiplant.inventory.shared.alert;

import java.time.Instant;
import java.util.UUID;

/**
 * The producer-agnostic alert transport (design §2.2, D-1). P-08 names the domain event
 * {@code StockThresholdBreached}; P-09 requires one event type {@code transfers} and
 * {@code logistics} can reuse for {@code TRANSFER_DISCREPANCY} / {@code LOGISTIC_DELAY} with no
 * new agreement. A producer-specific name cannot be reused, so the <em>transport</em>
 * generalizes here while the <em>domain concept</em> keeps P-08's name inside
 * {@code inventory.domain.model.StockThresholdBreach}.
 *
 * <p>{@code title} is deliberately absent: {@code notifications} derives it from
 * {@code alertType + subjectToken} (F-1), so the dedup token has exactly one author.
 *
 * @param branchExternalId the branch the alert concerns
 * @param alertType         one of the four {@link AlertType} values
 * @param severity          {@link AlertSeverity#CRITICAL} or {@link AlertSeverity#WARNING} for
 *                          {@code STOCK_MINIMUM} (R-20)
 * @param subjectToken      the F-1 dedup subject — the product {@code external_id} for
 *                          {@code STOCK_MINIMUM}
 * @param message           human-readable detail, rendered by the producer
 * @param occurredAt        when the underlying condition was detected
 */
public record OperationalAlertRaised(UUID branchExternalId, AlertType alertType, AlertSeverity severity,
		String subjectToken, String message, Instant occurredAt) {
}
