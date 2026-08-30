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
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.RestClient;

/**
 * Integration tests for customer association with sales (R-C6, R-C9, T-C1, RNF-INT-01, OQ-1).
 *
 * <ul>
 *   <li>Registering a sale with {@code customerExternalId} sets {@code sales.customer_id} and freezes
 *       the customer's name and tax id snapshot in {@code sales.customer_name} / {@code sales.customer_tax_id}.</li>
 *   <li>Editing the customer afterwards leaves the stored receipt snapshot unchanged while the sale's
 *       {@code customer} object reflects the live updated name (OQ-1).</li>
 *   <li>Supplying both {@code customerExternalId} and body {@code customerName} ignores the body values in favor
 *       of the customer record (R-C9, RNF-SEC-05).</li>
 *   <li>Registering a sale without a customer (walk-in) leaves {@code customer_id} null and {@code customer} object null.</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerSaleAssociationIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID PRODUCT_NPK = UUID.fromString("d0000000-0000-0000-0000-000000000001");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

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
	void registeringSaleWithCustomerStoresFkAndSnapshotAndSubsequentCustomerEditLeavesReceiptSnapshotUnchanged() {
		// 1. Create a customer with known name and tax id
		String initialName = "Cliente Snapshot " + suffix();
		String taxId = "999." + suffix();
		CustomerResponse customer = createCustomer(initialName, taxId);
		UUID customerExternalId = customer.externalId();

		// 2. Register sale with customerExternalId
		Map<String, Object> saleRequest = Map.of(
				"customerExternalId", customerExternalId,
				"notes", "Venta con cliente asociado",
				"items", List.of(
						Map.of("productExternalId", PRODUCT_NPK, "quantity", new BigDecimal("2.0000"))
				)
		);

		@SuppressWarnings("unchecked")
		ResponseEntity<Map> saleResponse = restClient.post()
				.uri("/api/sales")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(saleRequest)
				.retrieve()
				.toEntity(Map.class);

		assertThat(saleResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		Map<String, Object> saleBody = saleResponse.getBody();
		assertThat(saleBody).isNotNull();

		UUID saleExternalId = UUID.fromString((String) saleBody.get("externalId"));
		String invoiceNumber = (String) saleBody.get("invoiceNumber");

		// Assert response contains snapshot fields AND customer object
		assertThat(saleBody.get("customerName")).isEqualTo(initialName);
		assertThat(saleBody.get("customerTaxId")).isEqualTo(taxId);

		@SuppressWarnings("unchecked")
		Map<String, Object> customerRef = (Map<String, Object>) saleBody.get("customer");
		assertThat(customerRef).isNotNull();
		assertThat(customerRef.get("externalId")).isEqualTo(customerExternalId.toString());
		assertThat(customerRef.get("name")).isEqualTo(initialName);
		assertThat(customerRef.get("taxId")).isEqualTo(taxId);

		// Assert in database: customer_id is resolved and set, customer_name and customer_tax_id hold snapshot
		Long dbCustomerId = jdbcTemplate.queryForObject(
				"SELECT id FROM customers WHERE external_id = ?", Long.class, customerExternalId);
		assertThat(dbCustomerId).isNotNull();

		Map<String, Object> dbSale = jdbcTemplate.queryForMap(
				"SELECT customer_id, customer_name, customer_tax_id FROM sales WHERE external_id = ?",
				saleExternalId);
		assertThat(((Number) dbSale.get("customer_id")).longValue()).isEqualTo(dbCustomerId);
		assertThat(dbSale.get("customer_name")).isEqualTo(initialName);
		assertThat(dbSale.get("customer_tax_id")).isEqualTo(taxId);

		// 3. Edit the customer with a new name and tax id
		String updatedName = "Cliente Renombrado " + suffix();
		String updatedTaxId = "888." + suffix();
		editCustomer(customerExternalId, updatedName, updatedTaxId);

		// Verify customer entity has the new name in DB
		String dbCustomerName = jdbcTemplate.queryForObject(
				"SELECT name FROM customers WHERE external_id = ?", String.class, customerExternalId);
		assertThat(dbCustomerName).isEqualTo(updatedName);

		// 4. Re-read sale receipt by externalId and by invoiceNumber
		@SuppressWarnings("unchecked")
		Map<String, Object> fetchedById = restClient.get()
				.uri("/api/sales/{id}", saleExternalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.body(Map.class);
		assertThat(fetchedById).isNotNull();

		// Stored snapshot customerName / customerTaxId MUST be unchanged (the frozen receipt)
		assertThat(fetchedById.get("customerName")).isEqualTo(initialName);
		assertThat(fetchedById.get("customerTaxId")).isEqualTo(taxId);

		// Live customer object MUST reflect the new live values (OQ-1)
		@SuppressWarnings("unchecked")
		Map<String, Object> fetchedCustomerRef = (Map<String, Object>) fetchedById.get("customer");
		assertThat(fetchedCustomerRef).isNotNull();
		assertThat(fetchedCustomerRef.get("externalId")).isEqualTo(customerExternalId.toString());
		assertThat(fetchedCustomerRef.get("name")).isEqualTo(updatedName);
		assertThat(fetchedCustomerRef.get("taxId")).isEqualTo(updatedTaxId);

		// Re-read by invoice number
		@SuppressWarnings("unchecked")
		Map<String, Object> fetchedByInvoice = restClient.get()
				.uri("/api/sales/by-invoice/{invoiceNumber}", invoiceNumber)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.body(Map.class);
		assertThat(fetchedByInvoice).isNotNull();
		assertThat(fetchedByInvoice.get("customerName")).isEqualTo(initialName);
		assertThat(fetchedByInvoice.get("customerTaxId")).isEqualTo(taxId);

		// Database sales table snapshot MUST still hold the original snapshot
		Map<String, Object> dbSaleAfter = jdbcTemplate.queryForMap(
				"SELECT customer_id, customer_name, customer_tax_id FROM sales WHERE external_id = ?",
				saleExternalId);
		assertThat(dbSaleAfter.get("customer_name")).isEqualTo(initialName);
		assertThat(dbSaleAfter.get("customer_tax_id")).isEqualTo(taxId);
	}

	@Test
	void bodyCustomerNameAndTaxIdAreIgnoredWhenCustomerExternalIdIsProvided() {
		String realName = "Cliente Real " + suffix();
		String realTaxId = "777." + suffix();
		CustomerResponse customer = createCustomer(realName, realTaxId);

		// Request specifies both customerExternalId and a spoofed body customerName/customerTaxId
		Map<String, Object> request = Map.of(
				"customerExternalId", customer.externalId(),
				"customerName", "Nombre Suplantado",
				"customerTaxId", "000.000.000-0",
				"items", List.of(
						Map.of("productExternalId", PRODUCT_NPK, "quantity", new BigDecimal("1.0000"))
				)
		);

		@SuppressWarnings("unchecked")
		Map<String, Object> body = restClient.post()
				.uri("/api/sales")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(Map.class);

		assertThat(body).isNotNull();
		// The customer record must win: snapshot receives the record's values, not the body's
		assertThat(body.get("customerName")).isEqualTo(realName);
		assertThat(body.get("customerTaxId")).isEqualTo(realTaxId);

		@SuppressWarnings("unchecked")
		Map<String, Object> customerRef = (Map<String, Object>) body.get("customer");
		assertThat(customerRef).isNotNull();
		assertThat(customerRef.get("name")).isEqualTo(realName);
		assertThat(customerRef.get("taxId")).isEqualTo(realTaxId);
	}

	@Test
	void walkInSaleWithoutCustomerLeavesCustomerIdNullAndCustomerObjectNull() {
		String walkInName = "Cliente Mostrador " + suffix();
		Map<String, Object> request = Map.of(
				"customerName", walkInName,
				"items", List.of(
						Map.of("productExternalId", PRODUCT_NPK, "quantity", new BigDecimal("1.0000"))
				)
		);

		@SuppressWarnings("unchecked")
		Map<String, Object> body = restClient.post()
				.uri("/api/sales")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(Map.class);

		assertThat(body).isNotNull();
		UUID saleExternalId = UUID.fromString((String) body.get("externalId"));
		assertThat(body.get("customerName")).isEqualTo(walkInName);
		assertThat(body.get("customerTaxId")).isNull();
		assertThat(body.get("customer")).isNull();

		// Database check
		Map<String, Object> dbSale = jdbcTemplate.queryForMap(
				"SELECT customer_id, customer_name, customer_tax_id FROM sales WHERE external_id = ?",
				saleExternalId);
		assertThat(dbSale.get("customer_id")).isNull();
		assertThat(dbSale.get("customer_name")).isEqualTo(walkInName);
		assertThat(dbSale.get("customer_tax_id")).isNull();
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

	private void editCustomer(UUID externalId, String newName, String newTaxId) {
		Map<String, Object> request = Map.of(
				"name", newName,
				"taxId", newTaxId,
				"email", "updated." + suffix() + "@optiplant.com",
				"phone", "+57 300 7654321",
				"address", "Carrera 50 #80-90"
		);
		restClient.put()
				.uri("/api/sales/customers/{id}", externalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.toBodilessEntity();
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
