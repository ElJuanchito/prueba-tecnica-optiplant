package com.optiplant.inventory.pricing.infrastructure.adapter.out.price;

import com.optiplant.inventory.pricing.domain.model.Price;
import com.optiplant.inventory.pricing.domain.model.UnitPrice;
import com.optiplant.inventory.pricing.domain.model.ValidityRange;
import com.optiplant.inventory.pricing.domain.service.PriceResolutionPolicy;
import com.optiplant.inventory.pricing.infrastructure.adapter.out.persistence.PriceListItemJpaEntity;
import com.optiplant.inventory.pricing.infrastructure.adapter.out.persistence.PriceListItemSpringDataRepository;
import com.optiplant.inventory.pricing.infrastructure.adapter.out.persistence.PriceListJpaEntity;
import com.optiplant.inventory.pricing.infrastructure.adapter.out.persistence.PriceListSpringDataRepository;
import com.optiplant.inventory.pricing.infrastructure.adapter.out.persistence.PricingReferenceSpringDataRepository;
import com.optiplant.inventory.pricing.infrastructure.adapter.out.persistence.PricingReferenceSpringDataRepository.IdExternalIdRow;
import com.optiplant.inventory.shared.price.AppliedPriceList;
import com.optiplant.inventory.shared.price.PriceResolutionPort;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Implementation of {@link PriceResolutionPort} (design §2, §6.2, P-05).
 *
 * <p>Uses a superset query over {@code idx_price_list_items_lookup} so all candidate prices
 * for a basket are fetched in a single round trip (RNF-PER-02); the branch-over-corporate
 * preference is folded by {@link PriceResolutionPolicy} in the domain.
 */
@Component
public class PriceResolutionAdapter implements PriceResolutionPort {

	private final PriceListSpringDataRepository priceListRepository;
	private final PriceListItemSpringDataRepository priceListItemRepository;
	private final PricingReferenceSpringDataRepository referenceRepository;

	public PriceResolutionAdapter(PriceListSpringDataRepository priceListRepository,
			PriceListItemSpringDataRepository priceListItemRepository,
			PricingReferenceSpringDataRepository referenceRepository) {
		this.priceListRepository = priceListRepository;
		this.priceListItemRepository = priceListItemRepository;
		this.referenceRepository = referenceRepository;
	}

	@Override
	public Optional<AppliedPriceList> findActiveListByExternalId(UUID priceListExternalId) {
		return priceListRepository.findByExternalId(priceListExternalId)
				.filter(PriceListJpaEntity::isActive)
				.map(entity -> new AppliedPriceList(entity.getExternalId(), entity.getCode(), entity.getMaxDiscountPercent()));
	}

	@Override
	public Optional<AppliedPriceList> findActiveDefaultListForBranch(UUID branchExternalId) {
		return priceListRepository.findActiveDefaultListForBranch(branchExternalId)
				.map(entity -> new AppliedPriceList(entity.getExternalId(), entity.getCode(), entity.getMaxDiscountPercent()));
	}

	@Override
	public Map<UUID, AppliedPriceList> describeLists(Collection<UUID> priceListExternalIds) {
		if (priceListExternalIds == null || priceListExternalIds.isEmpty()) {
			return Map.of();
		}
		return priceListRepository.findByExternalIdIn(priceListExternalIds).stream()
				.collect(Collectors.toMap(
						PriceListJpaEntity::getExternalId,
						entity -> new AppliedPriceList(entity.getExternalId(), entity.getCode(), entity.getMaxDiscountPercent())
				));
	}

	@Override
	public Map<UUID, BigDecimal> resolveUnitPrices(UUID priceListExternalId, UUID branchExternalId,
			Collection<UUID> productExternalIds, LocalDate operationDate) {
		if (productExternalIds == null || productExternalIds.isEmpty() || operationDate == null) {
			return Map.of();
		}
		Long priceListId = referenceRepository.findPriceListIdByExternalId(priceListExternalId).orElse(null);
		if (priceListId == null) {
			return Map.of();
		}

		Long branchId = branchExternalId == null ? null
				: referenceRepository.findActiveBranchIdByExternalId(branchExternalId).orElse(null);

		List<IdExternalIdRow> productRows = referenceRepository.findProductIds(productExternalIds);
		if (productRows.isEmpty()) {
			return Map.of();
		}
		Map<Long, UUID> productExtMap = productRows.stream()
				.collect(Collectors.toMap(IdExternalIdRow::getId, IdExternalIdRow::getExternalId));
		List<Long> productIds = productRows.stream().map(IdExternalIdRow::getId).toList();

		List<PriceListItemJpaEntity> entities = priceListItemRepository.findEligible(priceListId, branchId, productIds,
				operationDate);

		List<Price> candidates = entities.stream().map(item -> new Price(
				item.getExternalId(),
				priceListExternalId,
				productExtMap.get(item.getProductId()),
				item.getBranchId() == null ? null : branchExternalId,
				new UnitPrice(item.getUnitPrice()),
				new ValidityRange(item.getValidFrom(), item.getValidTo()),
				item.getCreatedAt()
		)).toList();

		Map<UUID, Price> resolved = PriceResolutionPolicy.resolveAll(candidates, operationDate);
		Map<UUID, BigDecimal> result = new HashMap<>();
		for (Map.Entry<UUID, Price> entry : resolved.entrySet()) {
			result.put(entry.getKey(), entry.getValue().unitPrice().value());
		}
		return result;
	}
}
