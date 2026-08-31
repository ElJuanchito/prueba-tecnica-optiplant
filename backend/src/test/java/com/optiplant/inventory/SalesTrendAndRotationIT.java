package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
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
 * Verifies exact figures for sales trend and ABC rotation against real PostgreSQL (R-03..R-10).
 * - Exact salesCount, unitsSold, totalAmount, and monthOverMonthVariationPercent
 * - Exact sharePercent, cumulativeSharePercent, abcClass per product
 * - Stable ABC class assignment across pages (R-09)
 * - direction=BOTTOM reverses presentation without changing classification (D-6)
 * - Cancelled/voided sales (status='CANCELLED') are excluded from all figures (R-03)
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SalesTrendAndRotationIT {

	private static final String SEED_PASSWORD = "Password123!";
	private static final UUID BRANCH_BOGOTA = UUID.fromString("b0000000-0000-0000-0000-000000000001");
	private static final UUID PRODUCT_NPK = UUID.fromString("d0000000-0000-0000-0000-000000000001");
	private static final UUID PRODUCT_BIO = UUID.fromString("d0000000-0000-0000-0000-000000000002");
	private static final UUID PRODUCT_FUNG = UUID.fromString("d0000000-0000-0000-0000-000000000004");

	@LocalServerPort
	private int port;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private RestClient restClient;

	@BeforeEach
	void setUp() {
		restClient = RestClient.builder().baseUrl("http://localhost:" + port).build();
		// Clean any previous sales to ensure deterministic assertions
		jdbcTemplate.update("DELETE FROM sale_items");
		jdbcTemplate.update("DELETE FROM sales");
	}

	@Test
	void salesTrendAndRotationAssertExactNumbersAndInvariants() {
		String token = token("gerente.bogota");

		YearMonth currentMonth = YearMonth.now(ZoneOffset.UTC);
		YearMonth prevMonth = currentMonth.minusMonths(1);
		YearMonth threeMonthsAgo = currentMonth.minusMonths(3);

		Instant currentMonthDate = currentMonth.atDay(5).atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant prevMonthDate = prevMonth.atDay(10).atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant threeMonthsAgoDate = threeMonthsAgo.atDay(15).atStartOfDay(ZoneOffset.UTC).toInstant();

		// Seed sales for Branch 1 (Bogotá)
		// Month M (current):
		// S1: BIO qty=2, total=136000.00
		long s1Id = insertSale("VEN-TEST-0001", 1, 2, "Cliente A", new BigDecimal("136000.00"), "COMPLETED", currentMonthDate);
		insertSaleItem(s1Id, 2, new BigDecimal("2.0000"), new BigDecimal("68000.00"), new BigDecimal("136000.00"));

		// S2: FUNG qty=1, total=52000.00
		long s2Id = insertSale("VEN-TEST-0002", 1, 2, "Cliente B", new BigDecimal("52000.00"), "COMPLETED", currentMonthDate);
		insertSaleItem(s2Id, 4, new BigDecimal("1.0000"), new BigDecimal("52000.00"), new BigDecimal("52000.00"));

		// S3: NPK qty=10, total=42000.00
		long s3Id = insertSale("VEN-TEST-0003", 1, 2, "Cliente C", new BigDecimal("42000.00"), "COMPLETED", currentMonthDate);
		insertSaleItem(s3Id, 1, new BigDecimal("10.0000"), new BigDecimal("4200.00"), new BigDecimal("42000.00"));

		// S4 (CANCELLED): NPK qty=5, total=21000.00 - should NOT be counted (R-03)
		long s4Id = insertSale("VEN-TEST-0004", 1, 2, "Cliente D", new BigDecimal("21000.00"), "CANCELLED", currentMonthDate);
		insertSaleItem(s4Id, 1, new BigDecimal("5.0000"), new BigDecimal("4200.00"), new BigDecimal("21000.00"));

		// Month M-1:
		// S5: NPK qty=50, total=200000.00
		long s5Id = insertSale("VEN-TEST-0005", 1, 2, "Cliente E", new BigDecimal("200000.00"), "COMPLETED", prevMonthDate);
		insertSaleItem(s5Id, 1, new BigDecimal("50.0000"), new BigDecimal("4000.00"), new BigDecimal("200000.00"));

		// Month M-2: no sales (zeros)

		// Month M-3:
		// S6: BIO qty=2, total=100000.00
		long s6Id = insertSale("VEN-TEST-0006", 1, 2, "Cliente F", new BigDecimal("100000.00"), "COMPLETED", threeMonthsAgoDate);
		insertSaleItem(s6Id, 2, new BigDecimal("2.0000"), new BigDecimal("50000.00"), new BigDecimal("100000.00"));

		// -------------------------------------------------------------
		// 1. Sales Trend Assertion (R-04, R-05, R-06)
		// -------------------------------------------------------------
		ResponseEntity<Map> trendResponse = restClient.get()
				.uri("/api/analytics/dashboard/sales-trend?months=4")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.retrieve()
				.toEntity(Map.class);

		assertThat(trendResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map<String, Object> trendBody = trendResponse.getBody();
		assertThat(trendBody).isNotNull();
		assertThat(trendBody.get("empty")).isEqualTo(false);

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> months = (List<Map<String, Object>>) trendBody.get("months");
		assertThat(months).hasSize(4);

		// Month M-3 (index 0)
		Map<String, Object> m3 = months.get(0);
		assertThat(((Number) m3.get("salesCount")).longValue()).isEqualTo(1L);
		assertThat(new BigDecimal(m3.get("unitsSold").toString())).isEqualByComparingTo("2.0000");
		assertThat(new BigDecimal(m3.get("totalAmount").toString())).isEqualByComparingTo("100000.00");

		// Month M-2 (index 1) - zero-filled
		Map<String, Object> m2 = months.get(1);
		assertThat(((Number) m2.get("salesCount")).longValue()).isEqualTo(0L);
		assertThat(new BigDecimal(m2.get("unitsSold").toString())).isEqualByComparingTo("0.0000");
		assertThat(new BigDecimal(m2.get("totalAmount").toString())).isEqualByComparingTo("0.00");

		// Month M-1 (index 2)
		Map<String, Object> m1 = months.get(2);
		assertThat(((Number) m1.get("salesCount")).longValue()).isEqualTo(1L);
		assertThat(new BigDecimal(m1.get("unitsSold").toString())).isEqualByComparingTo("50.0000");
		assertThat(new BigDecimal(m1.get("totalAmount").toString())).isEqualByComparingTo("200000.00");

		// Current Month M (index 3) - sum of S1, S2, S3 (S4 cancelled excluded)
		Map<String, Object> mM = months.get(3);
		assertThat(((Number) mM.get("salesCount")).longValue()).isEqualTo(3L);
		assertThat(new BigDecimal(mM.get("unitsSold").toString())).isEqualByComparingTo("13.0000");
		assertThat(new BigDecimal(mM.get("totalAmount").toString())).isEqualByComparingTo("230000.00");

		// Month over month variation: (230000 - 200000) / 200000 * 100 = 15.00 %
		assertThat(new BigDecimal(trendBody.get("monthOverMonthVariationPercent").toString()))
				.isEqualByComparingTo("15.00");

		// -------------------------------------------------------------
		// 2. Rotation & ABC Classification (R-08, R-09, D-5)
		// Total month sales: 230,000.00
		// Product 2 (BIO): 136,000 (59.13%) -> Class A (<= 80%)
		// Product 4 (FUNG): 52,000 (22.61%, cum 81.74%) -> Class B (<= 95%)
		// Product 1 (NPK): 42,000 (18.26%, cum 100.00%) -> Class C (> 95%)
		// -------------------------------------------------------------
		// Page 0, size 2
		ResponseEntity<Map> rotPage0Resp = restClient.get()
				.uri("/api/analytics/dashboard/rotation?page=0&size=2")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.retrieve()
				.toEntity(Map.class);

		assertThat(rotPage0Resp.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map<String, Object> rotPage0 = rotPage0Resp.getBody();
		assertThat(rotPage0).isNotNull();
		assertThat(((Number) rotPage0.get("totalElements")).longValue()).isEqualTo(3L);

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> content0 = (List<Map<String, Object>>) rotPage0.get("content");
		assertThat(content0).hasSize(2);

		// Item 0: BIO -> Class A
		Map<String, Object> item0 = content0.get(0);
		assertThat(item0.get("sku")).isEqualTo("BIO-FOL-AMINO");
		assertThat(new BigDecimal(item0.get("salesAmount").toString())).isEqualByComparingTo("136000.00");
		assertThat(new BigDecimal(item0.get("sharePercent").toString())).isEqualByComparingTo("59.13");
		assertThat(new BigDecimal(item0.get("cumulativeSharePercent").toString())).isEqualByComparingTo("59.13");
		assertThat(item0.get("abcClass")).isEqualTo("A");

		// Item 1: FUNG -> Class B
		Map<String, Object> item1 = content0.get(1);
		assertThat(item1.get("sku")).isEqualTo("FUNG-BIO-TRICH");
		assertThat(new BigDecimal(item1.get("salesAmount").toString())).isEqualByComparingTo("52000.00");
		assertThat(new BigDecimal(item1.get("sharePercent").toString())).isEqualByComparingTo("22.61");
		assertThat(new BigDecimal(item1.get("cumulativeSharePercent").toString())).isEqualByComparingTo("81.74");
		assertThat(item1.get("abcClass")).isEqualTo("B");

		// Page 1, size 2 -> Product NPK must still be Class C and cumulative 100.00%
		ResponseEntity<Map> rotPage1Resp = restClient.get()
				.uri("/api/analytics/dashboard/rotation?page=1&size=2")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.retrieve()
				.toEntity(Map.class);

		assertThat(rotPage1Resp.getStatusCode()).isEqualTo(HttpStatus.OK);
		Map<String, Object> rotPage1 = rotPage1Resp.getBody();
		assertThat(rotPage1).isNotNull();
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> content1 = (List<Map<String, Object>>) rotPage1.get("content");
		assertThat(content1).hasSize(1);

		Map<String, Object> item2 = content1.get(0);
		assertThat(item2.get("sku")).isEqualTo("FERT-NPK-151515");
		assertThat(new BigDecimal(item2.get("salesAmount").toString())).isEqualByComparingTo("42000.00");
		assertThat(new BigDecimal(item2.get("sharePercent").toString())).isEqualByComparingTo("18.26");
		assertThat(new BigDecimal(item2.get("cumulativeSharePercent").toString())).isEqualByComparingTo("100.00");
		assertThat(item2.get("abcClass")).isEqualTo("C");

		// -------------------------------------------------------------
		// 3. Direction BOTTOM reverses presentation only (D-6)
		// -------------------------------------------------------------
		ResponseEntity<Map> bottomResp = restClient.get()
				.uri("/api/analytics/dashboard/rotation?direction=BOTTOM&page=0&size=2")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.retrieve()
				.toEntity(Map.class);

		assertThat(bottomResp.getStatusCode()).isEqualTo(HttpStatus.OK);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> bottomContent = (List<Map<String, Object>>) bottomResp.getBody().get("content");
		assertThat(bottomContent).hasSize(2);
		// Lowest seller NPK comes first in BOTTOM direction, but retains Class C
		assertThat(bottomContent.get(0).get("sku")).isEqualTo("FERT-NPK-151515");
		assertThat(bottomContent.get(0).get("abcClass")).isEqualTo("C");
		assertThat(bottomContent.get(1).get("sku")).isEqualTo("FUNG-BIO-TRICH");
		assertThat(bottomContent.get(1).get("abcClass")).isEqualTo("B");

		// -------------------------------------------------------------
		// 4. Voiding a sale drops out of figures (R-03)
		// Void S1 (BIO 136,000) -> Month M total drops from 230,000 to 94,000
		// -------------------------------------------------------------
		jdbcTemplate.update("UPDATE sales SET status = 'CANCELLED' WHERE id = ?", s1Id);

		ResponseEntity<Map> afterVoidTrend = restClient.get()
				.uri("/api/analytics/dashboard/sales-trend?months=4")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
				.retrieve()
				.toEntity(Map.class);
		@SuppressWarnings("unchecked")
		List<Map<String, Object>> afterMonths = (List<Map<String, Object>>) afterVoidTrend.getBody().get("months");
		Map<String, Object> afterCurrentMonth = afterMonths.get(3);
		assertThat(((Number) afterCurrentMonth.get("salesCount")).longValue()).isEqualTo(2L);
		assertThat(new BigDecimal(afterCurrentMonth.get("unitsSold").toString())).isEqualByComparingTo("11.0000");
		assertThat(new BigDecimal(afterCurrentMonth.get("totalAmount").toString())).isEqualByComparingTo("94000.00");
	}

	private long insertSale(String invoiceNumber, long branchId, long userId, String customerName,
			BigDecimal totalAmount, String status, Instant createdAt) {
		UUID externalId = UUID.randomUUID();
		return jdbcTemplate.queryForObject("""
				INSERT INTO sales (external_id, invoice_number, branch_id, user_id, price_list_id, customer_name, subtotal, discount_amount, tax_amount, total_amount, status, created_at)
				VALUES (?, ?, ?, ?, 1, ?, ?, 0.0000, 0.0000, ?, ?, ?)
				RETURNING id
				""",
				Long.class,
				externalId, invoiceNumber, branchId, userId, customerName, totalAmount, totalAmount, status,
				Timestamp.from(createdAt));
	}

	private void insertSaleItem(long saleId, long productId, BigDecimal quantity, BigDecimal unitPrice,
			BigDecimal subtotal) {
		jdbcTemplate.update("""
				INSERT INTO sale_items (external_id, sale_id, product_id, quantity, list_unit_price, unit_price, discount_percent, subtotal)
				VALUES (?, ?, ?, ?, ?, ?, 0.00, ?)
				""",
				UUID.randomUUID(), saleId, productId, quantity, unitPrice, unitPrice, subtotal);
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
