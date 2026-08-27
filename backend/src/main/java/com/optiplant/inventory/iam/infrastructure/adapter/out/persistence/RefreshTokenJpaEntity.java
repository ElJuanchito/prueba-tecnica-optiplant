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

/** Maps {@code refresh_tokens} (01-init-schema.sql, added in slice 1). {@code user_id}
 * stays a plain {@code Long} FK, resolved from the caller's {@code external_id} by the
 * adapter — the same UUID→BIGINT pattern the design assigns to the slice-4 audit
 * adapter. */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
public class RefreshTokenJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_id", nullable = false, unique = true)
	private UUID externalId = UUID.randomUUID();

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "family_id", nullable = false)
	private UUID familyId;

	@Column(name = "token_hash", nullable = false, unique = true, length = 64)
	private String tokenHash;

	@Column(name = "issued_at", nullable = false)
	private Instant issuedAt;

	@Column(name = "last_used_at", nullable = false)
	private Instant lastUsedAt;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "revoked_reason", length = 20)
	private String revokedReason;
}
