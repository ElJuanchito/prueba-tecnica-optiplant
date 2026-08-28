package com.optiplant.inventory.iam.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Maps {@code audit_logs} (01-init-schema.sql:423-435). {@code user_id}/{@code
 * branch_id} stay plain, nullable {@code Long} FKs, resolved from an {@code
 * AuthenticatedPrincipal}'s external ids by {@link AuditWriteAdapter} — the same
 * UUID→BIGINT pattern {@link RefreshTokenPersistenceAdapter} already uses.
 *
 * <p>{@code payload_before}/{@code payload_after} are {@code jsonb} columns; {@link
 * JdbcTypeCode}({@link SqlTypes#JSON}) on a plain {@code String} property passes the
 * caller's already-serialized JSON text through unchanged (no re-serialization, no
 * extra JSON library in {@code shared} — design's Open Question on this exact point),
 * verified present in {@code hibernate-core-7.4.5.Final.jar}.
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
public class AuditLogJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_id", nullable = false, unique = true)
	private UUID externalId = UUID.randomUUID();

	@Column(name = "user_id")
	private Long userId;

	@Column(name = "branch_id")
	private Long branchId;

	@Column(nullable = false, length = 50)
	private String action;

	@Column(name = "entity_name", nullable = false, length = 50)
	private String entityName;

	@Column(name = "entity_id", nullable = false, length = 100)
	private String entityId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "payload_before", columnDefinition = "jsonb")
	private String payloadBefore;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "payload_after", columnDefinition = "jsonb")
	private String payloadAfter;

	@Column(name = "ip_address", length = 50)
	private String ipAddress;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();
}
