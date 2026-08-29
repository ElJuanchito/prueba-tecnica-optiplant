package com.optiplant.inventory.inventory.infrastructure.adapter.out.persistence;

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
 * Maps {@code branch_inventories} ({@code 01-init-schema.sql:190-207}, design §6.1).
 *
 * <p>{@code branch_id} and {@code product_id} stay plain, non-nullable {@code Long} columns —
 * deliberately <strong>no {@code @ManyToOne}</strong>. {@code inventory} declares no
 * {@code @Entity} for {@code branches} or {@code products}: both belong to other modules
 * ({@code iam}, {@code catalog}), and a second {@code @Entity} mapped onto either table would
 * give that module's row two owners in one persistence unit (design §9, rejected alternative).
 * External ids cross the port boundary; {@code ForeignKeyResolverSpringDataRepository} resolves
 * them to/from these {@code Long} columns.
 */
@Entity
@Table(name = "branch_inventories")
@Getter
@Setter
@NoArgsConstructor
public class BranchInventoryJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_id", nullable = false, unique = true)
	private UUID externalId = UUID.randomUUID();

	@Column(name = "branch_id", nullable = false)
	private Long branchId;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(name = "current_stock", nullable = false, precision = 14, scale = 4)
	private BigDecimal currentStock = BigDecimal.ZERO;

	@Column(name = "reserved_stock", nullable = false, precision = 14, scale = 4)
	private BigDecimal reservedStock = BigDecimal.ZERO;

	@Column(name = "in_transit_stock", nullable = false, precision = 14, scale = 4)
	private BigDecimal inTransitStock = BigDecimal.ZERO;

	/**
	 * Schema default is {@code 10.0000} — a row created on demand (F-3) MUST set this
	 * explicitly to {@code 0} at the persistence-adapter level; letting the column default
	 * apply would make a brand-new product fire {@code STOCK_MINIMUM} on its first movement
	 * (design §8).
	 */
	@Column(name = "min_stock_threshold", nullable = false, precision = 14, scale = 4)
	private BigDecimal minStockThreshold = BigDecimal.ZERO;

	@Column(name = "average_cost", nullable = false, precision = 14, scale = 4)
	private BigDecimal averageCost = BigDecimal.ZERO;

	@Column(name = "last_updated_at", nullable = false)
	private Instant lastUpdatedAt = Instant.now();
}
