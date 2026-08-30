package com.optiplant.inventory.logistics.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.logistics.domain.model.RoutePriority;
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
 * Maps {@code logistics_routes} ({@code 01-init-schema.sql:346-358}, design §6.1). {@code
 * origin_branch_id} and {@code destination_branch_id} stay plain, non-nullable {@code Long}
 * columns — {@code logistics} declares no {@code @Entity} for {@code branches} ({@code iam}'s
 * table). No {@code updated_at} column exists (F-6, §8): an edit updates fields in place with no
 * timestamp to refresh.
 */
@Entity
@Table(name = "logistics_routes")
@Getter
@Setter
@NoArgsConstructor
public class LogisticsRouteJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_id", nullable = false, unique = true)
	private UUID externalId = UUID.randomUUID();

	@Column(name = "origin_branch_id", nullable = false)
	private Long originBranchId;

	@Column(name = "destination_branch_id", nullable = false)
	private Long destinationBranchId;

	@Column(name = "estimated_duration_hours", nullable = false, precision = 6, scale = 2)
	private BigDecimal estimatedDurationHours;

	@Column(name = "transport_cost", nullable = false, precision = 12, scale = 2)
	private BigDecimal transportCost;

	@Enumerated(EnumType.STRING)
	@Column(name = "priority_level", nullable = false, length = 20)
	private RoutePriority priorityLevel;

	@Column(name = "is_active", nullable = false)
	private boolean active = true;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();
}
