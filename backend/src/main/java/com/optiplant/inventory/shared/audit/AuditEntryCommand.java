package com.optiplant.inventory.shared.audit;

import java.util.UUID;

/**
 * One mutation event to be written to {@code audit_logs}, per RF-SEG-04. Only
 * {@code external_id}-shaped identifiers cross this port — {@code actorUserId}/{@code
 * branchId} are {@code users.external_id}/{@code branches.external_id}, never a
 * numeric id — the implementation resolves them to the internal {@code BIGINT}
 * foreign keys itself (design decision: "the principal carries UUIDs only").
 *
 * <p>{@code action} stays a plain {@code String}, not the {@code iam}-local {@link
 * AuditAction} enum: {@code audit_logs.action} has no {@code CHECK} constraint, and
 * every future module (sales, transfers, inventory adjustments, ...) must be free to
 * write its own action name without this shared type growing a case for each one.
 *
 * @param branchId      nullable — {@code null} when the acting/affected context has no
 *                       branch (e.g. a corporate {@code ADMIN} action with no
 *                       branch-scoped target)
 * @param payloadBefore  JSON text, or {@code null} on creation (no prior state)
 * @param payloadAfter   JSON text, or {@code null} on a pure deletion/disable with no
 *                       new state to record beyond the entity id
 */
public record AuditEntryCommand(UUID actorUserId, UUID branchId, String action, String entityName, String entityId,
		String payloadBefore, String payloadAfter, String ipAddress) {
}
