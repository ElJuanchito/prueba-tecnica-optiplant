package com.optiplant.inventory.pricing.infrastructure.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link PriceListJpaEntity} (design §6.1).
 */
public interface PriceListSpringDataRepository extends JpaRepository<PriceListJpaEntity, Long> {

	Optional<PriceListJpaEntity> findByExternalId(UUID externalId);

	Optional<PriceListJpaEntity> findByCode(String code);

	List<PriceListJpaEntity> findByExternalIdIn(Collection<UUID> externalIds);

	@Query(value = """
			SELECT pl.* FROM price_lists pl
			JOIN branches b ON b.default_price_list_id = pl.id
			WHERE b.external_id = :branchExternalId AND pl.is_active = TRUE
			""", nativeQuery = true)
	Optional<PriceListJpaEntity> findActiveDefaultListForBranch(@Param("branchExternalId") UUID branchExternalId);

	@Query(value = """
			SELECT * FROM price_lists
			WHERE (:active IS NULL OR is_active = :active)
			ORDER BY id ASC
			""", countQuery = """
			SELECT count(*) FROM price_lists
			WHERE (:active IS NULL OR is_active = :active)
			""", nativeQuery = true)
	Page<PriceListJpaEntity> search(@Param("active") Boolean active, Pageable pageable);
}
