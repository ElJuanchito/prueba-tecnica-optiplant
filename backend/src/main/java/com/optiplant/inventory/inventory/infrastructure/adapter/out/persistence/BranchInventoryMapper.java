package com.optiplant.inventory.inventory.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.inventory.domain.model.BranchInventory;
import com.optiplant.inventory.inventory.domain.model.StockLevel;
import com.optiplant.inventory.inventory.domain.model.UnitCost;
import java.math.BigDecimal;
import java.util.UUID;
import org.mapstruct.Mapper;

/**
 * Entity ↔ domain mapping for {@code branch_inventories} (design §6.1), MapStruct with Spring
 * component model exactly as {@code catalog}'s {@code ProductMapper}. The externally resolved
 * {@code branchExternalId}/{@code productExternalId} are supplied as extra source parameters —
 * the entity itself only carries the plain {@code Long} foreign keys.
 */
@Mapper(componentModel = "spring")
public interface BranchInventoryMapper {

	BranchInventory toDomain(BranchInventoryJpaEntity entity, UUID branchExternalId, UUID productExternalId);

	default StockLevel toStockLevel(BigDecimal value) {
		return value == null ? null : new StockLevel(value);
	}

	default BigDecimal fromStockLevel(StockLevel level) {
		return level == null ? null : level.value();
	}

	default UnitCost toUnitCost(BigDecimal value) {
		return value == null ? null : new UnitCost(value);
	}

	default BigDecimal fromUnitCost(UnitCost cost) {
		return cost == null ? null : cost.value();
	}
}
