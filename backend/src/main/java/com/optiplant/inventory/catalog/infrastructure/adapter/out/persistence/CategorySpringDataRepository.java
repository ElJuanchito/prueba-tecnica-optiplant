package com.optiplant.inventory.catalog.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@code categories}.
 *
 * <p>{@link #existsByNameIgnoringCase} is JPQL over {@code LOWER(c.name)} — the
 * application half of R-02, whose schema half is {@code uq_categories_name_ci}
 * (S-4). {@link #search} is JPQL with a fixed ascending sort by name (contract
 * §6.1), so it never needs a dynamic {@code Sort} and the D-10 "JPQL, never
 * native" rule (which is about the deferred <em>product</em> search) does not
 * bite here either way.
 *
 * <p>{@link #hasActiveProducts} and {@link #countActiveProductsByCategoryIds}
 * read the {@code products} table. They are <strong>native</strong> only because
 * {@code ProductJpaEntity} does not exist until S5 (task 5.1); neither carries a
 * dynamic {@code Sort}, so D-10 is not violated. S5 may migrate them to JPQL
 * against {@code ProductJpaEntity} once it lands (see apply-progress S3 note).
 * The category listing therefore costs exactly two statements regardless of page
 * size — the page query plus one grouped count (design §6.2, task 3.4).
 */
public interface CategorySpringDataRepository extends JpaRepository<CategoryJpaEntity, Long> {

	Optional<CategoryJpaEntity> findByExternalId(UUID externalId);

	@Query("""
			SELECT COUNT(c) > 0 FROM CategoryJpaEntity c
			WHERE LOWER(c.name) = :key
			  AND (:excludingExternalId IS NULL OR c.externalId <> :excludingExternalId)
			""")
	boolean existsByNameIgnoringCase(@Param("key") String comparisonKey,
			@Param("excludingExternalId") UUID excludingExternalId);

	/**
	 * {@code namePattern} is a pre-lowercased {@code %contains%} pattern built by
	 * the adapter, or {@code null} for "no name filter". It is kept off any SQL
	 * string function on purpose: passing a raw {@code null} into
	 * {@code LOWER(CONCAT(...))} makes PostgreSQL infer {@code lower(bytea)} and
	 * fail, so the {@code LOWER} stays on the {@code c.name} column only.
	 */
	@Query("""
			SELECT c FROM CategoryJpaEntity c
			WHERE (:namePattern IS NULL OR LOWER(c.name) LIKE :namePattern)
			  AND (:active IS NULL OR c.active = :active)
			ORDER BY c.name ASC
			""")
	Page<CategoryJpaEntity> search(@Param("namePattern") String namePattern, @Param("active") Boolean active,
			Pageable pageable);

	@Query(value = """
			SELECT EXISTS (
			    SELECT 1 FROM products p
			    JOIN categories c ON c.id = p.category_id
			    WHERE c.external_id = :categoryExternalId AND p.is_active = TRUE
			)
			""", nativeQuery = true)
	boolean hasActiveProducts(@Param("categoryExternalId") UUID categoryExternalId);

	/**
	 * One grouped row per category id that still has at least one active product.
	 * Each element is {@code [category_id (Long), count (Long)]}. Categories with
	 * no active products are simply absent.
	 */
	@Query(value = """
			SELECT p.category_id, COUNT(*)
			FROM products p
			WHERE p.category_id IN (:categoryIds) AND p.is_active = TRUE
			GROUP BY p.category_id
			""", nativeQuery = true)
	List<Object[]> countActiveProductsByCategoryIds(@Param("categoryIds") List<Long> categoryIds);
}
