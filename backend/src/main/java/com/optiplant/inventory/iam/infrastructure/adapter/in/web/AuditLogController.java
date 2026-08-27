package com.optiplant.inventory.iam.infrastructure.adapter.in.web;

import com.optiplant.inventory.iam.application.port.in.QueryAuditLogUseCase;
import com.optiplant.inventory.iam.application.port.in.QueryAuditLogUseCase.AuditQuery;
import com.optiplant.inventory.iam.application.port.out.AuditQueryPort.AuditPage;
import com.optiplant.inventory.iam.domain.model.AuditRecord;
import com.optiplant.inventory.shared.security.PrincipalAccessor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /api/audit} — filtered, paginated read access to the audit log
 * (RF-SEG-04). Gated to {@code ADMIN}/{@code BRANCH_MANAGER} by {@code
 * SecurityConfig}'s {@code /api/audit/**} matcher (slice 3); {@code OPERATOR} never
 * reaches this controller, rejected by the security filter chain with {@code 403}
 * before dispatch (audit-log "OPERATOR is denied"). No update/delete mapping exists
 * here or anywhere else — audit entries are immutable and retained (audit-log "Audit
 * entries are immutable and retained").
 */
@RestController
@RequestMapping("/api/audit")
public class AuditLogController {

	private static final int DEFAULT_PAGE_SIZE = 20;
	private static final int MAX_PAGE_SIZE = 100;

	private final QueryAuditLogUseCase queryAuditLogUseCase;
	private final PrincipalAccessor principalAccessor;

	public AuditLogController(QueryAuditLogUseCase queryAuditLogUseCase, PrincipalAccessor principalAccessor) {
		this.queryAuditLogUseCase = queryAuditLogUseCase;
		this.principalAccessor = principalAccessor;
	}

	@GetMapping
	public AuditPageResponse query(@RequestParam(required = false) UUID userId,
			@RequestParam(required = false) UUID branchId, @RequestParam(required = false) String entityName,
			@RequestParam(required = false) String action, @RequestParam(required = false) Instant from,
			@RequestParam(required = false) Instant to, @RequestParam(defaultValue = "0") int page,
			@RequestParam(required = false) Integer size) {

		int pageSize = size == null ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
		AuditPage result = queryAuditLogUseCase.query(principalAccessor.require(),
				new AuditQuery(userId, branchId, entityName, action, from, to, page, pageSize));

		List<AuditEntryResponse> content = result.content().stream().map(AuditLogController::toResponse).toList();
		return new AuditPageResponse(content, result.totalElements(), result.page(), result.size());
	}

	private static AuditEntryResponse toResponse(AuditRecord record) {
		return new AuditEntryResponse(record.externalId(), record.actorUserExternalId(), record.branchExternalId(),
				record.action(), record.entityName(), record.entityId(), record.payloadBefore(),
				record.payloadAfter(), record.ipAddress(), record.createdAt());
	}

	public record AuditEntryResponse(UUID externalId, UUID actorUserId, UUID branchId, String action,
			String entityName, String entityId, String payloadBefore, String payloadAfter, String ipAddress,
			Instant createdAt) {
	}

	public record AuditPageResponse(List<AuditEntryResponse> content, long totalElements, int page, int size) {
	}
}
