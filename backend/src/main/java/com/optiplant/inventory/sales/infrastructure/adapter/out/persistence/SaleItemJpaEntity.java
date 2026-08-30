package com.optiplant.inventory.sales.infrastructure.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps {@code sale_items} ({@code 01-init-schema.sql:327-338}, design §6.1).
 * {@code product_id} is a plain {@code Long} column — NO {@code @Entity} over {@code products}.
 * {@code sale_id} is the parent link within the same aggregate.
 * {@code sale_items} has no timestamp and no unit-of-measure column (F-8).
 */
@Entity
@Table(name = "sale_items")
@Getter
@Setter
@NoArgsConstructor
public class SaleItemJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_id", nullable = false, unique = true)
	private UUID externalId = UUID.randomUUID();

	@ManyToOne
	@JoinColumn(name = "sale_id", nullable = false)
	private SaleJpaEntity sale;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(nullable = false, precision = 14, scale = 4)
	private BigDecimal quantity;

	@Column(name = "list_unit_price", nullable = false, precision = 14, scale = 4)
	private BigDecimal listUnitPrice;

	@Column(name = "unit_price", nullable = false, precision = 14, scale = 4)
	private BigDecimal unitPrice;

	@Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
	private BigDecimal discountPercent = BigDecimal.ZERO;

	@Column(nullable = false, precision = 14, scale = 4)
	private BigDecimal subtotal;
}
