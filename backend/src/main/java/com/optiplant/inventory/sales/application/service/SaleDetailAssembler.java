package com.optiplant.inventory.sales.application.service;

import com.optiplant.inventory.sales.application.port.out.SaleReferencePort;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort.BranchDescriptor;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort.CustomerDescriptor;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort.ProductDescriptor;
import com.optiplant.inventory.sales.application.port.out.SaleReferencePort.UserDescriptor;
import com.optiplant.inventory.sales.domain.model.BranchRef;
import com.optiplant.inventory.sales.domain.model.CustomerRef;
import com.optiplant.inventory.sales.domain.model.PriceListRef;
import com.optiplant.inventory.sales.domain.model.Sale;
import com.optiplant.inventory.sales.domain.model.SaleDetail;
import com.optiplant.inventory.sales.domain.model.SaleItem;
import com.optiplant.inventory.sales.domain.model.SaleItemView;
import com.optiplant.inventory.sales.domain.model.UserRef;
import com.optiplant.inventory.shared.price.AppliedPriceList;
import com.optiplant.inventory.shared.price.PriceResolutionPort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Enriches a {@link Sale} with descriptors from references and price lists (design §5, §6).
 */
final class SaleDetailAssembler {

	private SaleDetailAssembler() {
	}

	static SaleDetail toDetail(
			Sale sale,
			SaleReferencePort referencePort,
			PriceResolutionPort priceResolutionPort
	) {
		Map<UUID, BranchDescriptor> branches = referencePort.findBranches(Set.of(sale.branchExternalId()));
		Map<UUID, UserDescriptor> users = referencePort.findUsers(Set.of(sale.soldByUserExternalId()));
		Map<UUID, AppliedPriceList> priceLists = priceResolutionPort.describeLists(Set.of(sale.priceListExternalId()));

		Set<UUID> productIds = sale.items().stream()
				.map(SaleItem::productExternalId)
				.collect(Collectors.toSet());
		Map<UUID, ProductDescriptor> products = referencePort.findProducts(productIds);

		List<SaleItemView> itemViews = sale.items().stream()
				.map(item -> {
					ProductDescriptor prod = products.get(item.productExternalId());
					String sku = prod != null ? prod.sku() : null;
					String name = prod != null ? prod.name() : null;
					return new SaleItemView(
							item.externalId(),
							item.productExternalId(),
							sku,
							name,
							item.quantity().value(),
							item.listUnitPrice().value(),
							item.unitPrice().value(),
							item.discountPercent().value(),
							item.subtotal().value()
					);
				})
				.toList();

		BranchDescriptor branchDesc = branches.get(sale.branchExternalId());
		BranchRef branchRef = new BranchRef(
				sale.branchExternalId(),
				branchDesc != null ? branchDesc.name() : null
		);

		UserDescriptor userDesc = users.get(sale.soldByUserExternalId());
		UserRef userRef = new UserRef(
				sale.soldByUserExternalId(),
				userDesc != null ? userDesc.username() : null
		);

		AppliedPriceList appliedList = priceLists.get(sale.priceListExternalId());
		PriceListRef priceListRef = new PriceListRef(
				sale.priceListExternalId(),
				appliedList != null ? appliedList.code() : null,
				appliedList != null ? appliedList.maxDiscountPercent() : null
		);

		CustomerRef customerRef = null;
		if (sale.customerExternalId() != null) {
			Map<UUID, CustomerDescriptor> customers = referencePort.findCustomers(Set.of(sale.customerExternalId()));
			CustomerDescriptor custDesc = customers.get(sale.customerExternalId());
			if (custDesc != null) {
				customerRef = new CustomerRef(custDesc.externalId(), custDesc.name(), custDesc.taxId());
			}
		}

		String humanNotes = sale.notes() != null ? sale.notes().humanNote() : null;
		String cancellationReason = sale.notes() != null && sale.notes().cancellationReason() != null
				? sale.notes().cancellationReason().value()
				: null;

		return new SaleDetail(
				sale.externalId(),
				sale.invoiceNumber().value(),
				sale.status(),
				branchRef,
				userRef,
				priceListRef,
				customerRef,
				sale.customerName().value(),
				sale.customerTaxId() != null ? sale.customerTaxId().value() : null,
				sale.totals().subtotal().value(),
				sale.totals().discountAmount().value(),
				sale.totals().taxAmount().value(),
				sale.totals().totalAmount().value(),
				humanNotes,
				cancellationReason,
				sale.createdAt(),
				itemViews
		);
	}
}
