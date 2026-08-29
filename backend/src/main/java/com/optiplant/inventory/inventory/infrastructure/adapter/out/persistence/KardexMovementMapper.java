package com.optiplant.inventory.inventory.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.inventory.domain.model.KardexLine;
import com.optiplant.inventory.inventory.domain.model.KardexMovement;
import com.optiplant.inventory.inventory.domain.model.Quantity;
import com.optiplant.inventory.inventory.domain.model.UnitCost;
import java.math.BigDecimal;
import java.util.UUID;
import org.mapstruct.Mapper;

/**
 * Entity ↔ domain mapping for {@code kardex_movements} (design §6.1). {@link #toDomain} backs
 * {@code KardexPersistenceAdapter#append}; {@link #toLine} backs the read-side {@code list}
 * query, which never needs the full {@link KardexMovement} aggregate.
 */
@Mapper(componentModel = "spring")
public interface KardexMovementMapper {

	KardexMovement toDomain(KardexMovementJpaEntity entity, UUID branchExternalId, UUID productExternalId,
			UUID userExternalId);

	KardexLine toLine(KardexMovementJpaEntity entity, UUID productExternalId, UUID userExternalId);

	default Quantity toQuantity(BigDecimal value) {
		return value == null ? null : new Quantity(value);
	}

	default BigDecimal fromQuantity(Quantity quantity) {
		return quantity == null ? null : quantity.value();
	}

	default UnitCost toUnitCost(BigDecimal value) {
		return value == null ? null : new UnitCost(value);
	}

	default BigDecimal fromUnitCost(UnitCost cost) {
		return cost == null ? null : cost.value();
	}
}
