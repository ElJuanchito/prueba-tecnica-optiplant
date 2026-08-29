package com.optiplant.inventory.catalog.infrastructure.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps {@code products} ({@code 01-init-schema.sql:94-104}). Same Lombok shape as
 * {@code iam}'s {@code BranchJpaEntity}.
 *
 * <p>Deviation from {@code UserJpaEntity}, which keeps its {@code branch_id} as a
 * plain {@code Long} because "no {@code BranchJpaEntity} exists yet" (its own
 * Javadoc): here both sides of every association live in {@code catalog} and are
 * created in the same change, and two needs force real associations —
 * {@code POST /products} persists inline units in one transaction (R-06) and the
 * product detail response embeds the category (contract §6.2). The {@code @ManyToOne}
 * is {@code LAZY}; the N+1 that usually argues against it is closed by the
 * {@code JOIN FETCH} in {@link ProductSpringDataRepository#search} (design §6.2, D-5).
 *
 * <p>{@code conversion_factor} on the unit side is {@code NUMERIC(12,4)}, so the
 * Java type is {@link java.math.BigDecimal} — never {@code double}, which cannot
 * represent a decimal factor exactly.
 *
 * <p>The numeric {@code id} never leaves this package: every port method traffics
 * in {@code external_id} UUIDs and domain records.
 */
@Entity
@Table(name = "products")
@Getter
@Setter
@NoArgsConstructor
public class ProductJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_id", nullable = false, unique = true)
	private UUID externalId = UUID.randomUUID();

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id", nullable = false)
	private CategoryJpaEntity category;

	@Column(nullable = false, unique = true)
	private String sku;

	@Column(nullable = false)
	private String name;

	@Column
	private String description;

	@Column(name = "base_unit", nullable = false)
	private String baseUnit;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	@OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<ProductUnitJpaEntity> units = new ArrayList<>();

	/** Adds a unit and keeps the bidirectional link consistent so the cascade persists it. */
	public void addUnit(ProductUnitJpaEntity unit) {
		unit.setProduct(this);
		units.add(unit);
	}
}
