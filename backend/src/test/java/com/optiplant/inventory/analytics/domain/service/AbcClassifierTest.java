package com.optiplant.inventory.analytics.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.optiplant.inventory.analytics.domain.model.AbcClass;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AbcClassifierTest {

	@Test
	@DisplayName("R-09: exactly 80.00% is classified as A")
	void exactlyEightyPercentIsA() {
		assertThat(AbcClassifier.classify(new BigDecimal("80.00"))).isEqualTo(AbcClass.A);
		assertThat(AbcClassifier.classify(new BigDecimal("80"))).isEqualTo(AbcClass.A);
	}

	@Test
	@DisplayName("R-09: exactly 95.00% is classified as B")
	void exactlyNinetyFivePercentIsB() {
		assertThat(AbcClassifier.classify(new BigDecimal("95.00"))).isEqualTo(AbcClass.B);
		assertThat(AbcClassifier.classify(new BigDecimal("95"))).isEqualTo(AbcClass.B);
	}

	@Test
	@DisplayName("R-09: single product at 100.00% is classified as C")
	void singleProductAtHundredPercentIsC() {
		assertThat(AbcClassifier.classify(new BigDecimal("100.00"))).isEqualTo(AbcClass.C);
		assertThat(AbcClassifier.classify(new BigDecimal("100"))).isEqualTo(AbcClass.C);
	}

	@ParameterizedTest
	@CsvSource({
			"0.00, A",
			"50.00, A",
			"79.99, A",
			"80.00, A",
			"80.01, B",
			"85.00, B",
			"94.99, B",
			"95.00, B",
			"95.01, C",
			"99.99, C",
			"100.00, C"
	})
	@DisplayName("R-09: either side of each boundary (80% and 95%)")
	void boundaries(String cumulativeShare, AbcClass expected) {
		assertThat(AbcClassifier.classify(new BigDecimal(cumulativeShare))).isEqualTo(expected);
	}

	@Test
	@DisplayName("Null cumulative share defaults to C")
	void nullCumulativeShareIsC() {
		assertThat(AbcClassifier.classify(null)).isEqualTo(AbcClass.C);
	}
}
