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
 * Branch isolation and RBAC for {@code sales} against a real PostgreSQL 17 (Testcontainers)
 * — R-25, contract §5 (tasks.md 3.4).
 * Named {@code SaleBranchIsolationIT} (three {@code *BranchIsolationIT} already exist).
 *
 * <ul>
 *   <li>An actor of branch A gets {@code 404 sale_not_found} for a sale belonging to branch B.</li>
 *   <li>Listing sales is scoped to the actor's branch.</li>
 *   <li>An {@code OPERATOR} is refused the void ({@code 403}).</li>
 *   <li>A corporate {@code ADMIN} registering a sale gets {@code 403 branch_context_required}.</li>
 *   <li>Corporate {@code ADMIN} can read sales network-wide.</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SaleBranchIsolationIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID PRODUCT_NPK = UUID.fromString("d0000000-0000-0000-0000-000000000001");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private RestClient restClient;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
	}

	@Test
	void actorOfBranchAGetsSaleNotFoundForSaleOfBranchB() {
		String medellinToken = token("gerente.medellin");
		String bogotaToken = token("gerente.bogota");

		// Medellín creates a sale
		Map<String, Object> sale = createSale(medellinToken, "Cliente Medellín");
		UUID saleExternalId = UUID.fromString((String) sale.get("externalId"));
		String invoiceNumber = (String) sale.get("invoiceNumber");

		// Bogotá tries to fetch detail by UUID -> 404 sale_not_found (never 403)
		ResponseEntity<ErrorBody> detailResponse = restClient.get()
				.uri("/api/sales/{id}", saleExternalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(detailResponse.getBody()).isNotNull();
		assertThat(detailResponse.getBody().code()).isEqualTo("sale_not_found");

		// Bogotá tries to fetch detail by invoice number -> 404 sale_not_found
		ResponseEntity<ErrorBody> invoiceResponse = restClient.get()
				.uri("/api/sales/by-invoice/{invoiceNumber}", invoiceNumber)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(invoiceResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
		assertThat(invoiceResponse.getBody()).isNotNull();
		assertThat(invoiceResponse.getBody().code()).isEqualTo("sale_not_found");
	}

	@Test
	void listingSalesIsScopedToActorsBranch() {
		String medellinToken = token("gerente.medellin");
		String bogotaToken = token("gerente.bogota");

		Map<String, Object> medellinSale = createSale(medellinToken, "Cliente Med Exclusivo");
		String medellinInvoice = (String) medellinSale.get("invoiceNumber");

		@SuppressWarnings("unchecked")
		Map<String, Object> bogotaPage = restClient.get()
				.uri("/api/sales?size=100")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.body(Map.class);

		assertThat(bogotaPage).isNotNull();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> content = (List<Map<String, Object>>) bogotaPage.get("content");
		assertThat(content).noneMatch(s -> medellinInvoice.equals(s.get("invoiceNumber")));
	}

	@Test
	void operatorIsRefusedTheVoid() {
		String bogotaManager = token("gerente.bogota");
		String bogotaOperator = token("operador.bogota");

		Map<String, Object> sale = createSale(bogotaManager, "Cliente Bogotá");
		UUID saleExternalId = UUID.fromString((String) sale.get("externalId"));

		ResponseEntity<Void> voidResponse = restClient.post()
				.uri("/api/sales/{id}/cancellation", saleExternalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaOperator)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("reason", "Intento de operador"))
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toBodilessEntity();

		assertThat(voidResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
	}

	@Test
	void corporateAdminRegisteringSaleGetsBranchContextRequired() {
		String adminToken = token("admin.corp");

		Map<String, Object> request = Map.of(
				"customerName", "Cliente Corp",
				"items", List.of(Map.of("productExternalId", PRODUCT_NPK, "quantity", new BigDecimal("1.0000")))
		);

		ResponseEntity<ErrorBody> response = restClient.post()
				.uri("/api/sales")
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
	void corporateAdminCanReadSalesNetworkWide() {
		String medellinToken = token("gerente.medellin");
		String adminToken = token("admin.corp");

		Map<String, Object> sale = createSale(medellinToken, "Cliente Global");
		UUID saleExternalId = UUID.fromString((String) sale.get("externalId"));
		String invoiceNumber = (String) sale.get("invoiceNumber");

		ResponseEntity<String> detailResponse = restClient.get()
				.uri("/api/sales/{id}", saleExternalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.toEntity(String.class);

		assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(detailResponse.getBody()).contains("\"invoiceNumber\":\"" + invoiceNumber + "\"");

		ResponseEntity<String> invoiceResponse = restClient.get()
				.uri("/api/sales/by-invoice/{invoiceNumber}", invoiceNumber)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
				.retrieve()
				.toEntity(String.class);

		assertThat(invoiceResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(invoiceResponse.getBody()).contains("\"externalId\":\"" + saleExternalId + "\"");
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> createSale(String token, String customerName) {
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
