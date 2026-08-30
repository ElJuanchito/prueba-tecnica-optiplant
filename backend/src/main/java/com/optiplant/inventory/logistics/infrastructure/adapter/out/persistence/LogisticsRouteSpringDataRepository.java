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

	/** {@code active} is nullable (contract §6 {@code active?}): {@code null} lists every route, regardless of state. */
	@Query(value = "SELECT r FROM LogisticsRouteJpaEntity r WHERE (:active IS NULL OR r.active = :active)",
			countQuery = "SELECT COUNT(r) FROM LogisticsRouteJpaEntity r WHERE (:active IS NULL OR r.active = :active)")
	Page<LogisticsRouteJpaEntity> search(@Param("active") Boolean active, Pageable pageable);
}
