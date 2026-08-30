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
 * Smoke assertions for {@code /api/sales/**} endpoints — RNF-API-02, RNF-PER-04, and contract §6 (tasks.md 3.7).
 *
 * <ul>
 *   <li>Status and page-envelope shape ({@code content}/{@code totalElements}/{@code page}/{@code size}).</li>
 *   <li>{@code aggregates} present where contract §6 specifies ({@code salesCount}, {@code totalAmount}).</li>
 *   <li>No numeric {@code id} anywhere in payloads (contract §7 "must not leak").</li>
 *   <li>No raw {@code VOID_REASON:} token escaping to the client.</li>
 *   <li>Oversized page rejected with {@code 400 invalid_request}.</li>
 * </ul>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SalesApiSmokeIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID PRODUCT_NPK = UUID.fromString("d0000000-0000-0000-0000-000000000001");

	@LocalServerPort
	private int port;

	private RestClient restClient;
	private String bogotaToken;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		bogotaToken = token("gerente.bogota");
	}

	@Test
	void listSalesReturnsPagedEnvelopeWithAggregatesAndNoNumericId() {
		ResponseEntity<String> response = restClient.get()
				.uri("/api/sales")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.toEntity(String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = response.getBody();
		assertThat(body).isNotNull();
		assertPagedEnvelopeShape(body);
		assertThat(body).contains("\"aggregates\"").contains("\"salesCount\"").contains("\"totalAmount\"");
		assertNoNumericIdLeak(body);
	}

	@Test
	void saleDetailReturnsExpectedShapeWithNoNumericIdAndNoRawVoidReasonToken() {
		UUID saleId = createSale("Cliente Smoke Detalle");

		ResponseEntity<String> response = restClient.get()
				.uri("/api/sales/{id}", saleId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.toEntity(String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(body)
				.contains("\"externalId\"")
				.contains("\"invoiceNumber\"")
				.contains("\"status\"")
				.contains("\"branch\"")
				.contains("\"soldBy\"")
				.contains("\"priceList\"")
				.contains("\"customerName\"")
				.contains("\"subtotal\"")
				.contains("\"discountAmount\"")
				.contains("\"taxAmount\"")
				.contains("\"totalAmount\"")
				.contains("\"items\"");
		assertNoNumericIdLeak(body);
		assertThat(body).doesNotContain("VOID_REASON:");
	}

	@Test
	void saleByInvoiceNumberReturnsExpectedShapeWithNoNumericId() {
		UUID saleId = createSale("Cliente Smoke Factura");
		String invoiceNumber = getInvoiceNumber(saleId);

		ResponseEntity<String> response = restClient.get()
				.uri("/api/sales/by-invoice/{invoiceNumber}", invoiceNumber)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.toEntity(String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(body).contains("\"invoiceNumber\":\"" + invoiceNumber + "\"");
		assertNoNumericIdLeak(body);
	}

	@Test
	void voidSaleReturnsUpdatedStatusAndCleanCancellationReason() {
		UUID saleId = createSale("Cliente Smoke Anular");
		String reason = "Anulacion smoke test";

		ResponseEntity<String> response = restClient.post()
				.uri("/api/sales/{id}/cancellation", saleId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("reason", reason))
				.retrieve()
				.toEntity(String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		String body = response.getBody();
		assertThat(body).isNotNull();
		assertThat(body).contains("\"status\":\"CANCELLED\"");
		assertThat(body).contains("\"cancellationReason\":\"" + reason + "\"");
		assertThat(body).doesNotContain("VOID_REASON:");
		assertNoNumericIdLeak(body);
	}

	@Test
	void oversizedPageSizeIsRejected() {
		ResponseEntity<ErrorBody> response = restClient.get()
				.uri("/api/sales?size=101")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.onStatus(status -> true, (req, res) -> {})
				.toEntity(ErrorBody.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().code()).isEqualTo("invalid_request");
	}

	@SuppressWarnings("unchecked")
	private UUID createSale(String customerName) {
		Map<String, Object> request = Map.of(
				"customerName", customerName,
				"items", List.of(Map.of("productExternalId", PRODUCT_NPK, "quantity", new BigDecimal("1.0000")))
		);
		Map<String, Object> body = restClient.post()
				.uri("/api/sales")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.contentType(MediaType.APPLICATION_JSON)
				.body(request)
				.retrieve()
				.body(Map.class);
		assertThat(body).isNotNull();
		return UUID.fromString((String) body.get("externalId"));
	}

	@SuppressWarnings("unchecked")
	private String getInvoiceNumber(UUID saleExternalId) {
		Map<String, Object> body = restClient.get()
				.uri("/api/sales/{id}", saleExternalId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.body(Map.class);
		assertThat(body).isNotNull();
		return (String) body.get("invoiceNumber");
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
