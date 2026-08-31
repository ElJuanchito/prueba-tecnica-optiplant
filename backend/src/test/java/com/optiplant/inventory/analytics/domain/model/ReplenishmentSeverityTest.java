package com.optiplant.inventory.analytics.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReplenishmentSeverityTest {

	@Test
	@DisplayName("R-16: currentStock <= 0 is OUT_OF_STOCK, otherwise CRITICAL")
	void severityRules() {
		assertThat(ReplenishmentSeverity.of(BigDecimal.ZERO)).isEqualTo(ReplenishmentSeverity.OUT_OF_STOCK);
		assertThat(ReplenishmentSeverity.of(new BigDecimal("-1"))).isEqualTo(ReplenishmentSeverity.OUT_OF_STOCK);
		assertThat(ReplenishmentSeverity.of(null)).isEqualTo(ReplenishmentSeverity.OUT_OF_STOCK);

		assertThat(ReplenishmentSeverity.of(new BigDecimal("0.01"))).isEqualTo(ReplenishmentSeverity.CRITICAL);
		assertThat(ReplenishmentSeverity.of(new BigDecimal("10"))).isEqualTo(ReplenishmentSeverity.CRITICAL);
	}
}
