package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * Integration tests for Customer CRUD, RBAC, unique constraints, and lifecycle (R-C1..R-C5, R-C7, R-C11, §6).
 *
 * <ul>
 *   <li>Duplicate tax id returns {@code 409 customer_tax_id_already_exists} without leaking DB internals.</li>
 *   <li>Multiple customers with null tax id are allowed (D-3).</li>
 *   <li>every internal role may create and edit customers ({@code 201} / {@code 200}); only {@code ADMIN} may deactivate or reactivate one ({@code 403} for the others).</li>
 *   <li>Associating an inactive customer with a new sale returns {@code 409 customer_inactive} while their history remains readable.</li>
 *   <li>No {@code DELETE} route exists ({@code 405 Method Not Allowed}).</li>
 *   <li>Disable / enable lifecycle works and records audit entries with {@code entity_name = 'CUSTOMER'} and {@code branch_id = NULL}.</li>
 *   <li>Oversized page size is rejected with {@code 400 invalid_request}.</li>
 *   <li>No numeric {@code id} is leaked anywhere in responses.</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerCrudIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID SEEDED_ACTIVE_CUSTOMER = UUID.fromString("60000000-0000-0000-0000-000000000001");
	private static final UUID SEEDED_INACTIVE_CUSTOMER = UUID.fromString("60000000-0000-0000-0000-000000000003");
	private static final UUID PRODUCT_NPK = UUID.fromString("d0000000-0000-0000-0000-000000000001");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private RestClient restClient;
	private String adminToken;
	private String managerToken;
	private String operatorToken;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		adminToken = token("admin.corp");
		managerToken = token("gerente.bogota");
		operatorToken = token("operador.bogota");
	}

	@Test
	void duplicateTaxIdReturnsConflictAndMultipleNullTaxIdsBothSucceed() {
		// 1. Attempting to create customer with already seeded tax_id ("900.555.444-1")
		Map<String, Object> duplicateRequest = Map.of(
				"name", "Cliente Duplicado NIT",
				"taxId", "900.555.444-1"
		);

		ResponseEntity<ErrorBody> conflict = restClient.post()
				.uri("/api/sales/customers")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(duplicateRequest)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(conflict.getBody()).isNotNull();
		assertThat(conflict.getBody().code()).isEqualTo("customer_tax_id_already_exists");
		assertThat(conflict.getBody().message()).doesNotContain("uq_customers_tax_id");

		// 2. Creating two customers with null taxId both succeed
		Map<String, Object> nullTax1 = Map.of("name", "Cliente Sin NIT 1 " + suffix());
		Map<String, Object> nullTax2 = Map.of("name", "Cliente Sin NIT 2 " + suffix());

		ResponseEntity<CustomerResponse> res1 = restClient.post()
				.uri("/api/sales/customers")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(nullTax1)
				.retrieve()
				.toEntity(CustomerResponse.class);
		assertThat(res1.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(res1.getBody()).isNotNull();
		assertThat(res1.getBody().taxId()).isNull();

		ResponseEntity<CustomerResponse> res2 = restClient.post()
				.uri("/api/sales/customers")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(nullTax2)
				.retrieve()
				.toEntity(CustomerResponse.class);
		assertThat(res2.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(res2.getBody()).isNotNull();
		assertThat(res2.getBody().taxId()).isNull();
	}

	@Test
	void rbacLetsEveryRoleCreateAndEditCustomersButReservesDeactivationForAdmin() {
		CustomerResponse customer = createCustomerAsAdmin("Cliente RBAC " + suffix(), "950." + suffix());
		UUID customerId = customer.externalId();

		Map<String, Object> createBody = Map.of("name", "Cliente Operador " + suffix());
		Map<String, Object> editBody = Map.of("name", "Cliente Editado " + suffix());

		// OPERATOR: create and edit allowed (the operator is the one billing a new customer at the counter)
		assertThat(postCustomerStatus(operatorToken, createBody)).isEqualTo(HttpStatus.CREATED);
		assertThat(putCustomerStatus(operatorToken, customerId, editBody)).isEqualTo(HttpStatus.OK);
		// OPERATOR: deactivate / reactivate reserved for ADMIN -> 403
		assertThat(patchCustomerStatus(operatorToken, customerId, "disable")).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(patchCustomerStatus(operatorToken, customerId, "enable")).isEqualTo(HttpStatus.FORBIDDEN);
		// OPERATOR: reads -> 200
		assertThat(getCustomerStatus(operatorToken, customerId)).isEqualTo(HttpStatus.OK);
		assertThat(listCustomersStatus(operatorToken)).isEqualTo(HttpStatus.OK);
		assertThat(getCustomerHistoryStatus(operatorToken, customerId)).isEqualTo(HttpStatus.OK);

		// BRANCH_MANAGER: create and edit allowed
		assertThat(postCustomerStatus(managerToken, createBody)).isEqualTo(HttpStatus.CREATED);
		assertThat(putCustomerStatus(managerToken, customerId, editBody)).isEqualTo(HttpStatus.OK);
		// BRANCH_MANAGER: deactivate / reactivate -> 403
		assertThat(patchCustomerStatus(managerToken, customerId, "disable")).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(patchCustomerStatus(managerToken, customerId, "enable")).isEqualTo(HttpStatus.FORBIDDEN);
		// BRANCH_MANAGER: reads -> 200
		assertThat(getCustomerStatus(managerToken, customerId)).isEqualTo(HttpStatus.OK);
		assertThat(listCustomersStatus(managerToken)).isEqualTo(HttpStatus.OK);

		// ADMIN: deactivate / reactivate allowed
		assertThat(patchCustomerStatus(adminToken, customerId, "disable")).isEqualTo(HttpStatus.OK);
		assertThat(patchCustomerStatus(adminToken, customerId, "enable")).isEqualTo(HttpStatus.OK);
	}

	@Test
	void associatingInactiveCustomerWithSaleReturnsCustomerInactiveWhileHistoryRemainsReadable() {
		// Attempting to register a sale with seeded inactive customer (...0003) -> 409 customer_inactive
		Map<String, Object> request = Map.of(
				"customerExternalId", SEEDED_INACTIVE_CUSTOMER,
				"items", List.of(Map.of("productExternalId", PRODUCT_NPK, "quantity", new BigDecimal("1.0000")))
		);

		ResponseEntity<ErrorBody> response = restClient.post()
				.uri("/api/sales")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("customer_inactive");

		// History query for the inactive customer still returns 200 OK
		ResponseEntity<String> historyResponse = restClient.get()
				.uri("/api/sales/customers/{id}/sales", SEEDED_INACTIVE_CUSTOMER)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + managerToken)
				.retrieve()
				.toEntity(String.class);

		assertThat(historyResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void deleteRouteDoesNotExist() {
		ResponseEntity<Void> response = restClient.delete()
				.uri("/api/sales/customers/{id}", SEEDED_ACTIVE_CUSTOMER)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toBodilessEntity();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
	}

	@Test
	void disableAndEnableLifecycleWorksAndWritesAuditLogsWithNullBranch() {
		CustomerResponse customer = createCustomerAsAdmin("Cliente Ciclo Vida " + suffix(), "960." + suffix());
		UUID customerId = customer.externalId();
		assertThat(customer.active()).isTrue();

		// Disable
		CustomerResponse disabled = patchCustomer(customerId, "disable");
		assertThat(disabled.active()).isFalse();

		// Enable
		CustomerResponse enabled = patchCustomer(customerId, "enable");
		assertThat(enabled.active()).isTrue();

		// Check audit logs in DB
		List<Map<String, Object>> auditLogs = jdbcTemplate.queryForList(
				"SELECT action, entity_name, entity_id, branch_id FROM audit_logs "
						+ "WHERE entity_name = 'CUSTOMER' AND entity_id = ? ORDER BY id ASC",
				customerId.toString()
		);

		assertThat(auditLogs).hasSize(3); // CREATE, DISABLE, ENABLE
		assertThat(auditLogs).allMatch(log -> "CUSTOMER".equals(log.get("entity_name")));
		assertThat(auditLogs).allMatch(log -> log.get("branch_id") == null);
		assertThat(auditLogs.stream().map(l -> l.get("action")).toList())
				.containsExactly("CREATE_CUSTOMER", "DISABLE_CUSTOMER", "ENABLE_CUSTOMER");
	}

	@Test
	void searchAndPaginationAndNoNumericIdLeak() {
		// Search by seeded name
		ResponseEntity<String> searchResponse = restClient.get()
				.uri("/api/sales/customers?search=Progreso")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.toEntity(String.class);

		assertThat(searchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		String searchBody = searchResponse.getBody();
		assertThat(searchBody).isNotNull();
		assertThat(searchBody).contains("Agropecuaria El Progreso S.A.S.");
		assertThat(searchBody).doesNotContain("\"id\":");

		// Oversized page size rejection
		ResponseEntity<ErrorBody> pagedError = restClient.get()
				.uri("/api/sales/customers?size=101")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(pagedError.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(pagedError.getBody()).isNotNull();
		assertThat(pagedError.getBody().code()).isEqualTo("invalid_request");
	}

	// --- helpers ---

	private CustomerResponse createCustomerAsAdmin(String name, String taxId) {
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

	private CustomerResponse patchCustomer(UUID customerId, String action) {
		CustomerResponse response = restClient.method(HttpMethod.PATCH)
				.uri("/api/sales/customers/{id}/{action}", customerId, action)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.body(CustomerResponse.class);
		assertThat(response).isNotNull();
		return response;
	}

	private HttpStatus postCustomerStatus(String token, Map<String, Object> body) {
		return HttpStatus.valueOf(restClient.post()
				.uri("/api/sales/customers")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toBodilessEntity()
				.getStatusCode().value());
	}

	private HttpStatus putCustomerStatus(String token, UUID customerId, Map<String, Object> body) {
		return HttpStatus.valueOf(restClient.put()
				.uri("/api/sales/customers/{id}", customerId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.contentType(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toBodilessEntity()
				.getStatusCode().value());
	}

	private HttpStatus patchCustomerStatus(String token, UUID customerId, String action) {
		return HttpStatus.valueOf(restClient.method(HttpMethod.PATCH)
				.uri("/api/sales/customers/{id}/{action}", customerId, action)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toBodilessEntity()
				.getStatusCode().value());
	}

	private HttpStatus getCustomerStatus(String token, UUID customerId) {
		return HttpStatus.valueOf(restClient.get()
				.uri("/api/sales/customers/{id}", customerId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toBodilessEntity()
				.getStatusCode().value());
	}

	private HttpStatus listCustomersStatus(String token) {
		return HttpStatus.valueOf(restClient.get()
				.uri("/api/sales/customers")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toBodilessEntity()
				.getStatusCode().value());
	}

	private HttpStatus getCustomerHistoryStatus(String token, UUID customerId) {
		return HttpStatus.valueOf(restClient.get()
				.uri("/api/sales/customers/{id}/sales", customerId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toBodilessEntity()
				.getStatusCode().value());
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
