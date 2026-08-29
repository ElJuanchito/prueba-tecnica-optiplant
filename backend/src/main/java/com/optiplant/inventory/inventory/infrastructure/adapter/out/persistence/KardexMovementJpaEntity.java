package com.optiplant.inventory.inventory.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.shared.stock.StockMovementType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Maps {@code kardex_movements} ({@code 01-init-schema.sql:210-236}, design §6.1). Append-only:
 * no method here or on {@link KardexMovementSpringDataRepository} updates or deletes a row
 * (R-17, RNF-INT-02).
 *
 * <p>{@code branch_id}, {@code product_id} and {@code user_id} stay plain {@code Long} columns —
 * same reasoning as {@link BranchInventoryJpaEntity}: {@code inventory} owns this table and
 * {@code branch_inventories} only, nothing else.
 */
@Entity
@Table(name = "kardex_movements")
@Getter
@Setter
@NoArgsConstructor
public class KardexMovementJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_id", nullable = false, unique = true)
	private UUID externalId = UUID.randomUUID();

	@Column(name = "branch_id", nullable = false)
	private Long branchId;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Enumerated(EnumType.STRING)
	@Column(name = "movement_type", nullable = false, length = 30)
	private StockMovementType movementType;

	@Column(nullable = false, precision = 14, scale = 4)
	private BigDecimal quantity;

	@Column(name = "unit_cost", nullable = false, precision = 14, scale = 4)
	private BigDecimal unitCost;

	@Column(name = "total_cost", nullable = false, precision = 14, scale = 4)
	private BigDecimal totalCost;

	@Column(name = "previous_stock", nullable = false, precision = 14, scale = 4)
	private BigDecimal previousStock;

	@Column(name = "resulting_stock", nullable = false, precision = 14, scale = 4)
	private BigDecimal resultingStock;

	@Column(name = "reference_id", length = 100)
	private String referenceId;

	@Column(name = "reference_type", length = 50)
	private String referenceType;

	@Column
	private String notes;

	/** Nullable — {@code ON DELETE SET NULL} (schema); a movement survives its actor's deletion. */
	@Column(name = "user_id")
	private Long userId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();
}
