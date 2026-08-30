package com.optiplant.inventory.sales.application.port.out;

import com.optiplant.inventory.sales.domain.model.CustomerName;
import com.optiplant.inventory.sales.domain.model.CustomerTaxId;
import com.optiplant.inventory.sales.domain.model.DiscountPercent;
import com.optiplant.inventory.sales.domain.model.InvoiceNumber;
import com.optiplant.inventory.sales.domain.model.Money;
import com.optiplant.inventory.sales.domain.model.Sale;
import com.optiplant.inventory.sales.domain.model.SaleNotes;
import com.optiplant.inventory.sales.domain.model.SalePage;
import com.optiplant.inventory.sales.domain.model.SaleQuantity;
import com.optiplant.inventory.sales.domain.model.SaleStatus;
import com.optiplant.inventory.sales.domain.model.SaleTotals;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Secondary port for sales persistence (design §5).
 */
public interface SaleRepositoryPort {

	/**
	 * Allocates an internal invoice number (if not provided) and creates a new sale with items.
	 */
	Sale create(NewSale newSale);

	/**
	 * Acquires a pessimistic write lock on the sale (T-02, F-7).
	 */
	Optional<Sale> lockForUpdate(UUID externalId);

	/**
	 * Reads a sale by external ID without locking (T-05, RN-09).
	 */
	Optional<Sale> findByExternalId(UUID externalId);

	/**
	 * Reads a sale by invoice number without locking (T-05, RN-09).
	 */
	Optional<Sale> findByInvoiceNumber(String invoiceNumber);

	/**
	 * Saves state changes on an existing sale (e.g. status transition on void).
	 */
	Sale save(Sale sale);

	/**
	 * Returns a paginated listing of sales summaries with totals aggregate (R-24).
	 */
	SalePage list(SaleFilter filter);

	record NewSale(
			InvoiceNumber invoiceNumber,
			UUID branchExternalId,
			UUID soldByUserExternalId,
			UUID priceListExternalId,
			UUID customerExternalId,
			CustomerName customerName,
			CustomerTaxId customerTaxId,
			SaleTotals totals,
			SaleNotes notes,
			List<NewSaleItem> items
	) {
	}

	record NewSaleItem(
			UUID productExternalId,
			SaleQuantity quantity,
			Money listUnitPrice,
			Money unitPrice,
			DiscountPercent discountPercent,
			Money subtotal
	) {
	}

	record SaleFilter(
			UUID callerBranchExternalId,
			UUID customerExternalId,
			SaleStatus status,
			Instant from,
			Instant to,
			String sort,
			int page,
			int size
	) {
	}
}
