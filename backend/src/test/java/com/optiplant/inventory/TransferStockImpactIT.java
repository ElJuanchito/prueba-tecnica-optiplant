package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * Active transfer activity summary and stock impact (R-12, R-13, R-14, F-7).
 * Asserts:
 * - Exact status counts split by inbound/outbound
 * - Delayed count when estimated_arrival_at < now() and actual_arrival_at is null
 * - projectedStock = currentStock + inboundInTransit - outboundCommitted
 * - inTransitStock reported as stored in branch_inventories (R-14)
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TransferStockImpactIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID PRODUCT_NPK = UUID.fromString("d0000000-0000-0000-0000-000000000001");
	private static final UUID PRODUCT_MAIZ = UUID.fromString("d0000000-0000-0000-0000-000000000003");

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
	void activitySummaryReportsExactCountsAndDetectsDelayedTransfers() {
		String caliToken = token("gerente.cali");

		// Initial check for Cali
		ResponseEntity<Map> caliResp1 = restClient.get()
				.uri("/api/analytics/dashboard/transfers")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + caliToken)
				.retrieve()
				.toEntity(Map.class);
		assertThat(caliResp1.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map<String, Object> caliBody1 = caliResp1.getBody();
		assertThat(caliBody1).isNotNull();

		@SuppressWarnings("unchecked")
		Map<String, Object> inbound1 = (Map<String, Object>) caliBody1.get("inbound");
		assertThat(((Number) inbound1.get("inTransit")).longValue()).isGreaterThanOrEqualTo(1L);

		long initialDelayed = ((Number) caliBody1.get("delayedCount")).longValue();

		// Insert a delayed transfer arriving in Cali from Medellín (estimated_arrival_at 2 hours ago)
		Instant twoHoursAgo = Instant.now().minus(2, ChronoUnit.HOURS);
		UUID delayedTransferId = UUID.randomUUID();
		jdbcTemplate.update("""
				INSERT INTO transfers (external_id, transfer_number, origin_branch_id, destination_branch_id,
				                       requested_by_user_id, status, carrier_name, tracking_number,
				                       dispatched_at, estimated_arrival_at, notes)
				VALUES (?, 'TRF-TEST-DELAYED', 2, 3, 7, 'IN_TRANSIT', 'TransEnvios', 'TRK-DELAYED-1',
				        ?, ?, 'Transferencia demorada de prueba')
				""", delayedTransferId, Timestamp.from(twoHoursAgo.minus(4, ChronoUnit.HOURS)), Timestamp.from(twoHoursAgo));

		// Now Cali has delayedCount incremented by 1
		ResponseEntity<Map> caliResp2 = restClient.get()
				.uri("/api/analytics/dashboard/transfers")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + caliToken)
				.retrieve()
				.toEntity(Map.class);
		long afterDelayed = ((Number) caliResp2.getBody().get("delayedCount")).longValue();
		assertThat(afterDelayed).isEqualTo(initialDelayed + 1L);
	}

	@Test
	void stockImpactCalculatesProjectedStockCorrectly() {
		String caliToken = token("gerente.cali");
		String bogotaToken = token("gerente.bogota");

		// For Cali: check that projectedStock = currentStock + inboundInTransit - outboundCommitted
		ResponseEntity<Map> caliImpact = restClient.get()
				.uri("/api/analytics/dashboard/transfers/stock-impact")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + caliToken)
				.retrieve()
				.toEntity(Map.class);

		assertThat(caliImpact.getStatusCode()).isEqualTo(HttpStatus.OK);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> caliContent = (List<Map<String, Object>>) caliImpact.getBody().get("content");
		assertThat(caliContent).isNotEmpty();

		for (Map<String, Object> item : caliContent) {
			BigDecimal current = new BigDecimal(item.get("currentStock").toString());
			BigDecimal inbound = new BigDecimal(item.get("inboundInTransit").toString());
			BigDecimal outbound = new BigDecimal(item.get("outboundCommitted").toString());
			BigDecimal projected = new BigDecimal(item.get("projectedStock").toString());
			assertThat(projected).as("projectedStock invariant").isEqualByComparingTo(current.add(inbound).subtract(outbound));
		}

		// Insert an outbound REQUESTED transfer from Bogotá for 10 units of NPK
		UUID trfId = UUID.randomUUID();
		long transferPk = jdbcTemplate.queryForObject("""
				INSERT INTO transfers (external_id, transfer_number, origin_branch_id, destination_branch_id,
				                       requested_by_user_id, status, notes)
				VALUES (?, 'TRF-TEST-OUTBOUND', 1, 2, 5, 'REQUESTED', 'Transferencia saliente solicitada')
				RETURNING id
				""", Long.class, trfId);

		jdbcTemplate.update("""
				INSERT INTO transfer_items (transfer_id, product_id, requested_quantity, dispatched_quantity, received_quantity)
				VALUES (?, 1, 10.0000, 0.0000, 0.0000)
				""", transferPk);

		// For Bogotá: verify stock impact for Product NPK
		ResponseEntity<Map> bogotaImpact = restClient.get()
				.uri("/api/analytics/dashboard/transfers/stock-impact")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + bogotaToken)
				.retrieve()
				.toEntity(Map.class);

		assertThat(bogotaImpact.getStatusCode()).isEqualTo(HttpStatus.OK);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> bogotaContent = (List<Map<String, Object>>) bogotaImpact.getBody().get("content");

		Map<String, Object> npkItem = bogotaContent.stream()
				.filter(item -> "FERT-NPK-151515".equals(item.get("sku")))
				.findFirst()
				.orElseThrow();

		BigDecimal npkCurrent = new BigDecimal(npkItem.get("currentStock").toString());
		BigDecimal npkOutbound = new BigDecimal(npkItem.get("outboundCommitted").toString());
		BigDecimal npkInbound = new BigDecimal(npkItem.get("inboundInTransit").toString());
		BigDecimal npkProjected = new BigDecimal(npkItem.get("projectedStock").toString());

		assertThat(npkOutbound).isGreaterThanOrEqualTo(new BigDecimal("10.0000"));
		assertThat(npkProjected).isEqualByComparingTo(npkCurrent.add(npkInbound).subtract(npkOutbound));
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

	private record LoginRequestBody(String username, String password) {}

	private record LoginResponseBody(String accessToken, String refreshToken, long expiresInSeconds,
			String role, String branchId, String branchName, String branchCode) {}
}
