package com.optiplant.inventory.purchases.domain;

import com.optiplant.inventory.purchases.domain.model.DiscountPercent;
import com.optiplant.inventory.purchases.domain.model.Money;
import com.optiplant.inventory.purchases.domain.model.OrderNumber;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrder;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderItem;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderNotes;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderStatus;
import com.optiplant.inventory.purchases.domain.model.PurchaseQuantity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Shared builders for {@code purchases} domain unit tests — no Spring, no Docker. */
public final class PurchaseOrderFixtures {

	public static final Instant NOW = Instant.parse("2026-08-30T10:00:00Z");
	public static final UUID BRANCH = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
	public static final UUID SUPPLIER = UUID.fromString("00000000-0000-0000-0000-0000000000d1");
	public static final UUID CREATED_BY = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

	private PurchaseOrderFixtures() {
	}

	public static PurchaseOrderItem item(UUID externalId, UUID productExternalId, String ordered, String received,
			String unitCost, String discountPercent) {
		return new PurchaseOrderItem(externalId, productExternalId, PurchaseQuantity.of(ordered),
				new BigDecimal(received), Money.of(unitCost), DiscountPercent.of(new BigDecimal(discountPercent)),
				Money.ZERO);
	}

	public static PurchaseOrder order(PurchaseOrderStatus status, PurchaseOrderItem... items) {
		return order(BRANCH, status, items);
	}

	public static PurchaseOrder order(UUID branchExternalId, PurchaseOrderStatus status, PurchaseOrderItem... items) {
		List<PurchaseOrderItem> itemList = Arrays.asList(items);
		return new PurchaseOrder(UUID.randomUUID(), OrderNumber.of("OC-2026-0001"), branchExternalId, SUPPLIER,
				CREATED_BY, status, null, Money.ZERO, PurchaseOrderNotes.empty(), null, NOW, NOW, itemList);
	}
}
