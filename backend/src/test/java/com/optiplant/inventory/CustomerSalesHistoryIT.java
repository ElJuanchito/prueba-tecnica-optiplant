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
 * Integration tests for per-customer sales history (R-C12, R-C13, R-C14, R-C15, RNF-SEC-03).
 *
 * <ul>
 *   <li>Branch isolation: a {@code BRANCH_MANAGER} calling {@code GET /api/sales/customers/{id}/sales} sees only
 *       their branch's sales for that customer, and aggregates cover only those.</li>
 *   <li>An {@code ADMIN} reads network-wide and sees sales across all branches with matching total aggregates.</li>
 *   <li>Unknown customer ID returns {@code 404 customer_not_found}.</li>
 *   <li>Known customer with no sales in scope returns {@code 200 OK} with empty content and zeroed aggregates.</li>
 *   <li>A sale registered without a customer appears in {@code GET /api/sales} and in no customer's history.</li>
 *   <li>Oversized page size is rejected with {@code 400 invalid_request}.</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerSalesHistoryIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID PRODUCT_NPK = UUID.fromString("d0000000-0000-0000-0000-000000000001");

	@LocalServerPort
	private int port;

	private RestClient restClient;
	private String adminToken;
	private String bogotaToken;
	private String medellinToken;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		adminToken = token("admin.corp");
		bogotaToken = token("gerente.bogota");
		medellinToken = token("gerente.medellin");
	}

	@Test
	void branchManagerSeesOnlyBranchSalesAndAggregatesWhileAdminSeesAllBranches() {
		// 1. Create a customer for history testing
		CustomerResponse customer = createCustomer("Cliente Historial Multi-Sucursal " + suffix(), "910." + suffix());
		UUID customerExternalId = customer.externalId();

		// 2. Bogotá creates a sale for this customer
		Map<String, Object> bogotaSale = createSale(bogotaToken, customerExternalId, new BigDecimal("2.0000"));
		UUID bogotaSaleId = UUID.fromString((String) bogotaSale.get("externalId"));
		BigDecimal bogotaTotal = new BigDecimal(bogotaSale.get("totalAmount").toString());

		// 3. Medellín creates a sale for the same customer
		Map<String, Object> medellinSale = createSale(medellinToken, customerExternalId, new BigDecimal("3.0000"));
		UUID medellinSaleId = UUID.fromString((String) medellinSale.get("externalId"));
		BigDecimal medellinTotal = new BigDecimal(medellinSale.get("totalAmount").toString());

		// 4. Bogotá branch manager queries customer sales history
		@SuppressWarnings("unchecked")
		Map<String, Object> bogotaHistory = restClient.get()
				.uri("/api/sales/customers/{id}/sales", customerExternalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.body(Map.class);

		assertThat(bogotaHistory).isNotNull();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> bogotaContent = (List<Map<String, Object>>) bogotaHistory.get("content");
		assertThat(bogotaContent).hasSize(1);
		assertThat(bogotaContent.get(0).get("externalId")).isEqualTo(bogotaSaleId.toString());

		@SuppressWarnings("unchecked")
		Map<String, Object> bogotaAggregates = (Map<String, Object>) bogotaHistory.get("aggregates");
		assertThat(bogotaAggregates).isNotNull();
		assertThat(((Number) bogotaAggregates.get("salesCount")).longValue()).isEqualTo(1L);
		assertThat(new BigDecimal(bogotaAggregates.get("totalAmount").toString())).isEqualByComparingTo(bogotaTotal);

		// 5. Admin queries customer sales history across all branches
		@SuppressWarnings("unchecked")
		Map<String, Object> adminHistory = restClient.get()
				.uri("/api/sales/customers/{id}/sales", customerExternalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.body(Map.class);

		assertThat(adminHistory).isNotNull();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> adminContent = (List<Map<String, Object>>) adminHistory.get("content");
		List<String> adminSaleIds = adminContent.stream()
				.map(s -> (String) s.get("externalId"))
				.toList();
		assertThat(adminSaleIds).contains(bogotaSaleId.toString(), medellinSaleId.toString());

		@SuppressWarnings("unchecked")
		Map<String, Object> adminAggregates = (Map<String, Object>) adminHistory.get("aggregates");
		assertThat(adminAggregates).isNotNull();
		assertThat(((Number) adminAggregates.get("salesCount")).longValue()).isEqualTo(2L);
		assertThat(new BigDecimal(adminAggregates.get("totalAmount").toString()))
				.isEqualByComparingTo(bogotaTotal.add(medellinTotal));
	}

	@Test
	void unknownCustomerReturns404CustomerNotFound() {
		UUID missingCustomer = UUID.randomUUID();

		ResponseEntity<ErrorBody> response = restClient.get()
				.uri("/api/sales/customers/{id}/sales", missingCustomer)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("customer_not_found");
	}

	@Test
	void knownCustomerWithNoSalesReturns200WithEmptyContentAndZeroedAggregates() {
		// Create customer with no sales
		CustomerResponse customer = createCustomer("Cliente Sin Ventas " + suffix(), "920." + suffix());

		@SuppressWarnings("unchecked")
		Map<String, Object> history = restClient.get()
				.uri("/api/sales/customers/{id}/sales", customer.externalId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.body(Map.class);

		assertThat(history).isNotNull();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> content = (List<Map<String, Object>>) history.get("content");
		assertThat(content).isEmpty();
		assertThat(((Number) history.get("totalElements")).longValue()).isEqualTo(0L);

		@SuppressWarnings("unchecked")
		Map<String, Object> aggregates = (Map<String, Object>) history.get("aggregates");
		assertThat(aggregates).isNotNull();
		assertThat(((Number) aggregates.get("salesCount")).longValue()).isEqualTo(0L);
		assertThat(new BigDecimal(aggregates.get("totalAmount").toString())).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void saleWithoutCustomerAppearsInGeneralSalesListingAndNotInAnyCustomerHistory() {
		CustomerResponse customer = createCustomer("Cliente Test Aislado " + suffix(), "930." + suffix());

		// Register a walk-in sale (no customerExternalId)
		Map<String, Object> walkInSale = createWalkInSale(bogotaToken, "Cliente Anonimo " + suffix());
		UUID walkInSaleId = UUID.fromString((String) walkInSale.get("externalId"));

		// Appears in general sales listing for Bogotá
		@SuppressWarnings("unchecked")
		Map<String, Object> generalSales = restClient.get()
				.uri("/api/sales?size=100")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.body(Map.class);

		assertThat(generalSales).isNotNull();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> generalContent = (List<Map<String, Object>>) generalSales.get("content");
		assertThat(generalContent).anyMatch(s -> walkInSaleId.toString().equals(s.get("externalId")));

		// Does NOT appear in customer's history
		@SuppressWarnings("unchecked")
		Map<String, Object> customerHistory = restClient.get()
				.uri("/api/sales/customers/{id}/sales", customer.externalId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.body(Map.class);

		assertThat(customerHistory).isNotNull();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> customerContent = (List<Map<String, Object>>) customerHistory.get("content");
		assertThat(customerContent).noneMatch(s -> walkInSaleId.toString().equals(s.get("externalId")));
	}

	@Test
	void oversizedPageSizeInHistoryIsRejected() {
		CustomerResponse customer = createCustomer("Cliente Paging " + suffix(), "940." + suffix());

		ResponseEntity<ErrorBody> response = restClient.get()
				.uri("/api/sales/customers/{id}/sales?size=101", customer.externalId())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("invalid_request");
	}

	// --- helpers ---

	private CustomerResponse createCustomer(String name, String taxId) {
		Map<String, Object> request = Map.of(
				"name", name,
				"taxId", taxId,
				"email", "test." + suffix() + "@optiplant.com",
				"phone", "+57 300 1234567",
				"address", "Calle 100 #20-30"
		);
		CustomerResponse response = restClient.post()
				.uri("/api/sales/customers")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(CustomerResponse.class);
		assertThat(response).isNotNull();
		return response;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> createSale(String token, UUID customerExternalId, BigDecimal quantity) {
		Map<String, Object> request = Map.of(
				"customerExternalId", customerExternalId,
				"items", List.of(Map.of("productExternalId", PRODUCT_NPK, "quantity", quantity))
		);
		Map<String, Object> body = restClient.post()
				.uri("/api/sales")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(Map.class);
		assertThat(body).isNotNull();
		return body;
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> createWalkInSale(String token, String customerName) {
		Map<String, Object> request = Map.of(
				"customerName", customerName,
				"items", List.of(Map.of("productExternalId", PRODUCT_NPK, "quantity", new BigDecimal("1.0000")))
		);
		Map<String, Object> body = restClient.post()
				.uri("/api/sales")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(Map.class);
		assertThat(body).isNotNull();
		return body;
	}

	private String token(String username) {
		LoginResponseBody body = restClient.post()
				.uri("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.body(new LoginRequestBody(username, SEED_PASSWORD))
				.retrieve()
				.body(LoginResponseBody.class);
		assertThat(body).isNotNull();
		return body.accessToken();
	}

	private static String suffix() {
		return UUID.randomUUID().toString().substring(0, 8);
	}

	private record LoginRequestBody(String username, String password) {
	}

	private record LoginResponseBody(String accessToken, String refreshToken, long expiresInSeconds, String role,
			String branchId, String branchName, String branchCode) {
	}

	private record ErrorBody(String code, String message) {
	}

	private record CustomerResponse(
			UUID externalId,
			String name,
			String taxId,
			String email,
			String phone,
			String address,
			boolean active,
			String createdAt,
			String updatedAt
	) {
	}
}
