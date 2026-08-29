package com.optiplant.inventory.catalog.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.catalog.domain.model.Category;
import com.optiplant.inventory.catalog.domain.model.CategoryName;
import com.optiplant.inventory.catalog.domain.model.CategoryRef;
import org.mapstruct.Mapper;

/**
 * Entity ↔ domain mapping for categories, MapStruct with Spring component model
 * exactly as {@code iam}'s {@code BranchMapper}. The numeric {@code id} is never
 * carried into a domain type.
 */
@Mapper(componentModel = "spring")
public interface CategoryMapper {

	Category toDomain(CategoryJpaEntity entity);

	CategoryRef toRef(CategoryJpaEntity entity);

	default CategoryName toCategoryName(String value) {
		return value == null ? null : new CategoryName(value);
	}

	default String fromCategoryName(CategoryName name) {
		return name == null ? null : name.value();
	}
}
