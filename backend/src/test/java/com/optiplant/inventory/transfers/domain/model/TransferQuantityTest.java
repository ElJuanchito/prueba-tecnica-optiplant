package com.optiplant.inventory.transfers.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.transfers.domain.exception.InvalidTransferQuantityException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link TransferQuantity} — strictly positive, scale 4 (design §3.1). */
class TransferQuantityTest {

	@Test
	void normalizesToScaleFour() {
		assertThat(new TransferQuantity(new BigDecimal("8")).value()).isEqualByComparingTo("8.0000");
		assertThat(new TransferQuantity(new BigDecimal("8.12345")).value()).isEqualTo(new BigDecimal("8.1235"));
	}

	@Test
	void rejectsNull() {
		assertThatThrownBy(() -> new TransferQuantity(null)).isInstanceOf(InvalidTransferQuantityException.class);
	}

	@Test
	void rejectsZero() {
		assertThatThrownBy(() -> new TransferQuantity(BigDecimal.ZERO))
				.isInstanceOf(InvalidTransferQuantityException.class);
	}

	@Test
	void rejectsNegative() {
		assertThatThrownBy(() -> new TransferQuantity(new BigDecimal("-1")))
				.isInstanceOf(InvalidTransferQuantityException.class);
	}
}
