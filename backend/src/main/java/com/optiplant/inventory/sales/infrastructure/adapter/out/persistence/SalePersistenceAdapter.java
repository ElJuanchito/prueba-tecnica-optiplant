package com.optiplant.inventory.sales.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.sales.application.port.out.SaleRepositoryPort;
import com.optiplant.inventory.sales.domain.exception.DuplicateInvoiceNumberException;
import com.optiplant.inventory.sales.domain.exception.ProductNotFoundException;
import com.optiplant.inventory.sales.domain.model.CustomerRef;
import com.optiplant.inventory.sales.domain.model.Sale;
import com.optiplant.inventory.sales.domain.model.SaleAggregates;
import com.optiplant.inventory.sales.domain.model.SalePage;
import com.optiplant.inventory.sales.domain.model.SaleSummary;
import com.optiplant.inventory.sales.infrastructure.adapter.out.persistence.SaleReferenceSpringDataRepository.CustomerRefRow;
import com.optiplant.inventory.sales.infrastructure.adapter.out.persistence.SaleReferenceSpringDataRepository.IdExternalIdRow;
import com.optiplant.inventory.sales.infrastructure.adapter.out.persistence.SaleReferenceSpringDataRepository.IdNameRow;
import com.optiplant.inventory.sales.infrastructure.adapter.out.persistence.SaleReferenceSpringDataRepository.PriceListSummaryRow;
import com.optiplant.inventory.sales.infrastructure.adapter.out.persistence.SaleSpringDataRepository.SaleAggregatesRow;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.Year;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * Implementation of {@link SaleRepositoryPort} (design §5, §6.1, §6.3, §7).
 */
@Component
public class SalePersistenceAdapter implements SaleRepositoryPort {

	private final SaleSpringDataRepository saleRepository;
	private final SaleReferenceSpringDataRepository referenceRepository;
	private final SaleMapper mapper;

	public SalePersistenceAdapter(SaleSpringDataRepository saleRepository,
			SaleReferenceSpringDataRepository referenceRepository, SaleMapper mapper) {
		this.saleRepository = saleRepository;
		this.referenceRepository = referenceRepository;
		this.mapper = mapper;
	}

	@Override
	public Sale create(NewSale newSale) {
		Long branchId = requireBranchId(newSale.branchExternalId());
		Long userId = requireUserId(newSale.soldByUserExternalId());
		Long priceListId = requirePriceListId(newSale.priceListExternalId());
		Long customerId = newSale.customerExternalId() != null
				? referenceRepository.findCustomerIdByExternalId(newSale.customerExternalId())
						.orElseThrow(() -> new IllegalStateException("No customer found for external id " + newSale.customerExternalId()))
				: null;

		Map<UUID, Long> productIdsByExternalId = new HashMap<>();
		for (NewSaleItem item : newSale.items()) {
			productIdsByExternalId.computeIfAbsent(item.productExternalId(), this::requireProductId);
		}

		String invoiceNumber;
		if (newSale.invoiceNumber() != null) {
			String suppliedNumber = newSale.invoiceNumber().value();
			if (saleRepository.findByInvoiceNumber(suppliedNumber).isPresent()) {
				throw new DuplicateInvoiceNumberException(suppliedNumber);
			}
			invoiceNumber = suppliedNumber;
		} else {
			int year = Year.now().getValue();
			saleRepository.allocateAdvisoryLock("sale_invoice_number:" + year);
			int sequence = saleRepository.nextSequenceNumber("VEN-" + year + "-%");
			invoiceNumber = "VEN-%d-%04d".formatted(year, sequence);
		}

		Instant now = Instant.now();
		SaleJpaEntity entity = mapper.toNewEntity(newSale, invoiceNumber, branchId, userId, priceListId,
				customerId, productIdsByExternalId, now);
		SaleJpaEntity saved = saleRepository.save(entity);

		Map<Long, UUID> productExternalIdsByProductId = new HashMap<>();
		productIdsByExternalId.forEach((extId, id) -> productExternalIdsByProductId.put(id, extId));

		return mapper.toDomain(saved, newSale.branchExternalId(), newSale.soldByUserExternalId(),
				newSale.priceListExternalId(), newSale.customerExternalId(), productExternalIdsByProductId);
	}

