package com.optiplant.inventory.iam.infrastructure.adapter.out.persistence;

import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogSpringDataRepository extends JpaRepository<AuditLogJpaEntity, Long> {

	// Every filter is optional (null = unfiltered); each `(:x IS NULL OR ...)` clause
	// keeps that opt-in behaviour without building the query string dynamically.
	// Native, not JPQL (found by executing, not by reading — CLAUDE.md): a plain
	// JPQL `(:from IS NULL OR a.createdAt >= :from)` makes PostgreSQL's extended
	// query protocol fail with "could not determine data type of parameter" for the
	// java.time.Instant params — Hibernate does not give the driver an explicit SQL
	// type hint for a parameter whose only occurrence is an `IS NULL` check, so an
	// explicit `::timestamptz` cast is required.
	@Query(value = """
			SELECT * FROM audit_logs
			WHERE (:userId IS NULL OR user_id = :userId)
			  AND (:branchId IS NULL OR branch_id = :branchId)
			  AND (:entityName IS NULL OR entity_name = :entityName)
			  AND (:action IS NULL OR action = :action)
			  AND (CAST(:from AS timestamptz) IS NULL OR created_at >= CAST(:from AS timestamptz))
			  AND (CAST(:to AS timestamptz) IS NULL OR created_at <= CAST(:to AS timestamptz))
			ORDER BY created_at DESC
			""",
			countQuery = """
					SELECT count(*) FROM audit_logs
					WHERE (:userId IS NULL OR user_id = :userId)
					  AND (:branchId IS NULL OR branch_id = :branchId)
					  AND (:entityName IS NULL OR entity_name = :entityName)
					  AND (:action IS NULL OR action = :action)
					  AND (CAST(:from AS timestamptz) IS NULL OR created_at >= CAST(:from AS timestamptz))
					  AND (CAST(:to AS timestamptz) IS NULL OR created_at <= CAST(:to AS timestamptz))
					""",
			nativeQuery = true)
	Page<AuditLogJpaEntity> search(@Param("userId") Long userId, @Param("branchId") Long branchId,
			@Param("entityName") String entityName, @Param("action") String action, @Param("from") Instant from,
			@Param("to") Instant to, Pageable pageable);
}
