package com.optiplant.inventory.purchases.domain.service;

import static com.optiplant.inventory.purchases.domain.PurchaseOrderFixtures.item;
import static com.optiplant.inventory.purchases.domain.PurchaseOrderFixtures.order;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.purchases.domain.exception.InvalidOrderQuantityException;
import com.optiplant.inventory.purchases.domain.exception.OverReceiptNotAuthorizedException;
import com.optiplant.inventory.purchases.domain.exception.PurchaseOrderItemNotFoundException;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrder;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderItem;
import com.optiplant.inventory.purchases.domain.model.PurchaseOrderStatus;
import com.optiplant.inventory.purchases.domain.service.PurchaseReceptionPolicy.ReceptionLine;
import com.optiplant.inventory.purchases.domain.service.PurchaseReceptionPolicy.ReceptionLineCommand;
import com.optiplant.inventory.purchases.domain.service.PurchaseReceptionPolicy.ReceptionPlan;
import com.optiplant.inventory.shared.security.Role;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PurchaseReceptionPolicyTest {

	private static final UUID P1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID P2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID ITEM_1 = UUID.fromString("00000000-0000-0000-0000-00000000a001");
	private static final UUID ITEM_2 = UUID.fromString("00000000-0000-0000-0000-00000000a002");

	private static ReceptionLineCommand line(UUID itemId, String qty) {
		return new ReceptionLineCommand(itemId, new BigDecimal(qty));
	}

	@Test
	@DisplayName("R-22: an empty reception is refused with invalid_request")
	void emptyReceptionRefused() {
		PurchaseOrder order = order(PurchaseOrderStatus.APPROVED,
				item(ITEM_1, P1, "100", "0", "10", "0"));

		assertThatThrownBy(() -> PurchaseReceptionPolicy.plan(order, List.of(), Role.BRANCH_MANAGER))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("R-22: an all-zero reception is refused with invalid_request")
	void allZeroReceptionRefused() {
		PurchaseOrder order = order(PurchaseOrderStatus.APPROVED,
				item(ITEM_1, P1, "100", "0", "10", "0"),
				item(ITEM_2, P2, "100", "0", "10", "0"));

		assertThatThrownBy(() -> PurchaseReceptionPolicy.plan(order,
				List.of(line(ITEM_1, "0"), line(ITEM_2, "0")), Role.BRANCH_MANAGER))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("R-22: a zero line writes no Kardex row — it is dropped from the plan")
	void zeroLineDroppedFromPlan() {
		PurchaseOrder order = order(PurchaseOrderStatus.APPROVED,
				item(ITEM_1, P1, "100", "0", "10", "0"),
				item(ITEM_2, P2, "100", "0", "10", "0"));

		ReceptionPlan plan = PurchaseReceptionPolicy.plan(order,
				List.of(line(ITEM_1, "0"), line(ITEM_2, "10")), Role.BRANCH_MANAGER);

		assertThat(plan.lines()).extracting(ReceptionLine::itemExternalId).containsExactly(ITEM_2);
	}

	@Test
	@DisplayName("R-16: a negative received quantity is always refused")
	void negativeQuantityRefused() {
		PurchaseOrder order = order(PurchaseOrderStatus.APPROVED,
				item(ITEM_1, P1, "100", "0", "10", "0"));

		assertThatThrownBy(() -> PurchaseReceptionPolicy.plan(order, List.of(line(ITEM_1, "-1")), Role.ADMIN))
				.isInstanceOf(InvalidOrderQuantityException.class);
	}

	@Test
	@DisplayName("R-16: over-receipt is refused for OPERATOR, accepted and recorded for managers")
	void overReceiptGatedByRole() {
		PurchaseOrderItem it = item(ITEM_1, P1, "100", "0", "10", "0");

		assertThatThrownBy(() -> PurchaseReceptionPolicy.plan(order(PurchaseOrderStatus.APPROVED, it),
				List.of(line(ITEM_1, "150")), Role.OPERATOR))
				.isInstanceOf(OverReceiptNotAuthorizedException.class);

		for (Role role : new Role[] { Role.BRANCH_MANAGER, Role.ADMIN }) {
			ReceptionPlan plan = PurchaseReceptionPolicy.plan(order(PurchaseOrderStatus.APPROVED, it),
					List.of(line(ITEM_1, "150")), role);
			assertThat(plan.excesses()).containsKey(ITEM_1);
			assertThat(plan.excesses().get(ITEM_1)).isEqualByComparingTo("50");
		}
	}

	@Test
	@DisplayName("R-16: receiving exactly the pending balance is not an over-receipt")
	void exactPendingIsNotOverReceipt() {
		PurchaseOrder order = order(PurchaseOrderStatus.APPROVED, item(ITEM_1, P1, "100", "40", "10", "0"));

		ReceptionPlan plan = PurchaseReceptionPolicy.plan(order, List.of(line(ITEM_1, "60")), Role.OPERATOR);

		assertThat(plan.excesses()).isEmpty();
		assertThat(plan.targetStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
	}

	@Test
	@DisplayName("R-17: the plan line carries the discount-adjusted effective unit cost")
	void effectiveUnitCostIsDiscountAdjusted() {
		PurchaseOrder order = order(PurchaseOrderStatus.APPROVED, item(ITEM_1, P1, "100", "0", "10", "20"));

		ReceptionPlan plan = PurchaseReceptionPolicy.plan(order, List.of(line(ITEM_1, "10")), Role.ADMIN);

		assertThat(plan.lines()).singleElement()
				.satisfies(l -> assertThat(l.effectiveUnitCost().value()).isEqualByComparingTo("8.0000"));
	}

	@Test
	@DisplayName("R-19: partial completion yields PARTIALLY_RECEIVED, full completion yields RECEIVED")
	void partialVersusTotalCompletion() {
		PurchaseOrder ordered100 = order(PurchaseOrderStatus.APPROVED, item(ITEM_1, P1, "100", "0", "10", "0"));
		assertThat(PurchaseReceptionPolicy.plan(ordered100, List.of(line(ITEM_1, "60")), Role.ADMIN).targetStatus())
				.isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);

		PurchaseOrder ordered100partial = order(PurchaseOrderStatus.PARTIALLY_RECEIVED,
				item(ITEM_1, P1, "100", "60", "10", "0"));
		assertThat(PurchaseReceptionPolicy.plan(ordered100partial, List.of(line(ITEM_1, "40")), Role.ADMIN)
				.targetStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
	}

	@Test
	@DisplayName("R-19: an unnamed line still counts toward the completion test")
	void unnamedLineCountsTowardCompletion() {
		PurchaseOrder order = order(PurchaseOrderStatus.APPROVED,
				item(ITEM_1, P1, "100", "0", "10", "0"),
				item(ITEM_2, P2, "50", "0", "10", "0"));

		ReceptionPlan plan = PurchaseReceptionPolicy.plan(order, List.of(line(ITEM_1, "100")), Role.ADMIN);

		assertThat(plan.targetStatus()).isEqualTo(PurchaseOrderStatus.PARTIALLY_RECEIVED);
	}

	@Test
	@DisplayName("T-02: plan lines are sorted ascending by product external_id")
	void planLinesSortedByProduct() {
		PurchaseOrder order = order(PurchaseOrderStatus.APPROVED,
				item(ITEM_2, P2, "100", "0", "10", "0"),
				item(ITEM_1, P1, "100", "0", "10", "0"));

		ReceptionPlan plan = PurchaseReceptionPolicy.plan(order,
				List.of(line(ITEM_2, "5"), line(ITEM_1, "5")), Role.ADMIN);

		assertThat(plan.lines()).extracting(ReceptionLine::productExternalId).containsExactly(P1, P2);
	}

	@Test
	@DisplayName("design §5 step 4: a reception line outside the order is not found")
	void unknownItemRefused() {
		PurchaseOrder order = order(PurchaseOrderStatus.APPROVED, item(ITEM_1, P1, "100", "0", "10", "0"));

		assertThatThrownBy(() -> PurchaseReceptionPolicy.plan(order,
				List.of(line(UUID.randomUUID(), "5")), Role.ADMIN))
				.isInstanceOf(PurchaseOrderItemNotFoundException.class);
	}
}
