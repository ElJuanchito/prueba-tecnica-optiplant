package com.optiplant.inventory.pricing.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.optiplant.inventory.pricing.domain.model.Price;
import com.optiplant.inventory.pricing.domain.model.UnitPrice;
import com.optiplant.inventory.pricing.domain.model.ValidityRange;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PriceResolutionPolicyTest {

	private static final UUID PRICE_LIST_ID = UUID.randomUUID();
	private static final UUID PRODUCT_ID = UUID.randomUUID();
	private static final UUID BRANCH_ID = UUID.randomUUID();
	private static final LocalDate TODAY = LocalDate.of(2026, 8, 30);

	@Test
	@DisplayName("RN-16: Branch exception price beats corporate price when both are valid on the operation date")
	void branchExceptionBeatsCorporate() {
		Price corporate = price(PRODUCT_ID, null, "100.0000", TODAY.minusDays(10), null);
		Price branch = price(PRODUCT_ID, BRANCH_ID, "85.0000", TODAY.minusDays(5), null);

		Optional<Price> resolved = PriceResolutionPolicy.resolveForProduct(List.of(corporate, branch), TODAY);

		assertThat(resolved).isPresent();
		assertThat(resolved.get().unitPrice().value()).isEqualByComparingTo("85.0000");
		assertThat(resolved.get().branchExternalId()).isEqualTo(BRANCH_ID);
	}

	@Test
	@DisplayName("RN-16: Corporate price is used when no branch exception exists")
	void corporatePriceUsedWhenNoBranchException() {
		Price corporate = price(PRODUCT_ID, null, "100.0000", TODAY.minusDays(10), null);

		Optional<Price> resolved = PriceResolutionPolicy.resolveForProduct(List.of(corporate), TODAY);

		assertThat(resolved).isPresent();
		assertThat(resolved.get().unitPrice().value()).isEqualByComparingTo("100.0000");
		assertThat(resolved.get().branchExternalId()).isNull();
	}

	@Test
	@DisplayName("RN-16: Expired branch row is ineligible; corporate price applies")
	void expiredBranchRowIsIneligible() {
		Price corporate = price(PRODUCT_ID, null, "100.0000", TODAY.minusDays(30), null);
		Price expiredBranch = price(PRODUCT_ID, BRANCH_ID, "80.0000", TODAY.minusDays(30), TODAY.minusDays(1));

		Optional<Price> resolved = PriceResolutionPolicy.resolveForProduct(List.of(corporate, expiredBranch), TODAY);

		assertThat(resolved).isPresent();
		assertThat(resolved.get().unitPrice().value()).isEqualByComparingTo("100.0000");
		assertThat(resolved.get().branchExternalId()).isNull();
	}

	@Test
	@DisplayName("R-11: If all rows are expired or no eligible row exists, resolution returns empty")
	void allRowsExpiredReturnsEmpty() {
		Price expiredCorporate = price(PRODUCT_ID, null, "100.0000", TODAY.minusDays(60), TODAY.minusDays(10));
		Price expiredBranch = price(PRODUCT_ID, BRANCH_ID, "80.0000", TODAY.minusDays(60), TODAY.minusDays(5));

		Optional<Price> resolved = PriceResolutionPolicy.resolveForProduct(List.of(expiredCorporate, expiredBranch), TODAY);

		assertThat(resolved).isEmpty();
	}

	@Test
	@DisplayName("R-11: Future price rows are not eligible on the operation date")
	void futurePriceRowIsIneligible() {
		Price futureCorporate = price(PRODUCT_ID, null, "120.0000", TODAY.plusDays(1), null);

		Optional<Price> resolved = PriceResolutionPolicy.resolveForProduct(List.of(futureCorporate), TODAY);

		assertThat(resolved).isEmpty();
	}

	@Test
	@DisplayName("RN-16 / RNF-PER-02: resolveAll resolves multiple products in one batch fold")
	void resolveAllMultiProduct() {
		UUID product2 = UUID.randomUUID();
		UUID product3 = UUID.randomUUID();

		Price p1Corp = price(PRODUCT_ID, null, "100.0000", TODAY.minusDays(10), null);
		Price p1Branch = price(PRODUCT_ID, BRANCH_ID, "90.0000", TODAY.minusDays(5), null);

		Price p2Corp = price(product2, null, "50.0000", TODAY.minusDays(10), null);

		Price p3Expired = price(product3, null, "30.0000", TODAY.minusDays(20), TODAY.minusDays(1));

		Map<UUID, Price> resolved = PriceResolutionPolicy.resolveAll(
				List.of(p1Corp, p1Branch, p2Corp, p3Expired),
				TODAY
		);

		assertThat(resolved).containsOnlyKeys(PRODUCT_ID, product2);
		assertThat(resolved.get(PRODUCT_ID).unitPrice().value()).isEqualByComparingTo("90.0000");
		assertThat(resolved.get(product2).unitPrice().value()).isEqualByComparingTo("50.0000");
		assertThat(resolved).doesNotContainKey(product3);
	}

	private Price price(UUID productId, UUID branchId, String amount, LocalDate from, LocalDate to) {
		return new Price(
				UUID.randomUUID(),
				PRICE_LIST_ID,
				productId,
				branchId,
				UnitPrice.of(amount),
				new ValidityRange(from, to),
				Instant.now()
		);
	}
}
