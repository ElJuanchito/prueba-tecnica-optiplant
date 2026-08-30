package com.optiplant.inventory.pricing.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps {@code price_lists} ({@code 01-init-schema.sql:141-153}, design §6.1).
 * FKs to other modules are plain {@code Long} columns; this table has no foreign keys.
 */
@Entity
@Table(name = "price_lists")
@Getter
@Setter
@NoArgsConstructor
public class PriceListJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_id", nullable = false, unique = true)
	private UUID externalId = UUID.randomUUID();

	@Column(nullable = false, unique = true, length = 30)
	private String code;

	@Column(nullable = false, length = 100)
	private String name;

	@Column
	private String description;

	@Column(name = "max_discount_percent", nullable = false, precision = 5, scale = 2)
	private BigDecimal maxDiscountPercent = BigDecimal.ZERO;

	@Column(name = "is_default", nullable = false)
	private boolean isDefault = false;

	@Column(name = "is_active", nullable = false)
	private boolean isActive = true;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();
}
