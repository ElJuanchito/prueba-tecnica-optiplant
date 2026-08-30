package com.optiplant.inventory.sales.domain.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.sales.domain.exception.InvalidSaleStateException;
import com.optiplant.inventory.sales.domain.model.SaleStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class SaleStateMachineTest {

	@Test
	@DisplayName("R-18: COMPLETED sale is cancellable")
	void completedSaleIsCancellable() {
		assertThatCode(() -> SaleStateMachine.requireCancellable(SaleStatus.COMPLETED))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("R-18: CANCELLED sale cannot be cancelled again (double void prevented)")
	void cancelledSaleCannotBeCancelledAgain() {
		assertThatThrownBy(() -> SaleStateMachine.requireCancellable(SaleStatus.CANCELLED))
				.isInstanceOf(InvalidSaleStateException.class)
				.hasMessageContaining("cannot be cancelled");
	}

	@ParameterizedTest
	@EnumSource(SaleStatus.class)
	@DisplayName("R-18: Exhaustive enumeration of sale status cancellability")
	void exhaustiveStatusCheck(SaleStatus status) {
		if (status == SaleStatus.COMPLETED) {
			assertThatCode(() -> SaleStateMachine.requireCancellable(status)).doesNotThrowAnyException();
		} else {
			assertThatThrownBy(() -> SaleStateMachine.requireCancellable(status))
					.isInstanceOf(InvalidSaleStateException.class);
		}
	}
}
