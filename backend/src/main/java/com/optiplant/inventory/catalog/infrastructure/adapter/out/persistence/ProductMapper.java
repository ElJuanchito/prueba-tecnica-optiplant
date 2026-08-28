package com.optiplant.inventory.catalog.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.catalog.domain.model.CategoryRef;
import com.optiplant.inventory.catalog.domain.model.Product;
import com.optiplant.inventory.catalog.domain.model.ProductSummary;
import com.optiplant.inventory.catalog.domain.model.ProductUnit;
import com.optiplant.inventory.catalog.domain.model.Sku;
import com.optiplant.inventory.catalog.domain.model.UnitCode;
import org.mapstruct.Mapper;

/**
 * Entity ↔ domain mapping for products, MapStruct with Spring component model
 * exactly as {@code iam}'s {@code BranchMapper}. The numeric {@code id} is never
 * carried into a domain type.
 *
 * <p>{@link #toDomain} rebuilds the full aggregate (units included); the
 * {@link Product} compact constructor re-asserts R-13/R-14 on the way out.
 * {@link #toSummary} produces the list projection, deliberately without
 * {@code units} and {@code description}.
 */
@Mapper(componentModel = "spring")
public interface ProductMapper {

	Product toDomain(ProductJpaEntity entity);

	ProductSummary toSummary(ProductJpaEntity entity);

	CategoryRef toCategoryRef(CategoryJpaEntity entity);

	ProductUnit toUnit(ProductUnitJpaEntity entity);

	default Sku toSku(String value) {
		return value == null ? null : new Sku(value);
	}

	default String fromSku(Sku sku) {
		return sku == null ? null : sku.value();
	}

	default UnitCode toUnitCode(String value) {
		return value == null ? null : new UnitCode(value);
	}

	default String fromUnitCode(UnitCode code) {
		return code == null ? null : code.value();
	}
}
