package com.optiplant.inventory.iam.application.service;

import com.optiplant.inventory.iam.application.port.in.QueryAuditLogUseCase;
import com.optiplant.inventory.iam.application.port.out.AuditQueryPort;
import com.optiplant.inventory.iam.application.port.out.AuditQueryPort.AuditFilter;
import com.optiplant.inventory.iam.application.port.out.AuditQueryPort.AuditPage;
import com.optiplant.inventory.shared.security.AuthenticatedPrincipal;
import com.optiplant.inventory.shared.security.Role;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Enforces the audit-log role-scoping requirement (RF-SEG-04, {@code
 * docs/casos_de_uso.md:91}): {@code ADMIN} passes any submitted branch filter through
 * unchanged (including none, meaning every branch); {@code BRANCH_MANAGER} is forced
 * to their own session branch regardless of what they submit. {@code OPERATOR} never
 * reaches this service — {@code SecurityConfig}'s {@code /api/audit/**} matcher
 * rejects it with {@code 403} before dispatch, the same HTTP-layer boundary {@code
 * BranchAccessPolicy} relies on for mutations.
 */
@Service
public class AuditQueryService implements QueryAuditLogUseCase {

	private final AuditQueryPort auditQueryPort;

	public AuditQueryService(AuditQueryPort auditQueryPort) {
		this.auditQueryPort = auditQueryPort;
	}

	@Override
	public AuditPage query(AuthenticatedPrincipal principal, AuditQuery query) {
		UUID effectiveBranch = principal.role() == Role.BRANCH_MANAGER ? principal.branchId()
				: query.branchExternalId();

		return auditQueryPort.query(new AuditFilter(query.actorUserExternalId(), effectiveBranch, query.entityName(),
				query.action(), query.from(), query.to(), query.page(), query.size()));
	}
}
