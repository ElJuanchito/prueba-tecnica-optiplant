package com.optiplant.inventory.notifications.domain.model;

import com.optiplant.inventory.shared.alert.AlertType;
import java.util.UUID;

/**
 * The F-1 deduplication key: {@code system_alerts} has no {@code product_id} column and no
 * uniqueness constraint (contract §2.5, F-1), so an unresolved alert for a persisting condition
 * is deduplicated on {@code (branch_id, alert_type, is_resolved = false)} plus a deterministic
 * subject token encoded into {@code title}.
 *
 * <p>{@link #title()} is the <strong>only</strong> writer of the F-1 token — the only reader is
 * the dedup query — so the token has exactly one author.
 */
public record AlertDedupKey(UUID branchExternalId, AlertType alertType, String subjectToken) {

	private static final int MAX_TITLE_LENGTH = 150;

	/** {@code "ALERT_TYPE:<subjectToken>"} — fits {@code system_alerts.title VARCHAR(150)}. */
	public String title() {
		String candidate = alertType.name() + ":" + subjectToken;
		if (candidate.length() > MAX_TITLE_LENGTH) {
			throw new IllegalArgumentException(
					"dedup title '" + candidate + "' exceeds " + MAX_TITLE_LENGTH + " characters");
		}
		return candidate;
	}
}
