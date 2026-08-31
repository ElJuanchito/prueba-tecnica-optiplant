package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
 * Branch isolation and RBAC tests for purchase orders against a real PostgreSQL 17 (Testcontainers)
 * — R-23, R-25, and contract §5 (tasks.md 3.5).
 *
 * <ul>
 *   <li>An actor of branch A requesting branch B's order gets {@code 404 purchase_order_not_found} (never 403).</li>
 *   <li>Order listing is scoped to the actor's branch.</li>
 *   <li>Corporate {@code ADMIN} reads orders network-wide.</li>
 *   <li>A corporate {@code ADMIN} ({@code branchId == null}) creating an order gets {@code 403 branch_context_required}.</li>
 *   <li>A corporate {@code ADMIN} receiving goods gets {@code 403 branch_context_required}.</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PurchaseBranchIsolationIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID SUPPLIER_AGROFERTIL = UUID.fromString("f0000000-0000-0000-0000-000000000001");
	private static final UUID PRODUCT_NPK = UUID.fromString("d0000000-0000-0000-0000-000000000001");

	@LocalServerPort
	private int port;

	private RestClient restClient;
	private String medellinToken;
	private String bogotaToken;
	private String adminToken;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		medellinToken = token("gerente.medellin");
		bogotaToken = token("gerente.bogota");
		adminToken = token("admin.corp");
	}

	@Test
	void actorOfBranchAGetsPurchaseOrderNotFoundForOrderOfBranchB() {
		// Bogotá creates an order
		Map<String, Object> bogotaOrder = createOrder(bogotaToken, "Orden Bogotá Exclusiva");
		UUID orderId = UUID.fromString((String) bogotaOrder.get("externalId"));
		UUID itemId = itemExternalIdOf(bogotaOrder);

		// Medellín attempts GET detail -> 404 purchase_order_not_found (never 403)
		ResponseEntity<ErrorBody> detailResponse = restClient.get()
				.uri("/api/purchases/orders/{id}", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + medellinToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(detailResponse.getBody()).isNotNull();
		assertThat(detailResponse.getBody().code()).isEqualTo("purchase_order_not_found");

		// Medellín attempts approval -> 404 purchase_order_not_found
		ResponseEntity<ErrorBody> approveResponse = restClient.post()
				.uri("/api/purchases/orders/{id}/approval", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + medellinToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(approveResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(approveResponse.getBody()).isNotNull();
		assertThat(approveResponse.getBody().code()).isEqualTo("purchase_order_not_found");

		// Medellín attempts reception -> 404 purchase_order_not_found
		ResponseEntity<ErrorBody> receiveResponse = restClient.post()
				.uri("/api/purchases/orders/{id}/receptions", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + medellinToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("items", List.of(Map.of("itemExternalId", itemId, "receivedQuantity", new BigDecimal("1.0000")))))
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(receiveResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(receiveResponse.getBody()).isNotNull();
		assertThat(receiveResponse.getBody().code()).isEqualTo("purchase_order_not_found");
	}

	@Test
	void listingOrdersIsScopedToActorsBranch() {
		Map<String, Object> medellinOrder = createOrder(medellinToken, "Orden Medellín");
		String medellinOrderNumber = (String) medellinOrder.get("orderNumber");

		@SuppressWarnings("unchecked")
		Map<String, Object> bogotaPage = restClient.get()
				.uri("/api/purchases/orders?size=100")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.body(Map.class);

		assertThat(bogotaPage).isNotNull();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> content = (List<Map<String, Object>>) bogotaPage.get("content");
		assertThat(content).noneMatch(o -> medellinOrderNumber.equals(o.get("orderNumber")));
	}

	@Test
	void corporateAdminCanReadOrdersNetworkWide() {
		Map<String, Object> medellinOrder = createOrder(medellinToken, "Orden Medellín Global");
		UUID medellinOrderId = UUID.fromString((String) medellinOrder.get("externalId"));

		Map<String, Object> bogotaOrder = createOrder(bogotaToken, "Orden Bogotá Global");
		UUID bogotaOrderId = UUID.fromString((String) bogotaOrder.get("externalId"));

		// Corporate admin reads Medellín order
		ResponseEntity<String> medellinResponse = restClient.get()
				.uri("/api/purchases/orders/{id}", medellinOrderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.toEntity(String.class);

		assertThat(medellinResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(medellinResponse.getBody()).contains("\"externalId\":\"" + medellinOrderId + "\"");

		// Corporate admin reads Bogotá order
		ResponseEntity<String> bogotaResponse = restClient.get()
				.uri("/api/purchases/orders/{id}", bogotaOrderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.toEntity(String.class);

		assertThat(bogotaResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(bogotaResponse.getBody()).contains("\"externalId\":\"" + bogotaOrderId + "\"");
	}

	@Test
	void corporateAdminCreatingOrderGetsBranchContextRequired() {
		Map<String, Object> request = Map.of(
				"supplierExternalId", SUPPLIER_AGROFERTIL,
				"items", List.of(
						Map.of("productExternalId", PRODUCT_NPK, "quantity", new BigDecimal("1.0000"), "unitCost", new BigDecimal("3500.0000"))
				)
		);

		ResponseEntity<ErrorBody> response = restClient.post()
				.uri("/api/purchases/orders")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("branch_context_required");
	}

	@Test
	void corporateAdminReceivingGoodsGetsBranchContextRequired() {
		Map<String, Object> bogotaOrder = createOrder(bogotaToken, "Orden para recepcion admin corp");
		UUID orderId = UUID.fromString((String) bogotaOrder.get("externalId"));
		UUID itemId = itemExternalIdOf(bogotaOrder);

		// Approve order
		restClient.post()
				.uri("/api/purchases/orders/{id}/approval", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.toBodilessEntity();

		// Corporate admin attempts reception
		ResponseEntity<ErrorBody> response = restClient.post()
				.uri("/api/purchases/orders/{id}/receptions", orderId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("items", List.of(Map.of("itemExternalId", itemId, "receivedQuantity", new BigDecimal("1.0000")))))
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("branch_context_required");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> createOrder(String token, String notes) {
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
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(Map.class);
		assertThat(body).isNotNull();
		return body;
	}

	@SuppressWarnings("unchecked")
	private UUID itemExternalIdOf(Map<String, Object> orderDetail) {
		List<Map<String, Object>> items = (List<Map<String, Object>>) orderDetail.get("items");
		assertThat(items).isNotEmpty();
		return UUID.fromString((String) items.get(0).get("externalId"));
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
