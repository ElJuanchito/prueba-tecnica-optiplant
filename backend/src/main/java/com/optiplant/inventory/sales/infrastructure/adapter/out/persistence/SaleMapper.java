package com.optiplant.inventory.sales.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.sales.application.port.out.SaleRepositoryPort.NewSale;
import com.optiplant.inventory.sales.application.port.out.SaleRepositoryPort.NewSaleItem;
import com.optiplant.inventory.sales.domain.model.BranchRef;
import com.optiplant.inventory.sales.domain.model.CustomerName;
import com.optiplant.inventory.sales.domain.model.CustomerRef;
import com.optiplant.inventory.sales.domain.model.CustomerTaxId;
import com.optiplant.inventory.sales.domain.model.DiscountPercent;
import com.optiplant.inventory.sales.domain.model.InvoiceNumber;
import com.optiplant.inventory.sales.domain.model.Money;
import com.optiplant.inventory.sales.domain.model.PriceListRef;
import com.optiplant.inventory.sales.domain.model.Sale;
import com.optiplant.inventory.sales.domain.model.SaleItem;
import com.optiplant.inventory.sales.domain.model.SaleNotes;
import com.optiplant.inventory.sales.domain.model.SaleQuantity;
import com.optiplant.inventory.sales.domain.model.SaleStatus;
import com.optiplant.inventory.sales.domain.model.SaleSummary;
import com.optiplant.inventory.sales.domain.model.SaleTotals;
import com.optiplant.inventory.sales.domain.model.UserRef;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Entity &harr; domain mapping for {@code sales} and {@code sale_items} (design §6.1).
 * The sole reader and writer of the F-3 token ({@link SaleNotes#render()} /
 * {@link SaleNotes#parse(String)}).
 */
@Component
public class SaleMapper {

	public Sale toDomain(SaleJpaEntity entity, UUID branchExternalId, UUID soldByUserExternalId,
			UUID priceListExternalId, UUID customerExternalId, Map<Long, UUID> productExternalIdsByProductId) {
		SaleTotals totals = new SaleTotals(
				new Money(entity.getSubtotal()),
				new Money(entity.getDiscountAmount()),
				new Money(entity.getTaxAmount()),
				new Money(entity.getTotalAmount())
		);

		List<SaleItem> items = entity.getItems().stream()
				.map(item -> toDomainItem(item, productExternalIdsByProductId))
				.toList();

		return new Sale(
				entity.getExternalId(),
				new InvoiceNumber(entity.getInvoiceNumber()),
				SaleStatus.valueOf(entity.getStatus()),
				branchExternalId,
				soldByUserExternalId,
				priceListExternalId,
				customerExternalId,
				new CustomerName(entity.getCustomerName()),
				CustomerTaxId.of(entity.getCustomerTaxId()),
				totals,
				SaleNotes.parse(entity.getNotes()),
				entity.getCreatedAt(),
				items
		);
	}

	public SaleSummary toSummary(SaleJpaEntity entity, UUID branchExternalId, String branchName,
			UUID userExternalId, String username, UUID priceListExternalId, String priceListCode,
			java.math.BigDecimal maxDiscountPercent, CustomerRef customerRef) {
		return new SaleSummary(
				entity.getExternalId(),
				entity.getInvoiceNumber(),
				SaleStatus.valueOf(entity.getStatus()),
				new BranchRef(branchExternalId, branchName),
				new UserRef(userExternalId, username),
				new PriceListRef(priceListExternalId, priceListCode, maxDiscountPercent),
				customerRef,
				entity.getCustomerName(),
				entity.getTotalAmount(),
				entity.getCreatedAt()
		);
	}

	public SaleJpaEntity toNewEntity(NewSale newSale, String invoiceNumber, Long branchId, Long userId,
			Long priceListId, Long customerId, Map<UUID, Long> productIdsByExternalId, Instant now) {
		SaleJpaEntity entity = new SaleJpaEntity();
		entity.setInvoiceNumber(invoiceNumber);
		entity.setBranchId(branchId);
		entity.setUserId(userId);
		entity.setPriceListId(priceListId);
		entity.setCustomerId(customerId);
		entity.setCustomerName(newSale.customerName().value());
		entity.setCustomerTaxId(newSale.customerTaxId() != null ? newSale.customerTaxId().value() : null);
		entity.setStatus(SaleStatus.COMPLETED.name());
		entity.setSubtotal(newSale.totals().subtotal().value());
		entity.setDiscountAmount(newSale.totals().discountAmount().value());
		entity.setTaxAmount(newSale.totals().taxAmount().value());
		entity.setTotalAmount(newSale.totals().totalAmount().value());
		entity.setNotes(newSale.notes() != null ? newSale.notes().render() : null);
		entity.setCreatedAt(now);

		for (NewSaleItem item : newSale.items()) {
			SaleItemJpaEntity itemEntity = new SaleItemJpaEntity();
			itemEntity.setProductId(productIdsByExternalId.get(item.productExternalId()));
			itemEntity.setQuantity(item.quantity().value());
			itemEntity.setListUnitPrice(item.listUnitPrice().value());
			itemEntity.setUnitPrice(item.unitPrice().value());
			itemEntity.setDiscountPercent(item.discountPercent().value());
			itemEntity.setSubtotal(item.subtotal().value());
			entity.addItem(itemEntity);
		}
		return entity;
	}

	public void applyState(SaleJpaEntity entity, Sale sale) {
		entity.setStatus(sale.status().name());
		entity.setNotes(sale.notes() != null ? sale.notes().render() : null);
	}

	private SaleItem toDomainItem(SaleItemJpaEntity entity, Map<Long, UUID> productExternalIdsByProductId) {
		UUID productExternalId = productExternalIdsByProductId.get(entity.getProductId());
		return new SaleItem(
				entity.getExternalId(),
				productExternalId,
				new SaleQuantity(entity.getQuantity()),
				new Money(entity.getListUnitPrice()),
				new Money(entity.getUnitPrice()),
				new DiscountPercent(entity.getDiscountPercent()),
				new Money(entity.getSubtotal())
		);
	}
}
