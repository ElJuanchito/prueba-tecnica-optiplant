package com.optiplant.inventory.iam.application.port.out;

import com.optiplant.inventory.iam.domain.model.AuditRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Filtered, paginated read access to persisted audit entries (RF-SEG-04, RNF-PER-04).
 * This port applies exactly the filter it is given, with no role awareness of its
 * own — role-scoping (ADMIN sees every branch, BRANCH_MANAGER is forced to their own)
 * is resolved by {@code AuditQueryService} before calling {@link #query}.
 */
public interface AuditQueryPort {

	AuditPage query(AuditFilter filter);

	/**
	 * Every field except {@code page}/{@code size} is optional (null = unfiltered).
	 * {@code actorUserExternalId}/{@code branchExternalId} carry {@code external_id}
	 * only, never a numeric id.
	 */
	record AuditFilter(UUID actorUserExternalId, UUID branchExternalId, String entityName, String action,
			Instant from, Instant to, int page, int size) {
	}

	record AuditPage(List<AuditRecord> content, long totalElements, int page, int size) {
	}
}
