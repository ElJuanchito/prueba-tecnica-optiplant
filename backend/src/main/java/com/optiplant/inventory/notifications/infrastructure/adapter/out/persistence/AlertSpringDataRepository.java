package com.optiplant.inventory.notifications.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.shared.alert.AlertSeverity;
import com.optiplant.inventory.shared.alert.AlertType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@code system_alerts} (design §6.3, DT-09).
 *
 * <p>{@link #advisoryLock} takes a PostgreSQL transaction advisory lock —
 * {@code SELECT pg_advisory_xact_lock(hashtext(:key))} — serializing concurrent alerts for the
 * same dedup subject without a schema change. It MUST be the first statement of the caller's
 * transaction (design §6.3). {@code pg_advisory_xact_lock} returns SQL {@code void}; comparing
 * it with {@code IS NULL} (always {@code false} for a non-null void value) is the portable way
 * to read a usable, typed result from a void-returning function call.
 */
public interface AlertSpringDataRepository extends JpaRepository<SystemAlertJpaEntity, Long> {

	@Query(value = "SELECT pg_advisory_xact_lock(hashtext(:key)) IS NULL", nativeQuery = true)
	boolean advisoryLock(@Param("key") String key);

	@Query("""
			SELECT a FROM SystemAlertJpaEntity a
			WHERE a.branchId = :branchId AND a.alertType = :alertType AND a.title = :title AND a.resolved = false
			""")
	Optional<SystemAlertJpaEntity> findUnresolvedByDedupKey(@Param("branchId") Long branchId,
			@Param("alertType") AlertType alertType, @Param("title") String title);

	Optional<SystemAlertJpaEntity> findByExternalId(UUID externalId);

	/** Ordered severity ({@code CRITICAL} first) then recency (contract §6). */
	@Query(value = """
			SELECT a FROM SystemAlertJpaEntity a
			WHERE (:branchId IS NULL OR a.branchId = :branchId)
			  AND (:resolved IS NULL OR a.resolved = :resolved)
			  AND (:alertType IS NULL OR a.alertType = :alertType)
			  AND (:severity IS NULL OR a.severity = :severity)
			ORDER BY CASE a.severity
			             WHEN com.optiplant.inventory.shared.alert.AlertSeverity.CRITICAL THEN 0
			             WHEN com.optiplant.inventory.shared.alert.AlertSeverity.WARNING THEN 1
			             ELSE 2
			         END ASC, a.createdAt DESC
			""", countQuery = """
			SELECT COUNT(a) FROM SystemAlertJpaEntity a
			WHERE (:branchId IS NULL OR a.branchId = :branchId)
			  AND (:resolved IS NULL OR a.resolved = :resolved)
			  AND (:alertType IS NULL OR a.alertType = :alertType)
			  AND (:severity IS NULL OR a.severity = :severity)
			""")
	Page<SystemAlertJpaEntity> search(@Param("branchId") Long branchId, @Param("resolved") Boolean resolved,
			@Param("alertType") AlertType alertType, @Param("severity") AlertSeverity severity, Pageable pageable);
}
