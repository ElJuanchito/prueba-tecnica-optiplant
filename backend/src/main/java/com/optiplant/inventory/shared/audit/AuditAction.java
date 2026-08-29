package com.optiplant.inventory.shared.audit;

/**
 * The generic CRUD-verb vocabulary an admin service emits when writing an audit entry:
 * {@code iam}'s create/edit/disable (slices 5a/5b) plus the enable/delete that
 * {@code catalog} adds (category and product re-enable, unit deletion).
 * {@code audit_logs.action} ({@code 01-init-schema.sql:428}) is a plain {@code
 * VARCHAR(50)} with no {@code CHECK} constraint — the schema's own comment lists
 * examples from other, not-yet-built modules ({@code 'CREATE_SALE'}, {@code
 * 'DISPATCH_TRANSFER'}, {@code 'ADJUST_STOCK'}), so {@link AuditEntryCommand#action()}
 * and every query filter stay a plain {@code String} for those module-specific names.
 * This enum only gives call sites a typed, typo-proof source for the generic verbs (its
 * {@code name()} is what actually gets written/matched). No {@code switch} anywhere
 * dispatches on it, so adding a constant breaks nothing.
 */
public enum AuditAction {
	CREATE, UPDATE, DISABLE, ENABLE, DELETE
}
