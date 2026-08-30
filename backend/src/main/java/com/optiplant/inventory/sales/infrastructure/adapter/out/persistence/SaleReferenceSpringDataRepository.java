package com.optiplant.inventory.sales.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * Resolves references (branches, products, users, price lists, units) for sales (design §6.1, §6.2, §6.5).
 */
public interface SaleReferenceSpringDataRepository extends Repository<SaleJpaEntity, Long> {

	@Query(value = "SELECT id FROM products WHERE external_id = :externalId AND is_active = TRUE", nativeQuery = true)
	Optional<Long> findActiveProductIdByExternalId(@Param("externalId") UUID externalId);

	@Query(value = "SELECT id FROM branches WHERE external_id = :externalId AND is_active = TRUE", nativeQuery = true)
	Optional<Long> findActiveBranchIdByExternalId(@Param("externalId") UUID externalId);

	@Query(value = "SELECT id FROM users WHERE external_id = :externalId", nativeQuery = true)
	Optional<Long> findUserIdByExternalId(@Param("externalId") UUID externalId);

	@Query(value = "SELECT id FROM price_lists WHERE external_id = :externalId", nativeQuery = true)
	Optional<Long> findPriceListIdByExternalId(@Param("externalId") UUID externalId);

	@Query(value = "SELECT external_id AS externalId, sku AS sku, name AS name FROM products WHERE external_id IN (:externalIds)", nativeQuery = true)
	List<ProductDescriptorRow> findProductDescriptors(@Param("externalIds") Collection<UUID> externalIds);

	@Query(value = "SELECT external_id AS externalId, name AS name FROM branches WHERE external_id IN (:externalIds)", nativeQuery = true)
	List<BranchDescriptorRow> findBranchDescriptors(@Param("externalIds") Collection<UUID> externalIds);

	@Query(value = "SELECT external_id AS externalId, username AS username FROM users WHERE external_id IN (:externalIds)", nativeQuery = true)
	List<UserDescriptorRow> findUserDescriptors(@Param("externalIds") Collection<UUID> externalIds);

	@Query(value = """
			SELECT pu.conversion_factor
			FROM product_units pu
			JOIN products p ON p.id = pu.product_id
			WHERE p.external_id = :productExternalId AND pu.external_id = :unitOfMeasureExternalId
			""", nativeQuery = true)
	Optional<BigDecimal> findConversionFactor(@Param("productExternalId") UUID productExternalId,
			@Param("unitOfMeasureExternalId") UUID unitOfMeasureExternalId);

	@Query(value = """
			SELECT u.external_id AS externalId, u.username AS username, u.role AS role
			FROM users u
			WHERE u.external_id = :userExternalId AND u.is_active = TRUE
			""", nativeQuery = true)
	Optional<ServiceUserSubjectRow> findExternalCredentialSubject(@Param("userExternalId") UUID userExternalId);

	@Query(value = "SELECT id AS id, external_id AS externalId FROM products WHERE id IN (:ids)", nativeQuery = true)
	List<IdExternalIdRow> findProductExternalIds(@Param("ids") Collection<Long> ids);

	@Query(value = "SELECT id AS id, external_id AS externalId FROM branches WHERE id IN (:ids)", nativeQuery = true)
	List<IdExternalIdRow> findBranchExternalIds(@Param("ids") Collection<Long> ids);

	@Query(value = "SELECT id AS id, external_id AS externalId FROM users WHERE id IN (:ids)", nativeQuery = true)
	List<IdExternalIdRow> findUserExternalIds(@Param("ids") Collection<Long> ids);

	@Query(value = "SELECT id AS id, external_id AS externalId FROM price_lists WHERE id IN (:ids)", nativeQuery = true)
	List<IdExternalIdRow> findPriceListExternalIds(@Param("ids") Collection<Long> ids);

	@Query(value = "SELECT id AS id, external_id AS externalId FROM products WHERE external_id IN (:externalIds)", nativeQuery = true)
	List<IdExternalIdRow> findProductIds(@Param("externalIds") Collection<UUID> externalIds);

	@Query(value = "SELECT id AS id, name AS name FROM branches WHERE id IN (:ids)", nativeQuery = true)
	List<IdNameRow> findBranchNamesByIds(@Param("ids") Collection<Long> ids);

	@Query(value = "SELECT id AS id, username AS name FROM users WHERE id IN (:ids)", nativeQuery = true)
	List<IdNameRow> findUsernamesByIds(@Param("ids") Collection<Long> ids);

	@Query(value = "SELECT id AS id, code AS code, max_discount_percent AS maxDiscountPercent FROM price_lists WHERE id IN (:ids)", nativeQuery = true)
	List<PriceListSummaryRow> findPriceListSummariesByIds(@Param("ids") Collection<Long> ids);

	interface IdExternalIdRow {
		Long getId();

		UUID getExternalId();
	}

	interface IdNameRow {
		Long getId();

		String getName();
	}

	interface PriceListSummaryRow {
		Long getId();

		String getCode();

		BigDecimal getMaxDiscountPercent();
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

	interface ServiceUserSubjectRow {
		UUID getExternalId();

		String getUsername();

		String getRole();
	}
}
