package com.optiplant.inventory.sales.infrastructure.adapter.out.persistence.customer;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@code customers} (design §6).
 */
public interface CustomerSpringDataRepository extends JpaRepository<CustomerJpaEntity, Long> {

	Optional<CustomerJpaEntity> findByExternalId(UUID externalId);

	@Query(value = """
			SELECT COUNT(*) > 0 FROM customers
			WHERE tax_id = :taxId
			  AND (:excludingExternalId IS NULL OR external_id <> :excludingExternalId)
			""", nativeQuery = true)
	boolean existsByTaxId(@Param("taxId") String taxId,
			@Param("excludingExternalId") UUID excludingExternalId);

	@Query(value = """
			SELECT * FROM customers
			WHERE (:search IS NULL OR name ILIKE :search OR tax_id ILIKE :search)
			  AND (:active IS NULL OR is_active = :active)
			ORDER BY name ASC
			""", countQuery = """
			SELECT COUNT(*) FROM customers
			WHERE (:search IS NULL OR name ILIKE :search OR tax_id ILIKE :search)
			  AND (:active IS NULL OR is_active = :active)
			""", nativeQuery = true)
	Page<CustomerJpaEntity> searchOrderByNameAsc(@Param("search") String search,
			@Param("active") Boolean active, Pageable pageable);

	@Query(value = """
			SELECT * FROM customers
			WHERE (:search IS NULL OR name ILIKE :search OR tax_id ILIKE :search)
			  AND (:active IS NULL OR is_active = :active)
			ORDER BY name DESC
			""", countQuery = """
			SELECT COUNT(*) FROM customers
			WHERE (:search IS NULL OR name ILIKE :search OR tax_id ILIKE :search)
			  AND (:active IS NULL OR is_active = :active)
			""", nativeQuery = true)
	Page<CustomerJpaEntity> searchOrderByNameDesc(@Param("search") String search,
			@Param("active") Boolean active, Pageable pageable);

	@Query(value = """
			SELECT * FROM customers
			WHERE (:search IS NULL OR name ILIKE :search OR tax_id ILIKE :search)
			  AND (:active IS NULL OR is_active = :active)
			ORDER BY created_at DESC
			""", countQuery = """
			SELECT COUNT(*) FROM customers
			WHERE (:search IS NULL OR name ILIKE :search OR tax_id ILIKE :search)
			  AND (:active IS NULL OR is_active = :active)
			""", nativeQuery = true)
	Page<CustomerJpaEntity> searchOrderByCreatedAtDesc(@Param("search") String search,
			@Param("active") Boolean active, Pageable pageable);
}
