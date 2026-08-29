package com.optiplant.inventory.catalog.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.catalog.domain.exception.BaseUnitChangeRejectedException;
import com.optiplant.inventory.catalog.domain.exception.BaseUnitChangeRejectedException.Reason;
import com.optiplant.inventory.catalog.domain.model.CategoryRef;
import com.optiplant.inventory.catalog.domain.model.Product;
import com.optiplant.inventory.catalog.domain.model.Sku;
import com.optiplant.inventory.catalog.domain.model.StockPresence;
import com.optiplant.inventory.catalog.domain.model.UnitCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BaseUnitChangePolicy} (R-08, design §4.2): the three
 * {@link StockPresence} values produce apply / {@code HAS_HISTORY} /
 * {@code PRECONDITION_UNVERIFIABLE}, and on refusal no field of the product
 * changes. Pure domain — no stub, no Docker.
 */
class BaseUnitChangePolicyTest {

	private static final Instant CREATED = Instant.parse("2020-01-01T00:00:00Z");
	private static final Instant NOW = Instant.parse("2026-08-28T12:00:00Z");
	private static final CategoryRef CATEGORY = new CategoryRef(UUID.randomUUID(), "Fertilizantes", true);

	private static Product product() {
		return new Product(UUID.randomUUID(), new Sku("FERT-NPK-151515"), "Fertilizante Triple 15", null,
				UnitCode.baseUnit("KG"), true, CATEGORY, List.of(), CREATED, CREATED);
	}

	@Test
	void untouchedAppliesTheNewBaseUnitAndAdvancesUpdatedAt() {
		Product before = product();

		Product after = BaseUnitChangePolicy.apply(before, UnitCode.baseUnit("LITRO"), StockPresence.UNTOUCHED, NOW);

		assertThat(after.baseUnit().value()).isEqualTo("LITRO");
		assertThat(after.updatedAt()).isEqualTo(NOW);
		assertThat(after.externalId()).isEqualTo(before.externalId());
		assertThat(after.sku()).isEqualTo(before.sku());
		assertThat(after.createdAt()).isEqualTo(before.createdAt());
	}

	@Test
	void hasHistoryThrowsWithHasHistoryReasonAndTouchesNoField() {
		Product before = product();

		assertThatThrownBy(
				() -> BaseUnitChangePolicy.apply(before, UnitCode.baseUnit("LITRO"), StockPresence.HAS_HISTORY, NOW))
				.isInstanceOfSatisfying(BaseUnitChangeRejectedException.class,
						ex -> assertThat(ex.reason()).isEqualTo(Reason.HAS_HISTORY));

		assertThat(before.baseUnit().value()).isEqualTo("KG");
		assertThat(before.updatedAt()).isEqualTo(CREATED);
	}

	@Test
	void unknownThrowsWithPreconditionUnverifiableReasonAndTouchesNoField() {
		Product before = product();

		assertThatThrownBy(
				() -> BaseUnitChangePolicy.apply(before, UnitCode.baseUnit("LITRO"), StockPresence.UNKNOWN, NOW))
				.isInstanceOfSatisfying(BaseUnitChangeRejectedException.class,
						ex -> assertThat(ex.reason()).isEqualTo(Reason.PRECONDITION_UNVERIFIABLE));

		assertThat(before.baseUnit().value()).isEqualTo("KG");
		assertThat(before.updatedAt()).isEqualTo(CREATED);
	}
}