	@Override
	public Optional<Sale> lockForUpdate(UUID externalId) {
		return saleRepository.findByExternalId(externalId).map(this::toDomain);
	}

	@Override
	public Optional<Sale> findByExternalId(UUID externalId) {
		return saleRepository.findDetailByExternalId(externalId).map(this::toDomain);
	}

	@Override
	public Optional<Sale> findByInvoiceNumber(String invoiceNumber) {
		return saleRepository.findByInvoiceNumber(invoiceNumber).map(this::toDomain);
	}

	@Override
	public Sale save(Sale sale) {
		SaleJpaEntity entity = saleRepository.findDetailByExternalId(sale.externalId())
				.orElseThrow(() -> new IllegalStateException("No sale found for external id " + sale.externalId()));

		mapper.applyState(entity, sale);
		saleRepository.save(entity);
		return sale;
	}

	@Override
	public SalePage list(SaleFilter filter) {
		Long branchId = filter.callerBranchExternalId() == null ? null
				: resolveBranchIdOrSentinel(filter.callerBranchExternalId());
		Long customerId = filter.customerExternalId() == null ? null
				: resolveCustomerIdOrSentinel(filter.customerExternalId());
		String status = filter.status() == null ? null : filter.status().name();

		PageRequest pageRequest = PageRequest.of(filter.page(), filter.size());
		Page<SaleJpaEntity> page = "totalAmount".equals(filter.sort())
				? saleRepository.searchOrderByTotalAmount(branchId, customerId, status, filter.from(), filter.to(), pageRequest)
				: saleRepository.searchOrderByCreatedAt(branchId, customerId, status, filter.from(), filter.to(), pageRequest);

		SaleAggregatesRow aggRow = saleRepository.computeAggregates(branchId, customerId, status, filter.from(), filter.to());
		SaleAggregates aggregates = new SaleAggregates(
				aggRow.getSalesCount() == null ? 0L : aggRow.getSalesCount(),
				aggRow.getTotalAmount() == null ? BigDecimal.ZERO : aggRow.getTotalAmount()
		);

		Set<Long> branchIds = new HashSet<>();
		Set<Long> userIds = new HashSet<>();
		Set<Long> priceListIds = new HashSet<>();
		Set<Long> customerIds = new HashSet<>();
		for (SaleJpaEntity entity : page.getContent()) {
			branchIds.add(entity.getBranchId());
			userIds.add(entity.getUserId());
			priceListIds.add(entity.getPriceListId());
			if (entity.getCustomerId() != null) {
				customerIds.add(entity.getCustomerId());
			}
		}

		Map<Long, UUID> branchExtIds = resolveExternalIds(branchIds, referenceRepository::findBranchExternalIds);
		Map<Long, String> branchNames = resolveNames(branchIds, referenceRepository::findBranchNamesByIds);

		Map<Long, UUID> userExtIds = resolveExternalIds(userIds, referenceRepository::findUserExternalIds);
		Map<Long, String> userNames = resolveNames(userIds, referenceRepository::findUsernamesByIds);

		Map<Long, UUID> priceListExtIds = resolveExternalIds(priceListIds, referenceRepository::findPriceListExternalIds);
		Map<Long, PriceListSummaryRow> priceListSummaries = resolvePriceListSummaries(priceListIds);
		Map<Long, CustomerRef> customerRefs = resolveCustomerRefs(customerIds);

		List<SaleSummary> content = page.getContent().stream().map(entity -> {
			UUID bExt = branchExtIds.get(entity.getBranchId());
			String bName = branchNames.get(entity.getBranchId());
			UUID uExt = userExtIds.get(entity.getUserId());
			String uName = userNames.get(entity.getUserId());
			UUID plExt = priceListExtIds.get(entity.getPriceListId());
			PriceListSummaryRow plRow = priceListSummaries.get(entity.getPriceListId());
			String plCode = plRow != null ? plRow.getCode() : null;
			BigDecimal plCap = plRow != null ? plRow.getMaxDiscountPercent() : null;
			CustomerRef custRef = entity.getCustomerId() != null ? customerRefs.get(entity.getCustomerId()) : null;

			return mapper.toSummary(entity, bExt, bName, uExt, uName, plExt, plCode, plCap, custRef);
		}).toList();

		return new SalePage(content, page.getTotalElements(), filter.page(), filter.size(), aggregates);
	}

