package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
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
 * Smoke assertions for {@code /api/purchases/**} endpoints — RNF-API-01, RNF-API-02, RNF-PER-04,
 * and contract §6 (tasks.md 3.6).
 *
 * <ul>
 *   <li>Supplier CRUD including disable/enable (R-03).</li>
 *   <li>Order listing with every §6 filter (supplier, product, status, date range, pagination, sorting).</li>
 *   <li>Cost history endpoint asserting agreed costs and ensuring {@code average_cost} is NEVER exposed (R-26).</li>
 *   <li>No numeric {@code id} anywhere in payloads (contract §7 "must not leak").</li>
 *   <li>No raw {@code CANCEL_REASON:} token in responses.</li>
 *   <li>Oversized page rejected with {@code 400 invalid_request}.</li>
 *   <li>OpenAPI {@code /v3/api-docs} contains purchases operations.</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PurchasesApiSmokeIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID SUPPLIER_AGROFERTIL = UUID.fromString("f0000000-0000-0000-0000-000000000001");
	private static final UUID PRODUCT_NPK = UUID.fromString("d0000000-0000-0000-0000-000000000001");

	@LocalServerPort
	private int port;

	private RestClient restClient;
	private String bogotaToken;
	private String adminToken;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		bogotaToken = token("gerente.bogota");
		adminToken = token("admin.corp");
	}

	@Test
	void supplierCrudCycleIncludingDisableEnableAndShapeValidation() {
		String uniqueTaxId = "900." + System.currentTimeMillis() % 1000000000 + "-9";

		// 1. Create supplier (ADMIN only)
		Map<String, Object> createRequest = Map.of(
				"taxId", uniqueTaxId,
				"name", "Proveedor Smoke Test S.A.",
				"contactName", "Carlos Vendedor",
				"email", "ventas@smokeproveedor.com",
				"phone", "+57 300 1234567",
				"address", "Calle 100 # 15-20"
		);

		ResponseEntity<String> createResponse = restClient.post()
				.uri("/api/purchases/suppliers")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(createRequest)
				.retrieve()
				.toEntity(String.class);

		assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		String createBody = createResponse.getBody();
		assertThat(createBody).isNotNull();
		assertNoNumericIdLeak(createBody);
		assertThat(createBody).contains("\"active\":true");

		UUID supplierId = extractExternalId(createBody);

		// 2. Get supplier by ID
		ResponseEntity<String> getResponse = restClient.get()
				.uri("/api/purchases/suppliers/{id}", supplierId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.toEntity(String.class);

		assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		String getBody = getResponse.getBody();
		assertThat(getBody).isNotNull();
		assertNoNumericIdLeak(getBody);
		assertThat(getBody).contains("\"taxId\":\"" + uniqueTaxId + "\"");

		// 3. Edit supplier
		Map<String, Object> editRequest = Map.of(
				"name", "Proveedor Smoke Editado S.A.",
				"contactName", "Carlos Editado",
				"email", "editado@smokeproveedor.com",
				"phone", "+57 300 9876543",
				"address", "Carrera 7 # 72-10"
		);

		ResponseEntity<String> editResponse = restClient.put()
				.uri("/api/purchases/suppliers/{id}", supplierId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(editRequest)
				.retrieve()
				.toEntity(String.class);

		assertThat(editResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		String editBody = editResponse.getBody();
		assertThat(editBody).isNotNull();
		assertNoNumericIdLeak(editBody);
		assertThat(editBody).contains("\"name\":\"Proveedor Smoke Editado S.A.\"");

		// 4. List suppliers with search and active filter
		ResponseEntity<String> listResponse = restClient.get()
				.uri("/api/purchases/suppliers?search=Editado&active=true&page=0&size=10")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.toEntity(String.class);

		assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		String listBody = listResponse.getBody();
		assertThat(listBody).isNotNull();
		assertPagedEnvelopeShape(listBody);
		assertNoNumericIdLeak(listBody);
		assertThat(listBody).contains("\"externalId\":\"" + supplierId + "\"");

		// 5. Disable supplier
		ResponseEntity<String> disableResponse = restClient.patch()
				.uri("/api/purchases/suppliers/{id}/disable", supplierId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.toEntity(String.class);

		assertThat(disableResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		String disableBody = disableResponse.getBody();
		assertThat(disableBody).isNotNull();
		assertNoNumericIdLeak(disableBody);
		assertThat(disableBody).contains("\"active\":false");

		// 6. Enable supplier
		ResponseEntity<String> enableResponse = restClient.patch()
				.uri("/api/purchases/suppliers/{id}/enable", supplierId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.toEntity(String.class);

		assertThat(enableResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		String enableBody = enableResponse.getBody();
		assertThat(enableBody).isNotNull();
		assertNoNumericIdLeak(enableBody);
		assertThat(enableBody).contains("\"active\":true");
	}

	@Test
	void listOrdersWithAllFiltersReturnsPagedEnvelopeWithNoNumericId() {
		UUID orderId = createOrder("Orden Smoke Filtros");

		// Listing with all §6 filters
		ResponseEntity<String> response = restClient.get()
				.uri(uriBuilder -> uriBuilder
						.path("/api/purchases/orders")
						.queryParam("supplierExternalId", SUPPLIER_AGROFERTIL)
						.queryParam("productExternalId", PRODUCT_NPK)
						.queryParam("status", "PENDING")
						.queryParam("from", "2026-01-01T00:00:00Z")
						.queryParam("to", "2027-01-01T00:00:00Z")
						.queryParam("page", "0")
						.queryParam("size", "10")
						.queryParam("sort", "createdAt,desc")
						.build())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.toEntity(String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = response.getBody();
		assertThat(body).isNotNull();
		assertPagedEnvelopeShape(body);
		assertNoNumericIdLeak(body);
		assertThat(body).contains("\"externalId\":\"" + orderId + "\"");
	}

	@Test
	void costHistoryReturnsAgreedCostsWithoutExposingAverageCost() {
		// Create and receive an order to populate cost history
		UUID orderId = createOrder("Orden para Cost History");
		UUID itemId = getItemId(orderId);

		// Approve
		restClient.post()
				.uri("/api/purchases/orders/{id}/approval", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.toBodilessEntity();

		// Receive
		restClient.post()
				.uri("/api/purchases/orders/{id}/receptions", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("items", List.of(Map.of("itemExternalId", itemId, "receivedQuantity", new BigDecimal("10.0000")))))
				.retrieve()
				.toBodilessEntity();

		// Query cost history for PRODUCT_NPK
		ResponseEntity<String> response = restClient.get()
				.uri(uriBuilder -> uriBuilder
						.path("/api/purchases/cost-history")
						.queryParam("productExternalId", PRODUCT_NPK)
						.queryParam("supplierExternalId", SUPPLIER_AGROFERTIL)
						.queryParam("page", "0")
						.queryParam("size", "10")
						.build())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.toEntity(String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = response.getBody();
		assertThat(body).isNotNull();
		assertPagedEnvelopeShape(body);
		assertNoNumericIdLeak(body);

		// Contains agreed cost fields
		assertThat(body)
				.contains("\"orderExternalId\"")
				.contains("\"orderNumber\"")
				.contains("\"supplier\"")
				.contains("\"unitCost\"")
				.contains("\"discountPercent\"")
				.contains("\"effectiveUnitCost\"")
				.contains("\"quantity\"")
				.contains("\"orderedAt\"")
				.contains("\"receivedAt\"");

		// R-26, RNF-API-02: average_cost / averageCost must NEVER appear anywhere in cost history
		assertThat(body).doesNotContain("average_cost");
		assertThat(body).doesNotContain("averageCost");
	}

	@Test
	void orderDetailReturnsExpectedShapeWithNoNumericIdAndNoRawCancelReasonToken() {
		UUID orderId = createOrder("Orden Smoke Detalle");
		String reason = "Cancelación para smoke test";

		restClient.post()
				.uri("/api/purchases/orders/{id}/cancellation", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("reason", reason))
				.retrieve()
				.toBodilessEntity();

		ResponseEntity<String> response = restClient.get()
				.uri("/api/purchases/orders/{id}", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.toEntity(String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(body)
				.contains("\"externalId\"")
				.contains("\"orderNumber\"")
				.contains("\"status\":\"CANCELLED\"")
				.contains("\"branch\"")
				.contains("\"supplier\"")
				.contains("\"createdBy\"")
				.contains("\"paymentTerms\"")
				.contains("\"totalAmount\"")
				.contains("\"notes\"")
				.contains("\"cancellationReason\":\"" + reason + "\"")
				.contains("\"items\"");
		assertNoNumericIdLeak(body);
		assertThat(body).doesNotContain("CANCEL_REASON:");
	}

	@Test
	void oversizedPageSizeIsRejectedOnAllPurchasesEndpoints() {
		// 1. Orders
		ResponseEntity<ErrorBody> ordersResponse = restClient.get()
				.uri("/api/purchases/orders?size=101")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(ordersResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(ordersResponse.getBody()).isNotNull();
		assertThat(ordersResponse.getBody().code()).isEqualTo("invalid_request");

		// 2. Suppliers
		ResponseEntity<ErrorBody> suppliersResponse = restClient.get()
				.uri("/api/purchases/suppliers?size=101")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(suppliersResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(suppliersResponse.getBody()).isNotNull();
		assertThat(suppliersResponse.getBody().code()).isEqualTo("invalid_request");

		// 3. Cost History
		ResponseEntity<ErrorBody> costHistoryResponse = restClient.get()
				.uri("/api/purchases/cost-history?productExternalId=" + PRODUCT_NPK + "&size=101")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(costHistoryResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(costHistoryResponse.getBody()).isNotNull();
		assertThat(costHistoryResponse.getBody().code()).isEqualTo("invalid_request");
	}

	@Test
	void openApiDocumentsAllPurchasesOperations() {
		ResponseEntity<String> response = restClient.get()
				.uri("/v3/api-docs")
				.retrieve()
				.toEntity(String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(body)
				.contains("/api/purchases/suppliers")
				.contains("/api/purchases/suppliers/{externalId}")
				.contains("/api/purchases/suppliers/{externalId}/disable")
				.contains("/api/purchases/suppliers/{externalId}/enable")
				.contains("/api/purchases/orders")
				.contains("/api/purchases/orders/{externalId}")
				.contains("/api/purchases/orders/{externalId}/approval")
				.contains("/api/purchases/orders/{externalId}/cancellation")
				.contains("/api/purchases/orders/{externalId}/receptions")
				.contains("/api/purchases/cost-history");
	}

	@SuppressWarnings("unchecked")
	private UUID createOrder(String notes) {
		Map<String, Object> request = Map.of(
				"supplierExternalId", SUPPLIER_AGROFERTIL,
				"paymentTerms", "Contado",
				"notes", notes,
				"items", List.of(
						Map.of("productExternalId", PRODUCT_NPK, "quantity", new BigDecimal("10.0000"), "unitCost", new BigDecimal("3500.0000"))
				)
		);
		Map<String, Object> body = restClient.post()
				.uri("/api/purchases/orders")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(Map.class);
		assertThat(body).isNotNull();
		return UUID.fromString((String) body.get("externalId"));
	}

	@SuppressWarnings("unchecked")
	private UUID getItemId(UUID orderId) {
		Map<String, Object> body = restClient.get()
				.uri("/api/purchases/orders/{id}", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.body(Map.class);
		assertThat(body).isNotNull();
		List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
		return UUID.fromString((String) items.get(0).get("externalId"));
	}

	private static UUID extractExternalId(String json) {
		// Extract externalId UUID from json string
		int index = json.indexOf("\"externalId\":\"");
		if (index != -1) {
			int start = index + 14;
			int end = json.indexOf('"', start);
			return UUID.fromString(json.substring(start, end));
		}
		throw new IllegalArgumentException("No externalId in JSON: " + json);
	}

	private static void assertPagedEnvelopeShape(String body) {
		assertThat(body).contains("\"content\"").contains("\"totalElements\"").contains("\"page\"").contains("\"size\"");
	}

	private static void assertNoNumericIdLeak(String body) {
		assertThat(body).doesNotContain("\"id\":");
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
