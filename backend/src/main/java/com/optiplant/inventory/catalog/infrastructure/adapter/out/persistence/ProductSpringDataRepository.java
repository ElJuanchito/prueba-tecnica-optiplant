package com.optiplant.inventory.catalog.infrastructure.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@code products}.
 *
 * <p>{@link #search} is <strong>JPQL, never native</strong> (D-10): Spring Data
 * JPA rejects a dynamic {@code Sort} on a native query — a fact this repo learned
 * by executing ({@code AuditWriteAdapter.java:56-58}) — and R-12 needs three sort
 * fields, so the {@code Pageable}'s {@code Sort} (built from {@code ProductSort})
 * is the only shape that works. {@code JOIN FETCH p.category} keeps a 100-row page
 * at one query instead of 101 (the N+1 contract §6.2 warns about); an explicit
 * {@code countQuery} without the fetch is supplied so pagination's count stays
 * valid.
 *
 * <p>{@code :q} is a pre-lowercased {@code %contains%} pattern (or {@code null})
 * built by the adapter, matched against {@code LOWER(p.sku)} / {@code LOWER(p.name)}.
 * It is kept off any SQL string function on purpose: wrapping a nullable bind
 * parameter in {@code LOWER(CONCAT(...))} makes PostgreSQL infer
 * {@code lower(bytea)} and 500 — the same correction slice S3 applied to the
 * category search.
 */
public interface ProductSpringDataRepository extends JpaRepository<ProductJpaEntity, Long> {

	@Query("""
			SELECT DISTINCT p FROM ProductJpaEntity p
			JOIN FETCH p.category c
			LEFT JOIN FETCH p.units
			WHERE p.externalId = :externalId
			""")
	Optional<ProductJpaEntity> findByExternalIdWithUnits(@Param("externalId") UUID externalId);

	@Query("""
			SELECT COUNT(p) > 0 FROM ProductJpaEntity p
			WHERE p.sku = :normalizedSku
			  AND (:excludingExternalId IS NULL OR p.externalId <> :excludingExternalId)
			""")
	boolean existsBySku(@Param("normalizedSku") String normalizedSku,
			@Param("excludingExternalId") UUID excludingExternalId);

	@Query(value = """
			SELECT p FROM ProductJpaEntity p
			JOIN FETCH p.category c
			WHERE (:q IS NULL OR LOWER(p.sku) LIKE :q OR LOWER(p.name) LIKE :q)
			  AND (:categoryExternalId IS NULL OR c.externalId = :categoryExternalId)
			  AND (:active IS NULL OR p.active = :active)
			""", countQuery = """
			SELECT COUNT(p) FROM ProductJpaEntity p
			WHERE (:q IS NULL OR LOWER(p.sku) LIKE :q OR LOWER(p.name) LIKE :q)
			  AND (:categoryExternalId IS NULL OR p.category.externalId = :categoryExternalId)
			  AND (:active IS NULL OR p.active = :active)
			""")
	Page<ProductJpaEntity> search(@Param("q") String q, @Param("categoryExternalId") UUID categoryExternalId,
			@Param("active") Boolean active, Pageable pageable);
}
