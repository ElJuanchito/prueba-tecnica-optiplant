package com.optiplant.inventory.pricing.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.pricing.domain.model.DiscountCap;
import com.optiplant.inventory.pricing.domain.model.PriceList;
import com.optiplant.inventory.pricing.domain.model.PriceListCode;
import com.optiplant.inventory.pricing.domain.model.PriceListName;
import org.springframework.stereotype.Component;

/**
 * Entity &harr; domain mapping for {@link PriceListJpaEntity} and {@link PriceList} (design §6.1).
 */
@Component
public class PriceListMapper {

	public PriceList toDomain(PriceListJpaEntity entity) {
		return new PriceList(
				entity.getExternalId(),
				new PriceListCode(entity.getCode()),
				new PriceListName(entity.getName()),
				entity.getDescription(),
				new DiscountCap(entity.getMaxDiscountPercent()),
				entity.isDefault(),
				entity.isActive(),
				entity.getCreatedAt(),
				entity.getUpdatedAt()
		);
	}

	public PriceListJpaEntity toNewEntity(PriceList domain) {
		PriceListJpaEntity entity = new PriceListJpaEntity();
		entity.setExternalId(domain.externalId());
		entity.setCode(domain.code().value());
		entity.setName(domain.name().value());
		entity.setDescription(domain.description());
		entity.setMaxDiscountPercent(domain.maxDiscountPercent().value());
		entity.setDefault(domain.isDefault());
		entity.setActive(domain.active());
		entity.setCreatedAt(domain.createdAt());
		entity.setUpdatedAt(domain.updatedAt());
		return entity;
	}

	public void applyState(PriceListJpaEntity entity, PriceList domain) {
		entity.setName(domain.name().value());
		entity.setDescription(domain.description());
		entity.setMaxDiscountPercent(domain.maxDiscountPercent().value());
		entity.setActive(domain.active());
		entity.setUpdatedAt(domain.updatedAt());
	}
}
