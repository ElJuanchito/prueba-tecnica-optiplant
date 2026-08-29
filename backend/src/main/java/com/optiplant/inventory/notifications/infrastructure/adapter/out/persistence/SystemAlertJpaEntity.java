package com.optiplant.inventory.notifications.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.shared.alert.AlertSeverity;
import com.optiplant.inventory.shared.alert.AlertType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Maps {@code system_alerts} ({@code 01-init-schema.sql:415-430}). {@code branch_id} and
 * {@code resolved_by_user_id} stay plain, nullable {@code Long} columns — {@code notifications}
 * declares no {@code @Entity} for {@code branches} or {@code users} (same reasoning as
 * {@code inventory}'s {@code BranchInventoryJpaEntity}).
 *
 * <p>{@code title} carries the F-1 dedup token ({@code "ALERT_TYPE:<subjectToken>"}), written
 * exactly once by {@code AlertDedupKey#title()} and never reconstructed here.
 */
@Entity
@Table(name = "system_alerts")
@Getter
@Setter
@NoArgsConstructor
public class SystemAlertJpaEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "external_id", nullable = false, unique = true)
	private UUID externalId = UUID.randomUUID();

	@Column(name = "branch_id")
	private Long branchId;

	@Enumerated(EnumType.STRING)
	@Column(name = "alert_type", nullable = false, length = 40)
	private AlertType alertType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AlertSeverity severity;

	@Column(nullable = false, length = 150)
	private String title;

	@Column(nullable = false)
	private String message;

	@Column(name = "is_resolved", nullable = false)
	private boolean resolved = false;

	@Column(name = "resolved_at")
	private Instant resolvedAt;

	@Column(name = "resolved_by_user_id")
	private Long resolvedByUserId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt = Instant.now();
}
