package com.optiplant.inventory.iam.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A persisted audit entry, read back for {@code GET /api/audit} (RF-SEG-04).
 *
 * <p>Carries {@code external_id} for itself, the acting user and the branch — never an
 * internal numeric id, mirroring every other domain model in this module. {@code
 * actorUserExternalId}/{@code branchExternalId} are nullable because their columns are
 * {@code ON DELETE SET NULL} ({@code 01-init-schema.sql:426-427}).
 */
public record AuditRecord(UUID externalId, UUID actorUserExternalId, UUID branchExternalId, String action,
		String entityName, String entityId, String payloadBefore, String payloadAfter, String ipAddress,
		Instant createdAt) {
}
