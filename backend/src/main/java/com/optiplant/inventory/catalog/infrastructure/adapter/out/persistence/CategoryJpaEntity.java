package com.optiplant.inventory.catalog.infrastructure.adapter.out.persistence;

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

/**
 * Maps {@code categories} ({@code 01-init-schema.sql:78-86}), including the
 * {@code is_active} column (S-1) and the {@code updated_at} column (S-2) this
 * change added. Same Lombok shape as {@code iam}'s {@code BranchJpaEntity}.
 *
 * <p>The numeric {@code id} lives here and never leaves this package: the
 * persistence adapter resolves it only to feed the product-side counts and
 * always returns {@code external_id} UUIDs and domain records (design §6.2).
 */
@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
public class CategoryJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_id", nullable = false, unique = true)
	private UUID externalId = UUID.randomUUID();

	@Column(nullable = false, unique = true)
	private String name;

	@Column
	private String description;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();
}
