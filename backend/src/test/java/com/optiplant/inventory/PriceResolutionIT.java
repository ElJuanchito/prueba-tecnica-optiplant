package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * Price resolution and supersession rules against a real PostgreSQL 17 (Testcontainers)
 * — R-11, R-16, RN-16, and D-7 (tasks.md 3.5).
 *
 * <ul>
 *   <li>A branch-scoped exception price beats the corporate default.</li>
 *   <li>The seeded expired row ({@code 50000000-0000-0000-0000-000000000010}) is ignored.</li>
 *   <li>A second current price for the same list/product/scope is refused with {@code 409 price_period_conflict}.</li>
 *   <li>Setting a valid future price closes the open row and inserts the new one.</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PriceResolutionIT {

	private static final String SEED_PASSWORD = "Password123!";
	// List 1: RETAIL (40000000-0000-0000-0000-000000000001)
	private static final UUID LIST_RETAIL = UUID.fromString("40000000-0000-0000-0000-000000000001");
	// Product 1: FERT-NPK-151515
	private static final UUID PRODUCT_NPK = UUID.fromString("d0000000-0000-0000-0000-000000000001");
	// Product 2: BIO-FOL-AMINO
	private static final UUID PRODUCT_FOLIAR = UUID.fromString("d0000000-0000-0000-0000-000000000002");
	// Branch Cali: b0000000-0000-0000-0000-000000000003
	private static final UUID BRANCH_CALI = UUID.fromString("b0000000-0000-0000-0000-000000000003");

	@LocalServerPort
	private int port;

	private RestClient restClient;
	private String caliToken;
	private String bogotaToken;
	private String adminToken;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		caliToken = token("gerente.cali");
		bogotaToken = token("gerente.bogota");
		adminToken = token("admin.corp");
	}

	@Test
	void branchScopedPriceBeatsCorporateAndExpiredPriceIsIgnored() {
		// In RETAIL list:
		// Product NPK has:
		// - Corporate price: 4200.0000 (valid_to null)
		// - Cali exception: 3980.0000 (valid_to null)
		// - Expired corporate price: 3900.0000 (valid_to 2026-06-30)

		// 1. Cali quote -> resolves branch exception 3980.0000
		QuoteResponse caliQuote = quote(caliToken, LIST_RETAIL, PRODUCT_NPK, new BigDecimal("1.0000"));
		assertThat(caliQuote.items()).hasSize(1);
		assertThat(caliQuote.items().get(0).listUnitPrice()).isEqualByComparingTo(new BigDecimal("3980.0000"));

		// 2. Bogotá quote -> resolves current corporate price 4200.0000, ignoring expired 3900.0000
		QuoteResponse bogotaQuote = quote(bogotaToken, LIST_RETAIL, PRODUCT_NPK, new BigDecimal("1.0000"));
		assertThat(bogotaQuote.items()).hasSize(1);
		assertThat(bogotaQuote.items().get(0).listUnitPrice()).isEqualByComparingTo(new BigDecimal("4200.0000"));
	}

	@Test
	void settingPriceWithValidFromNotStrictlyAfterCurrentOpenRowFailsWithPricePeriodConflict() {
		// Corporate price for PRODUCT_NPK in LIST_RETAIL is currently open with valid_from <= today.
		// Attempting to set a new price with validFrom = today or past is refused by PriceSupersessionPolicy
		// to protect check_price_period and prevent overlapping current rows.
		Map<String, Object> request = Map.of(
				"productExternalId", PRODUCT_NPK,
				"unitPrice", new BigDecimal("4500.0000"),
				"validFrom", LocalDate.now().minusDays(1)
		);

		ResponseEntity<ErrorBody> response = restClient.post()
				.uri("/api/pricing/price-lists/{id}/prices", LIST_RETAIL)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("price_period_conflict");
	}

	@Test
	void settingFuturePriceClosesCurrentOpenRowAndInsertsNewOne() {
		// Create a fresh dedicated price list for supersession testing
		String code = "SUPER-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		Map<String, Object> listRequest = Map.of(
				"code", code,
				"name", "Lista Supersesion Test",
				"maxDiscountPercent", new BigDecimal("15.00")
		);

		@SuppressWarnings("unchecked")
		Map<String, Object> listResponse = restClient.post()
				.uri("/api/pricing/price-lists")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(listRequest)
				.retrieve()
				.body(Map.class);
		assertThat(listResponse).isNotNull();
		UUID priceListId = UUID.fromString((String) listResponse.get("externalId"));

		// 1. Set initial price with validFrom = tomorrow
		LocalDate date1 = LocalDate.now().plusDays(1);
		Map<String, Object> price1Req = Map.of(
				"productExternalId", PRODUCT_FOLIAR,
				"unitPrice", new BigDecimal("50000.0000"),
				"validFrom", date1
		);
		@SuppressWarnings("unchecked")
		Map<String, Object> price1Res = restClient.post()
				.uri("/api/pricing/price-lists/{id}/prices", priceListId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(price1Req)
				.retrieve()
				.body(Map.class);
		assertThat(price1Res).isNotNull();
		UUID price1Id = UUID.fromString((String) price1Res.get("externalId"));
		assertThat(price1Res.get("validTo")).isNull();

		// 2. Set superseding price with validFrom = date1 + 10 days
		LocalDate date2 = date1.plusDays(10);
		Map<String, Object> price2Req = Map.of(
				"productExternalId", PRODUCT_FOLIAR,
				"unitPrice", new BigDecimal("55000.0000"),
				"validFrom", date2
		);
		@SuppressWarnings("unchecked")
		Map<String, Object> price2Res = restClient.post()
				.uri("/api/pricing/price-lists/{id}/prices", priceListId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(price2Req)
				.retrieve()
				.body(Map.class);
		assertThat(price2Res).isNotNull();
		assertThat(price2Res.get("validTo")).isNull();

		// Check price 1 is now closed with validTo = date2 - 1 day
		@SuppressWarnings("unchecked")
		Map<String, Object> pricesPage = restClient.get()
				.uri("/api/pricing/price-lists/{id}/prices?productExternalId={p}", priceListId, PRODUCT_FOLIAR)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.body(Map.class);
		assertThat(pricesPage).isNotNull();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> content = (List<Map<String, Object>>) pricesPage.get("content");
		assertThat(content).hasSize(2);

		Map<String, Object> closedRow = content.stream()
				.filter(p -> price1Id.toString().equals(p.get("externalId")))
				.findFirst()
				.orElseThrow();
		assertThat(closedRow.get("validTo")).isEqualTo(date2.minusDays(1).toString());
	}

	private QuoteResponse quote(String token, UUID priceListId, UUID productId, BigDecimal quantity) {
		Map<String, Object> body = Map.of(
				"priceListExternalId", priceListId,
				"items", List.of(Map.of("productExternalId", productId, "quantity", quantity))
		);
		return restClient.post()
				.uri("/api/pricing/quotes")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(QuoteResponse.class);
	}

	private String token(String username) {
		LoginResponseBody body = restClient.post().uri("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
				.body(new LoginRequestBody(username, SEED_PASSWORD)).retrieve().body(LoginResponseBody.class);
		assertThat(body).isNotNull();
		return body.accessToken();
	}

	private record ErrorBody(String code, String message) {
	}

	private record LoginRequestBody(String username, String password) {
	}

	private record LoginResponseBody(String accessToken, String refreshToken, long expiresInSeconds, String role,
			String branchId, String branchName, String branchCode) {
	}

	private record QuoteItemResponse(UUID productExternalId, BigDecimal listUnitPrice, BigDecimal unitPrice, BigDecimal subtotal) {
	}

	private record QuoteResponse(UUID priceListExternalId, String code, BigDecimal maxDiscountPercent, List<QuoteItemResponse> items) {
	}
}
