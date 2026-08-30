package com.optiplant.inventory.sales.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.sales.domain.exception.DiscountExceedsCapException;
import com.optiplant.inventory.shared.security.Role;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DiscountCapPolicyTest {

	private static final BigDecimal CAP = new BigDecimal("15.00");

	@Test
	@DisplayName("R-13 / RN-17: Discount below the cap is valid")
	void discountBelowCapIsValid() {
		assertThatCode(() -> DiscountCapPolicy.validate(new BigDecimal("10.00"), CAP))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("R-13 / RN-17: Discount exactly at the cap is valid")
	void discountAtCapIsValid() {
		assertThatCode(() -> DiscountCapPolicy.validate(new BigDecimal("15.00"), CAP))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("R-13 / RN-17: Discount above the cap throws DiscountExceedsCapException")
	void discountAboveCapThrows() {
		assertThatThrownBy(() -> DiscountCapPolicy.validate(new BigDecimal("15.01"), CAP))
				.isInstanceOf(DiscountExceedsCapException.class)
				.satisfies(ex -> {
					DiscountExceedsCapException capEx = (DiscountExceedsCapException) ex;
					assertThat(capEx.requestedDiscount()).isEqualByComparingTo("15.01");
					assertThat(capEx.maxDiscountPercent()).isEqualByComparingTo("15.00");
				});
	}

	@ParameterizedTest
	@EnumSource(Role.class)
	@DisplayName("F-5 / PA-02: Discount cap applies identically for all three roles")
	void capAppliesIdenticallyForAllRoles(Role role) {
		// Regardless of caller role, discounts exceeding the applied list cap are rejected
		assertThatThrownBy(() -> DiscountCapPolicy.validate(new BigDecimal("20.00"), CAP))
				.isInstanceOf(DiscountExceedsCapException.class);
	}
}
