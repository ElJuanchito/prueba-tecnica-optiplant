package com.optiplant.inventory.iam.application.port.in;

import com.optiplant.inventory.iam.application.port.out.AuditQueryPort.AuditPage;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import java.time.Instant;
import java.util.UUID;

/**
 * Queries the audit log with role-scoping enforced (audit-log "Audit query is
 * filtered and role-scoped"): {@code ADMIN} sees every branch; {@code BRANCH_MANAGER}
 * is forced to their own regardless of any branch filter submitted. {@code OPERATOR}
 * never reaches this use case — {@code SecurityConfig}'s {@code /api/audit/**}
 * matcher rejects the request with {@code 403} before dispatch (slice 3).
 */
public interface QueryAuditLogUseCase {

	AuditPage query(AuthenticatedPrincipal principal, AuditQuery query);

	record AuditQuery(UUID actorUserExternalId, UUID branchExternalId, String entityName, String action, Instant from,
			Instant to, int page, int size) {
	}
}
