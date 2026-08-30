package com.optiplant.inventory.pricing.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps {@code price_list_items} ({@code 01-init-schema.sql:159-171}, design §6.1).
 * All foreign keys ({@code price_list_id}, {@code product_id}, {@code branch_id}) are plain
 * {@code Long} columns — NO {@code @ManyToOne} and NO {@code @Entity} over {@code products} or
 * {@code branches}.
 */
@Entity
@Table(name = "price_list_items")
@Getter
@Setter
@NoArgsConstructor
public class PriceListItemJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_id", nullable = false, unique = true)
	private UUID externalId = UUID.randomUUID();

	@Column(name = "price_list_id", nullable = false)
	private Long priceListId;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(name = "branch_id")
	private Long branchId;

	@Column(name = "unit_price", nullable = false, precision = 14, scale = 4)
	private BigDecimal unitPrice;

	@Column(name = "valid_from", nullable = false)
	private LocalDate validFrom = LocalDate.now();

	@Column(name = "valid_to")
	private LocalDate validTo;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();
}
