package com.optiplant.inventory.purchases.infrastructure.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps {@code purchase_orders} ({@code 01-init-schema.sql:264-280}, design §6.1).
 * All foreign keys ({@code branch_id}, {@code supplier_id}, {@code user_id}) are plain
 * {@code Long} columns — NO {@code @ManyToOne} to {@code branches}, {@code suppliers} or
 * {@code users}.
 *
 * <p>{@code items} is {@code @OneToMany(cascade = ALL, orphanRemoval = true)}:
 * {@link PurchaseOrderItemJpaEntity} rows have no life outside their parent order.
 * {@code notes} carries the F-3 token; only {@link PurchaseOrderMapper} reads or writes it.
 * No {@code @Version} (F-5); {@code updated_at} is application-maintained.
 */
@Entity
@Table(name = "purchase_orders")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrderJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_id", nullable = false, unique = true)
	private UUID externalId = UUID.randomUUID();

	@Column(name = "order_number", nullable = false, unique = true, length = 50)
	private String orderNumber;

	@Column(name = "branch_id", nullable = false)
	private Long branchId;

	@Column(name = "supplier_id", nullable = false)
	private Long supplierId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(nullable = false, length = 30)
	private String status = "PENDING";

	@Column(name = "payment_terms", length = 100)
	private String paymentTerms;

	@Column(name = "total_amount", nullable = false, precision = 14, scale = 4)
	private BigDecimal totalAmount;

	@Column
	private String notes;

	@Column(name = "received_at")
	private Instant receivedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	@OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id ASC")
	private List<PurchaseOrderItemJpaEntity> items = new ArrayList<>();

	public void addItem(PurchaseOrderItemJpaEntity item) {
		item.setPurchaseOrder(this);
		items.add(item);
	}
}
