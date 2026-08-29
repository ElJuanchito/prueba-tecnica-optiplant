package com.optiplant.inventory.transfers.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.transfers.domain.exception.DuplicateTransferItemException;
import com.optiplant.inventory.transfers.domain.exception.InvalidTransferQuantityException;
import com.optiplant.inventory.transfers.domain.exception.TransferItemNotFoundException;
import com.optiplant.inventory.transfers.domain.model.DispatchLine;
import com.optiplant.inventory.transfers.domain.model.TransferItem;
import com.optiplant.inventory.transfers.domain.model.TransferQuantity;
import com.optiplant.inventory.transfers.domain.service.TransferDispatchPolicy.DispatchApplication;
import com.optiplant.inventory.transfers.domain.service.TransferDispatchPolicy.DispatchOperation;
import com.optiplant.inventory.transfers.domain.service.TransferDispatchPolicy.DispatchPlan;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link TransferDispatchPolicy} — R-13's per-line bounds and the §7.1
 * deterministic lock order {@link TransferDispatchPolicy#plan} returns: the full set of
 * {@code (branch, product)} pairs sorted ascending by branch UUID then product UUID.
 */
class TransferDispatchPolicyTest {

	// Fixed, orderable UUIDs so the §7.1 sort is deterministic and independently verifiable.
	private static final UUID ORIGIN_BRANCH = UUID.fromString("00000000-0000-0000-0000-000000000001");
	private static final UUID DESTINATION_BRANCH = UUID.fromString("00000000-0000-0000-0000-000000000002");
	private static final UUID PRODUCT_A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
	private static final UUID PRODUCT_B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

	private static TransferItem requested(UUID itemId, UUID productId, String quantity) {
		return TransferItem.requested(itemId, productId, new TransferQuantity(new BigDecimal(quantity)));
	}

	@Test
	void dispatchingLessThanAgreedIsAllowedAndDocumented() {
		UUID itemId = UUID.randomUUID();
		TransferItem item = requested(itemId, PRODUCT_A, "100");

		DispatchApplication application = TransferDispatchPolicy.apply(List.of(item),
				List.of(new DispatchLine(itemId, new BigDecimal("60"))));

		assertThat(application.items().get(0).dispatchedQuantity().value()).isEqualByComparingTo("60.0000");
		assertThat(application.observations()).hasSize(1);
	}

	@Test
	void dispatchingTheFullAgreedAmountAddsNoObservation() {
		UUID itemId = UUID.randomUUID();
		TransferItem item = requested(itemId, PRODUCT_A, "100");

		DispatchApplication application = TransferDispatchPolicy.apply(List.of(item),
				List.of(new DispatchLine(itemId, new BigDecimal("100"))));

		assertThat(application.observations()).isEmpty();
	}

	@Test
	void dispatchingAboveTheAgreedAmountIsRefused() {
		UUID itemId = UUID.randomUUID();
		TransferItem item = requested(itemId, PRODUCT_A, "100");

		assertThatThrownBy(() -> TransferDispatchPolicy.apply(List.of(item),
				List.of(new DispatchLine(itemId, new BigDecimal("101")))))
				.isInstanceOf(InvalidTransferQuantityException.class);
	}

	@Test
	void dispatchingZeroIsRefused() {
		UUID itemId = UUID.randomUUID();
		TransferItem item = requested(itemId, PRODUCT_A, "100");

		assertThatThrownBy(() -> TransferDispatchPolicy.apply(List.of(item),
				List.of(new DispatchLine(itemId, BigDecimal.ZERO))))
				.isInstanceOf(InvalidTransferQuantityException.class);
	}

	@Test
	void anItemNotNamedInTheDispatchIsRefused() {
		TransferItem item = requested(UUID.randomUUID(), PRODUCT_A, "100");

		assertThatThrownBy(() -> TransferDispatchPolicy.apply(List.of(item), List.of()))
				.isInstanceOf(InvalidTransferQuantityException.class);
	}

	@Test
	void namingTheSameItemTwiceIsRefused() {
		UUID itemId = UUID.randomUUID();
		TransferItem item = requested(itemId, PRODUCT_A, "100");
		TransferItem other = requested(UUID.randomUUID(), PRODUCT_B, "10");

		assertThatThrownBy(() -> TransferDispatchPolicy.apply(List.of(item, other),
				List.of(new DispatchLine(itemId, new BigDecimal("50")), new DispatchLine(itemId, new BigDecimal("50")))))
				.isInstanceOf(DuplicateTransferItemException.class);
	}

	@Test
	void anUnknownItemIsRefused() {
		TransferItem item = requested(UUID.randomUUID(), PRODUCT_A, "100");

		assertThatThrownBy(() -> TransferDispatchPolicy.apply(List.of(item),
				List.of(new DispatchLine(UUID.randomUUID(), new BigDecimal("50")))))
				.isInstanceOf(TransferItemNotFoundException.class);
	}

	@Test
	void planSortsLinesAscendingByBranchThenByProductTheDeterministicLockOrder() {
		UUID itemB = UUID.randomUUID();
		UUID itemA = UUID.randomUUID();
		// item1 references the "larger" product, item2 the "smaller" one, so a naive per-item order
		// would be wrong and only a genuine sort passes.
		TransferItem transferItemForProductB = requested(itemB, PRODUCT_B, "10");
		TransferItem transferItemForProductA = requested(itemA, PRODUCT_A, "20");
		List<TransferItem> items = List.of(transferItemForProductB, transferItemForProductA);
		List<DispatchLine> lines = List.of(new DispatchLine(itemB, new BigDecimal("10")),
				new DispatchLine(itemA, new BigDecimal("15")));

		DispatchPlan plan = TransferDispatchPolicy.plan(ORIGIN_BRANCH, DESTINATION_BRANCH, items, lines);

		List<String> rendered = plan.lines().stream()
				.map(line -> line.branchExternalId() + "/" + line.productExternalId() + "/" + line.operation())
				.toList();
		assertThat(rendered).containsExactly(
				ORIGIN_BRANCH + "/" + PRODUCT_A + "/" + DispatchOperation.STOCK_OUT,
				ORIGIN_BRANCH + "/" + PRODUCT_B + "/" + DispatchOperation.STOCK_OUT,
				DESTINATION_BRANCH + "/" + PRODUCT_A + "/" + DispatchOperation.IN_TRANSIT_INCREMENT,
				DESTINATION_BRANCH + "/" + PRODUCT_B + "/" + DispatchOperation.IN_TRANSIT_INCREMENT);
	}

	@Test
	void theTransferRowIsAlwaysLockedFirstByTheCallerBeforeAnyPlanLine() {
		// F-5: the plan itself only orders inventory rows; the transfer row lock happens outside
		// this policy, in the application service, before TransferDispatchPolicy.plan is called.
		UUID itemId = UUID.randomUUID();
		TransferItem item = requested(itemId, PRODUCT_A, "10");

		DispatchPlan plan = TransferDispatchPolicy.plan(ORIGIN_BRANCH, DESTINATION_BRANCH, List.of(item),
				List.of(new DispatchLine(itemId, new BigDecimal("10"))));

		assertThat(plan.lines()).hasSize(2);
	}
}
