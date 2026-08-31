package com.optiplant.inventory.logistics.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@code logistics_routes} (design §6.1, §7). No lock is taken on any
 * method here — routes carry no version column and are edited only by a single corporate
 * {@code ADMIN} flow (design §6.1).
 */
public interface LogisticsRouteSpringDataRepository extends JpaRepository<LogisticsRouteJpaEntity, Long> {

	Optional<LogisticsRouteJpaEntity> findByExternalId(UUID externalId);

	/** Active only (R-24, P-11) — the pair {@code RouteLeadTimeAdapter} reads. */
	Optional<LogisticsRouteJpaEntity> findByOriginBranchIdAndDestinationBranchIdAndActiveTrue(Long originBranchId,
			Long destinationBranchId);

	/** Backs R-23's duplicate-pair refusal, mirroring {@code uq_route_pair} — any state, not just active. */
	boolean existsByOriginBranchIdAndDestinationBranchId(Long originBranchId, Long destinationBranchId);

	/**
	 * {@code active} is nullable (contract §6 {@code active?}): {@code null} lists every route,
	 * regardless of state. Carries no {@code ORDER BY} of its own — {@code cost_asc} and {@code
	 * duration_asc} (RF-LOG-03) are plain column sorts, so the caller supplies them through
	 * {@code pageable}'s {@link org.springframework.data.domain.Sort}, exactly the technique
	 * {@code ProductSpringDataRepository} already uses for {@code ProductSort}.
	 */
	@Query(value = "SELECT r FROM LogisticsRouteJpaEntity r WHERE (:active IS NULL OR r.active = :active)",
			countQuery = "SELECT COUNT(r) FROM LogisticsRouteJpaEntity r WHERE (:active IS NULL OR r.active = :active)")
	Page<LogisticsRouteJpaEntity> search(@Param("active") Boolean active, Pageable pageable);

	/**
	 * {@code priority_desc} (RF-LOG-03) — {@code URGENT} first, then {@code STANDARD}, then
	 * {@code LOW}. A plain {@code ORDER BY r.priorityLevel DESC} would sort alphabetically
	 * (Z-to-A), which only happens to land {@code URGENT, STANDARD, LOW} today because the three
	 * literals are alphabetically ordered that way; it silently breaks the day a fourth value
	 * (e.g. {@code CRITICAL}) is added without also sorting alphabetically first. This
	 * {@code CASE} expression ranks each literal explicitly instead ({@code RouteSort}'s
	 * javadoc), so a new priority value simply falls into {@code ELSE} until it earns a rank.
	 */
	@Query(value = """
			SELECT r FROM LogisticsRouteJpaEntity r WHERE (:active IS NULL OR r.active = :active)
			ORDER BY CASE r.priorityLevel
				WHEN URGENT THEN 0
				WHEN STANDARD THEN 1
				WHEN LOW THEN 2
				ELSE 3
			END
			""",
			countQuery = "SELECT COUNT(r) FROM LogisticsRouteJpaEntity r WHERE (:active IS NULL OR r.active = :active)")
	Page<LogisticsRouteJpaEntity> searchOrderByPriorityRank(@Param("active") Boolean active, Pageable pageable);
}
