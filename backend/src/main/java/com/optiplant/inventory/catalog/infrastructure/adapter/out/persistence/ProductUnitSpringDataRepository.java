package com.optiplant.inventory.catalog.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data repository for {@code product_units}.
 *
 * <p>{@link #clearDefaultSaleUnit} is the load-bearing statement of design §8.2 /
 * D-11. Both {@code @Modifying} flags are required:
 * <ul>
 *   <li>{@code flushAutomatically = true} pushes any pending change out
 *       <em>before</em> the bulk {@code UPDATE ... SET FALSE} runs, so it genuinely
 *       reaches the database ahead of the later {@code UPDATE ... SET TRUE};</li>
 *   <li>{@code clearAutomatically = true} drops the now-stale persistence context
 *       so no managed entity can write the old {@code TRUE} back afterwards.</li>
 * </ul>
 * {@code uq_product_units_single_default} is a partial unique index PostgreSQL
 * checks per statement and cannot defer, so without this ordering the table would
 * transiently hold two {@code TRUE} rows for one {@code product_id} and the whole
 * transaction would abort — with a correct domain and a correct schema.
 *
 * <p>The {@code AND u.defaultSaleUnit = TRUE} predicate restricts the write to the
 * at most one row that can hold the flag: the statement touches one row instead of
 * every unit of the product, and is a no-op when there is no previous default.
 */
public interface ProductUnitSpringDataRepository extends JpaRepository<ProductUnitJpaEntity, Long> {

	@Query("""
			SELECT u FROM ProductUnitJpaEntity u
			WHERE u.product.externalId = :productExternalId
			ORDER BY u.createdAt ASC, u.id ASC
			""")
	List<ProductUnitJpaEntity> findByProductExternalId(@Param("productExternalId") UUID productExternalId);

	@Query("""
			SELECT u FROM ProductUnitJpaEntity u
			WHERE u.product.externalId = :productExternalId AND u.externalId = :unitExternalId
			""")
	Optional<ProductUnitJpaEntity> findScoped(@Param("productExternalId") UUID productExternalId,
			@Param("unitExternalId") UUID unitExternalId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			UPDATE ProductUnitJpaEntity u SET u.defaultSaleUnit = FALSE
			WHERE u.product.externalId = :productExternalId AND u.defaultSaleUnit = TRUE
			""")
	void clearDefaultSaleUnit(@Param("productExternalId") UUID productExternalId);

	/**
	 * Bulk delete scoped by product. A bulk statement is used rather than
	 * {@code delete(entity)} because the parent {@link ProductJpaEntity} — loaded
	 * with its {@code units} collection by the service to drive
	 * {@code ProductUnitPolicy} — is managed in the same transaction, and an
	 * {@code em.remove} on a child still reachable from that managed
	 * {@code @OneToMany} collection is silently reconciled away at flush.
	 * {@code clearAutomatically} then drops the now-stale collection.
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
			DELETE FROM ProductUnitJpaEntity u
			WHERE u.product.externalId = :productExternalId AND u.externalId = :unitExternalId
			""")
	int deleteScoped(@Param("productExternalId") UUID productExternalId, @Param("unitExternalId") UUID unitExternalId);
}
