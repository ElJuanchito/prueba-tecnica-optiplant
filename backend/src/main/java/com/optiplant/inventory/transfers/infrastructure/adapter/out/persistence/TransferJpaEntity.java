package com.optiplant.inventory.transfers.infrastructure.adapter.out.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps {@code transfers} ({@code 01-init-schema.sql:362-390}, design §6.1). {@code origin_branch_id},
 * {@code destination_branch_id}, {@code requested_by_user_id}, {@code dispatched_by_user_id} and
 * {@code received_by_user_id} stay plain {@code Long} columns — never {@code @ManyToOne}: none of
 * {@code branches} or {@code users} belongs to this module.
 *
 * <p>{@code items} is the one {@code @OneToMany(cascade = ALL, orphanRemoval = true)} in this
 * change: {@link TransferItemJpaEntity} rows have no life outside their transfer (design §6.1).
 * {@code notes} carries the F-1 priority token; only {@code TransferMapper} reads or writes it.
 * {@code updated_at} has no trigger (F-5, §8) — the mapper sets it explicitly on every mutation.
 */
@Entity
@Table(name = "transfers")
@Getter
@Setter
@NoArgsConstructor
public class TransferJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_id", nullable = false, unique = true)
	private UUID externalId = UUID.randomUUID();

	@Column(name = "transfer_number", nullable = false, unique = true, length = 50)
	private String transferNumber;

	@Column(name = "origin_branch_id", nullable = false)
	private Long originBranchId;

	@Column(name = "destination_branch_id", nullable = false)
	private Long destinationBranchId;

	@Column(name = "requested_by_user_id", nullable = false)
	private Long requestedByUserId;

	@Column(name = "dispatched_by_user_id")
	private Long dispatchedByUserId;

	@Column(name = "received_by_user_id")
	private Long receivedByUserId;

	@Column(nullable = false, length = 35)
	private String status;

	@Column(name = "carrier_name", length = 100)
	private String carrierName;

	@Column(name = "tracking_number", length = 100)
	private String trackingNumber;

	@Column(name = "dispatched_at")
	private Instant dispatchedAt;

	@Column(name = "estimated_arrival_at")
	private Instant estimatedArrivalAt;

	@Column(name = "actual_arrival_at")
	private Instant actualArrivalAt;

	@Column
	private String notes;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt = Instant.now();

	@OneToMany(mappedBy = "transfer", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("id ASC")
	private List<TransferItemJpaEntity> items = new ArrayList<>();

	/** Keeps the bidirectional link consistent — required for {@code cascade = ALL} to persist a new item. */
	public void addItem(TransferItemJpaEntity item) {
		item.setTransfer(this);
		items.add(item);
	}
}
