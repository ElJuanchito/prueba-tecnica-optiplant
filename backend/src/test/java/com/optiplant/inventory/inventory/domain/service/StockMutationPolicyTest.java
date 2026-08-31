package com.optiplant.inventory.inventory.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.inventory.domain.exception.InsufficientStockException;
import com.optiplant.inventory.inventory.domain.exception.UnitCostContractViolationException;
import com.optiplant.inventory.inventory.domain.model.BranchInventory;
import com.optiplant.inventory.inventory.domain.model.KardexMovement;
import com.optiplant.inventory.inventory.domain.model.Quantity;
import com.optiplant.inventory.inventory.domain.model.StockLevel;
import com.optiplant.inventory.inventory.domain.model.UnitCost;
import com.optiplant.inventory.inventory.domain.service.StockMutationPolicy.MovementDraft;
import com.optiplant.inventory.shared.stock.StockMovementType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link StockMutationPolicy} (design §3.3): P-03 cost-presence rule, R-11
 * insufficient stock, R-12 outbound valuation at {@code averageCost}, scale 4 (RN-02, RN-03).
 *
 * <p>The P-06 guard is pinned here: {@code average_cost} is recalculated by
 * {@link WeightedAverageCostPolicy} <strong>only</strong> for {@code PURCHASE_RECEIPT}
 * (design §2.3). Widening it to {@code isInbound()} would move the average on
 * {@code TRANSFER_IN}, {@code ADJUSTMENT_POS} and {@code INITIAL_LOAD}, breaking the archived
 * {@code add-sales-module} R-21 (a sale void must not alter {@code average_cost}).
 */
class StockMutationPolicyTest {

	private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");

	private static BranchInventory inventory(String currentStock, String averageCost) {
		return new BranchInventory(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				new StockLevel(new BigDecimal(currentStock)), StockLevel.zero(), StockLevel.zero(),
				new StockLevel(new BigDecimal("10")), new UnitCost(new BigDecimal(averageCost)), NOW);
	}

	@Test
	void inboundRejectsAMissingSuppliedCost() {
		BranchInventory current = inventory("10", "5");

		assertThatThrownBy(() -> StockMutationPolicy.apply(current, StockMovementType.PURCHASE_RECEIPT,
				new Quantity(BigDecimal.TEN), null, null, null, null, UUID.randomUUID(), NOW))
				.isInstanceOf(UnitCostContractViolationException.class);
	}

	@Test
	void outboundRejectsASuppliedCost() {
		BranchInventory current = inventory("10", "5");

		assertThatThrownBy(() -> StockMutationPolicy.apply(current, StockMovementType.SALE,
				new Quantity(BigDecimal.ONE), new UnitCost(BigDecimal.TEN), null, null, null, UUID.randomUUID(), NOW))
				.isInstanceOf(UnitCostContractViolationException.class);
	}

	@Test
	void outboundAboveBalanceIsInsufficientStockAndTouchesNothing() {
		BranchInventory current = inventory("5", "5");

		assertThatThrownBy(() -> StockMutationPolicy.apply(current, StockMovementType.SALE,
				new Quantity(BigDecimal.TEN), null, null, null, null, UUID.randomUUID(), NOW))
				.isInstanceOf(InsufficientStockException.class);
	}

	@Test
	void outboundIsValuedAtTheCurrentAverageCostNeverAClientSuppliedOne() {
		BranchInventory current = inventory("100", "12.5000");

		MovementDraft draft = StockMutationPolicy.apply(current, StockMovementType.DAMAGE_WASTE,
				new Quantity(new BigDecimal("8")), null, null, null, "broken bag", UUID.randomUUID(), NOW);

		KardexMovement.Draft movement = draft.movement();
		assertThat(movement.unitCost().value()).isEqualByComparingTo("12.5000");
		assertThat(movement.totalCost()).isEqualByComparingTo("100.0000");
		assertThat(movement.previousStock()).isEqualByComparingTo("100.0000");
		assertThat(movement.resultingStock()).isEqualByComparingTo("92.0000");
		assertThat(draft.updated().currentStock().value()).isEqualByComparingTo("92.0000");
	}

	@Test
	void inboundIncreasesTheBalanceAndStampsTheSuppliedCost() {
		BranchInventory current = inventory("10", "5");

		MovementDraft draft = StockMutationPolicy.apply(current, StockMovementType.PURCHASE_RECEIPT,
				new Quantity(new BigDecimal("5")), new UnitCost(new BigDecimal("20")), "PURCHASE_ORDER", "PO-1", null,
				UUID.randomUUID(), NOW);

		assertThat(draft.updated().currentStock().value()).isEqualByComparingTo("15.0000");
		assertThat(draft.movement().resultingStock()).isEqualByComparingTo("15.0000");
		assertThat(draft.movement().totalCost()).isEqualByComparingTo("100.0000");
	}

	@Test
	@DisplayName("P-06 / R-18: PURCHASE_RECEIPT recalculates the weighted average (100 @ 10 + 100 @ 20 -> 15)")
	void purchaseReceiptRecalculatesTheWeightedAverage() {
		BranchInventory current = inventory("100", "10");

		MovementDraft draft = StockMutationPolicy.apply(current, StockMovementType.PURCHASE_RECEIPT,
				new Quantity(new BigDecimal("100")), new UnitCost(new BigDecimal("20")), "PURCHASE_ORDER", "PO-9",
				null, UUID.randomUUID(), NOW);

		assertThat(draft.updated().averageCost().value()).isEqualByComparingTo("15.0000");
	}

	@Test
	@DisplayName("P-06: TRANSFER_IN, ADJUSTMENT_POS and INITIAL_LOAD leave averageCost identical "
			+ "(add-sales-module R-21)")
	void otherInboundTypesDoNotMoveTheAverageCost() {
		for (StockMovementType type : new StockMovementType[] { StockMovementType.TRANSFER_IN,
				StockMovementType.ADJUSTMENT_POS, StockMovementType.INITIAL_LOAD }) {
			BranchInventory current = inventory("100", "12.5000");

			MovementDraft draft = StockMutationPolicy.apply(current, type, new Quantity(new BigDecimal("50")),
					new UnitCost(new BigDecimal("99")), null, null, null, UUID.randomUUID(), NOW);

			assertThat(draft.updated().averageCost().value())
					.as("%s must not move averageCost", type)
					.isEqualByComparingTo("12.5000");
		}
	}

	@Test
	@DisplayName("P-06: the four outbound types leave averageCost identical")
	void outboundTypesDoNotMoveTheAverageCost() {
		for (StockMovementType type : new StockMovementType[] { StockMovementType.SALE,
				StockMovementType.TRANSFER_OUT, StockMovementType.ADJUSTMENT_NEG, StockMovementType.DAMAGE_WASTE }) {
			BranchInventory current = inventory("100", "12.5000");

			MovementDraft draft = StockMutationPolicy.apply(current, type, new Quantity(new BigDecimal("10")),
					null, null, null, null, UUID.randomUUID(), NOW);

			assertThat(draft.updated().averageCost().value())
					.as("%s must not move averageCost", type)
					.isEqualByComparingTo("12.5000");
		}
	}
}
