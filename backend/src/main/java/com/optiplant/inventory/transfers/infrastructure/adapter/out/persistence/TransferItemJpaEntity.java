package com.optiplant.inventory.transfers.infrastructure.adapter.out.persistence;

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
 * Maps {@code transfer_items} ({@code 01-init-schema.sql:397-407}, design §6.1). {@code product_id}
 * stays a plain, non-nullable {@code Long} column — {@code transfers} declares no {@code @Entity}
 * for {@code products} ({@code catalog}'s table). {@code transfer_id} is the aggregate's own
 * parent link and is the one exception to the "no {@code @ManyToOne}" rule (design §6.1): it never
 * crosses a module boundary, since {@link TransferJpaEntity} is owned by this same module.
 */
@Entity
@Table(name = "transfer_items")
@Getter
@Setter
@NoArgsConstructor
public class TransferItemJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_id", nullable = false, unique = true)
	private UUID externalId = UUID.randomUUID();

	@ManyToOne
	@JoinColumn(name = "transfer_id", nullable = false)
	private TransferJpaEntity transfer;

	@Column(name = "product_id", nullable = false)
	private Long productId;

	@Column(name = "requested_quantity", nullable = false, precision = 14, scale = 4)
	private BigDecimal requestedQuantity;

	@Column(name = "dispatched_quantity", nullable = false, precision = 14, scale = 4)
	private BigDecimal dispatchedQuantity = BigDecimal.ZERO;

	@Column(name = "received_quantity", nullable = false, precision = 14, scale = 4)
	private BigDecimal receivedQuantity = BigDecimal.ZERO;

	@Column(name = "discrepancy_quantity", nullable = false, precision = 14, scale = 4)
	private BigDecimal discrepancyQuantity = BigDecimal.ZERO;

	@Column(name = "discrepancy_reason")
	private String discrepancyReason;
}