	private Sale toDomain(SaleJpaEntity entity) {
		Map<Long, UUID> branchExtIds = resolveExternalIds(Set.of(entity.getBranchId()),
				referenceRepository::findBranchExternalIds);
		Map<Long, UUID> userExtIds = resolveExternalIds(Set.of(entity.getUserId()),
				referenceRepository::findUserExternalIds);
		Map<Long, UUID> priceListExtIds = resolveExternalIds(Set.of(entity.getPriceListId()),
				referenceRepository::findPriceListExternalIds);
		UUID custExtId = entity.getCustomerId() == null ? null
				: referenceRepository.findCustomerRefs(Set.of(entity.getCustomerId())).stream().findFirst()
						.map(CustomerRefRow::getExternalId).orElse(null);

		Set<Long> productIds = entity.getItems().stream()
				.map(SaleItemJpaEntity::getProductId)
				.collect(Collectors.toSet());
		Map<Long, UUID> productExtIds = resolveExternalIds(productIds, referenceRepository::findProductExternalIds);

		return mapper.toDomain(entity, branchExtIds.get(entity.getBranchId()), userExtIds.get(entity.getUserId()),
				priceListExtIds.get(entity.getPriceListId()), custExtId, productExtIds);
	}

	private Map<Long, CustomerRef> resolveCustomerRefs(Collection<Long> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<Long, CustomerRef> result = new HashMap<>();
		for (CustomerRefRow row : referenceRepository.findCustomerRefs(List.copyOf(ids))) {
			result.put(row.getId(), new CustomerRef(row.getExternalId(), row.getName(), row.getTaxId()));
		}
		return result;
	}

	private Map<Long, UUID> resolveExternalIds(Collection<Long> ids,
			Function<List<Long>, List<IdExternalIdRow>> lookup) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<Long, UUID> result = new HashMap<>();
		for (IdExternalIdRow row : lookup.apply(List.copyOf(ids))) {
			result.put(row.getId(), row.getExternalId());
		}
		return result;
	}

	private Map<Long, String> resolveNames(Collection<Long> ids,
			Function<List<Long>, List<IdNameRow>> lookup) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<Long, String> result = new HashMap<>();
		for (IdNameRow row : lookup.apply(List.copyOf(ids))) {
			result.put(row.getId(), row.getName());
		}
		return result;
	}

	private Map<Long, PriceListSummaryRow> resolvePriceListSummaries(Collection<Long> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<Long, PriceListSummaryRow> result = new HashMap<>();
		for (PriceListSummaryRow row : referenceRepository.findPriceListSummariesByIds(List.copyOf(ids))) {
			result.put(row.getId(), row);
		}
		return result;
	}

	private Long requireBranchId(UUID externalId) {
		return referenceRepository.findActiveBranchIdByExternalId(externalId)
				.orElseThrow(() -> new IllegalStateException("No active branch found for external id " + externalId));
	}

	private Long requireUserId(UUID externalId) {
		return referenceRepository.findUserIdByExternalId(externalId)
				.orElseThrow(() -> new IllegalStateException("No user found for external id " + externalId));
	}

	private Long requirePriceListId(UUID externalId) {
		return referenceRepository.findPriceListIdByExternalId(externalId)
				.orElseThrow(() -> new IllegalStateException("No price list found for external id " + externalId));
	}

	private Long requireProductId(UUID externalId) {
		return referenceRepository.findActiveProductIdByExternalId(externalId)
				.orElseThrow(() -> new ProductNotFoundException(externalId));
	}

	private Long resolveBranchIdOrSentinel(UUID externalId) {
		return referenceRepository.findActiveBranchIdByExternalId(externalId).orElse(-1L);
	}

	private Long resolveCustomerIdOrSentinel(UUID externalId) {
		return referenceRepository.findCustomerIdByExternalId(externalId).orElse(-1L);
	}
}
