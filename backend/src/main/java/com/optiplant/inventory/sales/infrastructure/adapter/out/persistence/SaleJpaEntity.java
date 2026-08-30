package com.optiplant.inventory.sales.infrastructure.adapter.out.persistence;

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
 * Maps {@code sales} ({@code 01-init-schema.sql:305-321}, design §6.1).
 * All foreign keys ({@code branch_id}, {@code user_id}, {@code price_list_id}) are plain
 * {@code Long} columns — NO {@code @ManyToOne} and NO {@code @Entity} over {@code branches},
 * {@code users} or {@code price_lists}.
 *
 * <p>{@code items} is {@code @OneToMany(cascade = ALL, orphanRemoval = true)}: {@link SaleItemJpaEntity}
 * rows have no life outside their parent sale. {@code notes} carries the F-3 token; only
 * {@code SaleMapper} reads or writes it. {@code sales} has no {@code updated_at} and no {@code version}.
 */
@Entity
@Table(name = "sales")
@Getter
@Setter
@NoArgsConstructor
public class SaleJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_id", nullable = false, unique = true)
	private UUID externalId = UUID.randomUUID();

	@Column(name = "invoice_number", nullable = false, unique = true, length = 50)
	private String invoiceNumber;

	@Column(name = "branch_id", nullable = false)
	private Long branchId;

	@Column(name = "user_id", nullable = false)
	private Long userId;

	@Column(name = "price_list_id", nullable = false)
	private Long priceListId;

	@Column(name = "customer_name", nullable = false, length = 150)
	private String customerName;

	@Column(name = "customer_tax_id", length = 30)
	private String customerTaxId;

	@Column(nullable = false, length = 20)
	private String status = "COMPLETED";

	@Column(nullable = false, precision = 14, scale = 4)
	private BigDecimal subtotal;

	@Column(name = "discount_amount", nullable = false, precision = 14, scale = 4)
	private BigDecimal discountAmount = BigDecimal.ZERO;

	@Column(name = "tax_amount", nullable = false, precision = 14, scale = 4)
	private BigDecimal taxAmount = BigDecimal.ZERO;

	@Column(name = "total_amount", nullable = false, precision = 14, scale = 4)
	private BigDecimal totalAmount;

	@Column
	private String notes;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@OneToMany(mappedBy = "sale", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id ASC")
	private List<SaleItemJpaEntity> items = new ArrayList<>();

	public void addItem(SaleItemJpaEntity item) {
		item.setSale(this);
		items.add(item);
	}
}
