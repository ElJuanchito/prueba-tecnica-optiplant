package com.optiplant.inventory.pricing.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.pricing.domain.model.Price;
import com.optiplant.inventory.pricing.domain.model.UnitPrice;
import com.optiplant.inventory.pricing.domain.model.ValidityRange;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Entity &harr; domain mapping for {@link PriceListItemJpaEntity} and {@link Price} (design §6.1).
 */
@Component
public class PriceListItemMapper {

	public Price toDomain(PriceListItemJpaEntity entity, UUID priceListExternalId, UUID productExternalId,
			UUID branchExternalId) {
		return new Price(
				entity.getExternalId(),
				priceListExternalId,
				productExternalId,
				branchExternalId,
				new UnitPrice(entity.getUnitPrice()),
				new ValidityRange(entity.getValidFrom(), entity.getValidTo()),
				entity.getCreatedAt()
		);
	}

	public PriceListItemJpaEntity toNewEntity(Price domain, Long priceListId, Long productId, Long branchId) {
		PriceListItemJpaEntity entity = new PriceListItemJpaEntity();
		entity.setExternalId(domain.externalId());
		entity.setPriceListId(priceListId);
		entity.setProductId(productId);
		entity.setBranchId(branchId);
		entity.setUnitPrice(domain.unitPrice().value());
		entity.setValidFrom(domain.validity().from());
		entity.setValidTo(domain.validity().to());
		entity.setCreatedAt(domain.createdAt());
		entity.setUpdatedAt(domain.createdAt());
		return entity;
	}

	public void applyState(PriceListItemJpaEntity entity, Price domain) {
		entity.setValidTo(domain.validity().to());
		entity.setUpdatedAt(domain.validity().to() != null ? java.time.Instant.now() : entity.getUpdatedAt());
	}
}
