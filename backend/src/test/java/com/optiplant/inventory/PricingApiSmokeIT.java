package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
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
 * Smoke assertions for {@code /api/pricing/**} endpoints and OpenAPI contract
 * — RF-VEN-03, RNF-API-01, RNF-API-02, and contract §6 (tasks.md 3.7).
 *
 * <ul>
 *   <li>One assertion per price-list CRUD operation and quote calculation.</li>
 *   <li>Status, page-envelope shape, and no numeric {@code id} anywhere in payloads.</li>
 *   <li>Oversized page rejected with {@code 400 invalid_request}.</li>
 *   <li>OpenAPI {@code /v3/api-docs} documents all fourteen sales and pricing operations.</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PricingApiSmokeIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID PRODUCT_NPK = UUID.fromString("d0000000-0000-0000-0000-000000000001");
	private static final UUID LIST_RETAIL = UUID.fromString("40000000-0000-0000-0000-000000000001");
	private static final ObjectMapper MAPPER = new ObjectMapper();

	@LocalServerPort
	private int port;

	private RestClient restClient;
	private String adminToken;
	private String bogotaToken;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		adminToken = token("admin.corp");
		bogotaToken = token("gerente.bogota");
	}

	@Test
	void priceListCrudOperationsAndPriceManagementSmoke() {
		String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		String code = "SMOKE-" + suffix;

		// 1. POST /api/pricing/price-lists -> 201
		Map<String, Object> createBody = Map.of(
				"code", code,
				"name", "Lista Smoke " + suffix,
				"description", "Descripcion smoke",
				"maxDiscountPercent", new BigDecimal("12.50")
		);

		ResponseEntity<String> createResponse = restClient.post()
				.uri("/api/pricing/price-lists")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(createBody)
				.retrieve()
				.toEntity(String.class);

		assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String createResponseBody = createResponse.getBody();
		assertThat(createResponseBody).isNotNull();
		assertNoNumericIdLeak(createResponseBody);
		UUID listId = UUID.fromString(extractField(createResponseBody, "externalId"));

		// 2. GET /api/pricing/price-lists -> 200 (paged)
		ResponseEntity<String> listAllResponse = restClient.get()
				.uri("/api/pricing/price-lists")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.toEntity(String.class);

		assertThat(listAllResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertPagedEnvelopeShape(listAllResponse.getBody());
		assertNoNumericIdLeak(listAllResponse.getBody());

		// 3. GET /api/pricing/price-lists/{externalId} -> 200
		ResponseEntity<String> getOneResponse = restClient.get()
				.uri("/api/pricing/price-lists/{id}", listId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.toEntity(String.class);

		assertThat(getOneResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(getOneResponse.getBody()).contains("\"code\":\"" + code + "\"");
		assertNoNumericIdLeak(getOneResponse.getBody());

		// 4. PUT /api/pricing/price-lists/{externalId} -> 200
		Map<String, Object> updateBody = Map.of(
				"name", "Lista Smoke Editada " + suffix,
				"description", "Descripcion editada",
				"maxDiscountPercent", new BigDecimal("14.00")
		);

		ResponseEntity<String> updateResponse = restClient.put()
				.uri("/api/pricing/price-lists/{id}", listId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(updateBody)
				.retrieve()
				.toEntity(String.class);

		assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(updateResponse.getBody()).contains("Lista Smoke Editada");
		assertNoNumericIdLeak(updateResponse.getBody());

		// 5. POST /api/pricing/price-lists/{externalId}/prices -> 201
		Map<String, Object> setPriceBody = Map.of(
				"productExternalId", PRODUCT_NPK,
				"unitPrice", new BigDecimal("4800.0000"),
				"validFrom", LocalDate.now()
		);

		ResponseEntity<String> setPriceResponse = restClient.post()
				.uri("/api/pricing/price-lists/{id}/prices", listId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(setPriceBody)
				.retrieve()
				.toEntity(String.class);

		assertThat(setPriceResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String setPriceResponseBody = setPriceResponse.getBody();
		assertThat(setPriceResponseBody).isNotNull();
		assertNoNumericIdLeak(setPriceResponseBody);
		UUID priceId = UUID.fromString(extractField(setPriceResponseBody, "externalId"));

		// 6. GET /api/pricing/price-lists/{externalId}/prices -> 200 (paged)
		ResponseEntity<String> listPricesResponse = restClient.get()
				.uri("/api/pricing/price-lists/{id}/prices", listId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.toEntity(String.class);

		assertThat(listPricesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertPagedEnvelopeShape(listPricesResponse.getBody());
		assertNoNumericIdLeak(listPricesResponse.getBody());

		// 7. PATCH /api/pricing/prices/{externalId}/closure -> 200
		ResponseEntity<String> closeResponse = restClient.patch()
				.uri("/api/pricing/prices/{id}/closure", priceId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("validTo", LocalDate.now().plusDays(30)))
				.retrieve()
				.toEntity(String.class);

		assertThat(closeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(closeResponse.getBody()).contains("\"validTo\"");
		assertNoNumericIdLeak(closeResponse.getBody());

		// 8. PATCH /api/pricing/price-lists/{externalId}/deactivation -> 200
		ResponseEntity<String> deactivateResponse = restClient.patch()
				.uri("/api/pricing/price-lists/{id}/deactivation", listId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.toEntity(String.class);

		assertThat(deactivateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(deactivateResponse.getBody()).contains("\"active\":false");
		assertNoNumericIdLeak(deactivateResponse.getBody());
	}

	@Test
	void quoteCalculationReturnsExpectedShapeWithNoNumericId() {
		Map<String, Object> quoteRequest = Map.of(
				"priceListExternalId", LIST_RETAIL,
				"items", List.of(Map.of(
						"productExternalId", PRODUCT_NPK,
						"quantity", new BigDecimal("2.0000"),
						"discountPercent", new BigDecimal("5.00")
				))
		);

		ResponseEntity<String> response = restClient.post()
				.uri("/api/pricing/quotes")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(quoteRequest)
				.retrieve()
				.toEntity(String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(body)
				.contains("\"priceListExternalId\"")
				.contains("\"code\"")
				.contains("\"maxDiscountPercent\"")
				.contains("\"items\"");
		assertNoNumericIdLeak(body);
	}

	@Test
	void oversizedPageSizeIsRejected() {
		ResponseEntity<ErrorBody> response1 = restClient.get()
				.uri("/api/pricing/price-lists?size=101")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response1.getBody()).isNotNull();
		assertThat(response1.getBody().code()).isEqualTo("invalid_request");

		ResponseEntity<ErrorBody> response2 = restClient.get()
				.uri("/api/pricing/price-lists/{id}/prices?size=101", LIST_RETAIL)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response2.getBody()).isNotNull();
		assertThat(response2.getBody().code()).isEqualTo("invalid_request");
	}

	@Test
	void openApiPublishesAllFourteenSalesAndPricingOperations() throws Exception {
		String doc = restClient.get().uri("/v3/api-docs").retrieve().body(String.class);
		JsonNode root = MAPPER.readTree(doc);
		JsonNode paths = root.get("paths");
		assertThat(paths).as("/v3/api-docs must contain paths").isNotNull();

		List<String> expectedOperations = List.of(
				"POST /api/sales",
				"GET /api/sales",
				"GET /api/sales/{externalId}",
				"GET /api/sales/by-invoice/{invoiceNumber}",
				"POST /api/sales/{externalId}/cancellation",
				"POST /api/pricing/quotes",
				"POST /api/pricing/price-lists",
				"GET /api/pricing/price-lists",
				"PUT /api/pricing/price-lists/{externalId}",
				"PATCH /api/pricing/price-lists/{externalId}/deactivation",
				"POST /api/pricing/price-lists/{externalId}/prices",
				"GET /api/pricing/price-lists/{externalId}/prices",
				"PATCH /api/pricing/prices/{externalId}/closure",
				"POST /api/external/sales"
		);

		List<String> published = new ArrayList<>();
		for (Iterator<Entry<String, JsonNode>> it = paths.fields(); it.hasNext();) {
			Entry<String, JsonNode> pathEntry = it.next();
			for (Iterator<String> verbs = pathEntry.getValue().fieldNames(); verbs.hasNext();) {
				published.add(verbs.next().toUpperCase(java.util.Locale.ROOT) + " " + pathEntry.getKey());
			}
		}

		assertThat(published).as("OpenAPI must document all 14 sales and pricing operations")
				.containsAll(expectedOperations);
	}

	private static void assertPagedEnvelopeShape(String body) {
		assertThat(body).contains("\"content\"").contains("\"totalElements\"").contains("\"page\"").contains("\"size\"");
	}

	private static void assertNoNumericIdLeak(String body) {
		assertThat(body).doesNotContain("\"id\":");
	}

	private static String extractField(String json, String field) {
		try {
			return MAPPER.readTree(json).get(field).asText();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
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
}
