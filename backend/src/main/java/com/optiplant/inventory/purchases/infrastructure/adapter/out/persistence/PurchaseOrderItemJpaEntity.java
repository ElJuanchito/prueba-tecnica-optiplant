package com.optiplant.inventory.purchases.infrastructure.adapter.out.persistence;

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
 * Maps {@code purchase_order_items} ({@code 01-init-schema.sql:287-297}, design §6.1).
 * {@code product_id} stays a plain, non-nullable {@code Long} column — NO {@code @Entity} for
 * {@code products}. {@code purchase_order_id} is the aggregate's own parent link (design §6.1).
 * It has no timestamp and no unit-of-measure column (F-4).
 */
@Entity
@Table(name = "purchase_order_items")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrderItemJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_id", nullable = false, unique = true)
	private UUID externalId = UUID.randomUUID();

	@ManyToOne
	@JoinColumn(name = "purchase_order_id", nullable = false)
	private PurchaseOrderJpaEntity purchaseOrder;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(name = "ordered_quantity", nullable = false, precision = 14, scale = 4)
	private BigDecimal orderedQuantity;

	@Column(name = "received_quantity", nullable = false, precision = 14, scale = 4)
	private BigDecimal receivedQuantity = BigDecimal.ZERO;

	@Column(name = "unit_cost", nullable = false, precision = 14, scale = 4)
	private BigDecimal unitCost;

	@Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
	private BigDecimal discountPercent = BigDecimal.ZERO;

	@Column(nullable = false, precision = 14, scale = 4)
	private BigDecimal subtotal;
}
