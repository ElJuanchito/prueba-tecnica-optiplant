package com.optiplant.inventory.purchases.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Resolves references (branches, products, users, suppliers, units) for purchases (design §4, §6.1, §6.3).
 */
public interface PurchaseReferenceSpringDataRepository extends Repository<PurchaseOrderJpaEntity, Long> {

	@Query(value = "SELECT id FROM products WHERE external_id = :externalId AND is_active = TRUE", nativeQuery = true)
	Optional<Long> findActiveProductIdByExternalId(@Param("externalId") UUID externalId);

	@Query(value = "SELECT id FROM products WHERE external_id = :externalId", nativeQuery = true)
	Optional<Long> findProductIdByExternalId(@Param("externalId") UUID externalId);

	@Query(value = "SELECT id FROM branches WHERE external_id = :externalId AND is_active = TRUE", nativeQuery = true)
	Optional<Long> findActiveBranchIdByExternalId(@Param("externalId") UUID externalId);

	@Query(value = "SELECT id FROM users WHERE external_id = :externalId", nativeQuery = true)
	Optional<Long> findUserIdByExternalId(@Param("externalId") UUID externalId);

	@Query(value = "SELECT id FROM suppliers WHERE external_id = :externalId AND is_active = TRUE", nativeQuery = true)
	Optional<Long> findActiveSupplierIdByExternalId(@Param("externalId") UUID externalId);

	@Query(value = "SELECT id FROM suppliers WHERE external_id = :externalId", nativeQuery = true)
	Optional<Long> findSupplierIdByExternalId(@Param("externalId") UUID externalId);

	@Query(value = "SELECT external_id AS externalId FROM products WHERE external_id IN (:externalIds) AND is_active = TRUE", nativeQuery = true)
	List<ActiveProductRow> findActiveProductExternalIds(@Param("externalIds") Collection<UUID> externalIds);

	@Query(value = "SELECT external_id AS externalId, sku AS sku, name AS name FROM products WHERE external_id IN (:externalIds)", nativeQuery = true)
	List<ProductDescriptorRow> findProductDescriptors(@Param("externalIds") Collection<UUID> externalIds);

	@Query(value = "SELECT external_id AS externalId, name AS name FROM branches WHERE external_id IN (:externalIds)", nativeQuery = true)
	List<BranchDescriptorRow> findBranchDescriptors(@Param("externalIds") Collection<UUID> externalIds);

	@Query(value = "SELECT external_id AS externalId, username AS username FROM users WHERE external_id IN (:externalIds)", nativeQuery = true)
	List<UserDescriptorRow> findUserDescriptors(@Param("externalIds") Collection<UUID> externalIds);

	@Query(value = "SELECT external_id AS externalId, tax_id AS taxId, name AS name FROM suppliers WHERE external_id IN (:externalIds)", nativeQuery = true)
	List<SupplierDescriptorRow> findSupplierDescriptors(@Param("externalIds") Collection<UUID> externalIds);

	@Query(value = """
			SELECT p.external_id AS productExternalId,
			       pu.external_id AS unitOfMeasureExternalId,
			       pu.conversion_factor AS conversionFactor
			FROM product_units pu
			JOIN products p ON p.id = pu.product_id
			WHERE p.external_id IN (:productExternalIds) AND pu.external_id IN (:unitExternalIds)
			""", nativeQuery = true)
	List<ConversionFactorRow> findConversionFactors(@Param("productExternalIds") Collection<UUID> productExternalIds,
			@Param("unitExternalIds") Collection<UUID> unitExternalIds);

	@Query(value = "SELECT id AS id, external_id AS externalId FROM products WHERE id IN (:ids)", nativeQuery = true)
	List<IdExternalIdRow> findProductExternalIds(@Param("ids") Collection<Long> ids);

	@Query(value = "SELECT id AS id, external_id AS externalId FROM branches WHERE id IN (:ids)", nativeQuery = true)
	List<IdExternalIdRow> findBranchExternalIds(@Param("ids") Collection<Long> ids);

	@Query(value = "SELECT id AS id, external_id AS externalId FROM users WHERE id IN (:ids)", nativeQuery = true)
	List<IdExternalIdRow> findUserExternalIds(@Param("ids") Collection<Long> ids);

	@Query(value = "SELECT id AS id, external_id AS externalId FROM suppliers WHERE id IN (:ids)", nativeQuery = true)
	List<IdExternalIdRow> findSupplierExternalIds(@Param("ids") Collection<Long> ids);

	@Query(value = "SELECT id AS id, name AS name FROM branches WHERE id IN (:ids)", nativeQuery = true)
	List<IdNameRow> findBranchNamesByIds(@Param("ids") Collection<Long> ids);

	@Query(value = "SELECT id AS id, external_id AS externalId, name AS name, tax_id AS taxId FROM suppliers WHERE id IN (:ids)", nativeQuery = true)
	List<SupplierRefRow> findSupplierRefs(@Param("ids") Collection<Long> ids);

	interface IdExternalIdRow {
		Long getId();

		UUID getExternalId();
	}

	interface IdNameRow {
		Long getId();

		String getName();
	}

	interface SupplierRefRow {
		Long getId();

		UUID getExternalId();

		String getName();

		String getTaxId();
	}

	interface ActiveProductRow {
		UUID getExternalId();
	}

	interface ProductDescriptorRow {
		UUID getExternalId();

		String getSku();

		String getName();
	}

	interface BranchDescriptorRow {
		UUID getExternalId();

		String getName();
	}

	interface UserDescriptorRow {
		UUID getExternalId();

		String getUsername();
	}

	interface SupplierDescriptorRow {
		UUID getExternalId();

		String getTaxId();

		String getName();
	}

	interface ConversionFactorRow {
		UUID getProductExternalId();

		UUID getUnitOfMeasureExternalId();

		BigDecimal getConversionFactor();
	}
}
