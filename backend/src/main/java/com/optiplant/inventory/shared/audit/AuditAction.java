package com.optiplant.inventory.shared.audit;

/**
 * The mutation-kind vocabulary {@code iam}'s own admin services emit when writing an
 * audit entry (create/edit/disable, per slices 5a/5b). {@code audit_logs.action}
 * ({@code 01-init-schema.sql:428}) is a plain {@code VARCHAR(50)} with no {@code CHECK}
 * constraint — the schema's own comment lists examples from other, not-yet-built
 * modules ({@code 'CREATE_SALE'}, {@code 'DISPATCH_TRANSFER'}, {@code 'ADJUST_STOCK'}),
 * so {@link AuditEntryCommand#action()} and every query filter stay a plain {@code
 * String}: this enum only gives {@code iam}'s own call sites a typed, typo-proof
 * source for its three action names (its {@code name()} is what actually gets
 * written/matched).
 */
public enum AuditAction {
	CREATE, UPDATE, DISABLE
}
