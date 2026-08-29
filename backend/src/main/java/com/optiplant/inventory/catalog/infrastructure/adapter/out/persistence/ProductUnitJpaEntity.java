package com.optiplant.inventory.catalog.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps {@code product_units} ({@code 01-init-schema.sql:112-121}). Same Lombok
 * shape as {@code iam}'s {@code BranchJpaEntity}.
 *
 * <p>There is <strong>no</strong> {@code updated_at} mapping: the table has no such
 * column and none is added (contract §6.3). {@code conversion_factor} is
 * {@code NUMERIC(12,4)} → {@link BigDecimal}.
 */
@Entity
@Table(name = "product_units")
@Getter
@Setter
@NoArgsConstructor
public class ProductUnitJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_id", nullable = false, unique = true)
	private UUID externalId = UUID.randomUUID();

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private ProductJpaEntity product;

	@Column(name = "unit_name", nullable = false)
	private String unitName;

	@Column(name = "conversion_factor", nullable = false)
	private BigDecimal conversionFactor;

	@Column(name = "is_default_sale_unit", nullable = false)
	private boolean defaultSaleUnit = false;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();
}
