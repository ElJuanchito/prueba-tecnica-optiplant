package com.optiplant.inventory.analytics.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.analytics.application.port.out.BranchBoardPort;
import com.optiplant.inventory.analytics.domain.model.AnalyticsPage;
import com.optiplant.inventory.analytics.domain.model.BranchPerformance;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Native-SQL read adapter for the corporate comparative board across active branches (design §4 Q-7).
 * Reads {@code branches}, {@code sales}, {@code sale_items}, {@code branch_inventories}, and {@code transfers}
 * via {@link JdbcClient} without JPA entities (P-01). Uses weighted average cost for valuation (R-22, RN-03)
 * and joins {@code sale_items} once across the whole month (A-6, DT-14 mitigation).
 */
@Component
public class BranchBoardJdbcAdapter implements BranchBoardPort {

	private final JdbcClient jdbcClient;

	public BranchBoardJdbcAdapter(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public AnalyticsPage<BranchPerformance> corporateBoard(int year, int month, String sort,
			String direction, int page, int size) {
		if (month < 1 || month > 12) {
			throw new IllegalArgumentException("month must be between 1 and 12");
		}

		String sortColumn;
		if (sort == null || sort.isBlank() || "salesAmount".equalsIgnoreCase(sort) || "sales_amount".equalsIgnoreCase(sort)) {
			sortColumn = "sales_amount";
		} else if ("salesCount".equalsIgnoreCase(sort) || "sales_count".equalsIgnoreCase(sort)) {
			sortColumn = "sales_count";
		} else if ("unitsSold".equalsIgnoreCase(sort) || "units_sold".equalsIgnoreCase(sort)) {
			sortColumn = "units_sold";
		} else if ("inventoryValue".equalsIgnoreCase(sort) || "inventory_value".equalsIgnoreCase(sort)) {
			sortColumn = "inventory_value";
		} else if ("criticalProductCount".equalsIgnoreCase(sort) || "critical_product_count".equalsIgnoreCase(sort)) {
			sortColumn = "critical_product_count";
		} else if ("activeTransferCount".equalsIgnoreCase(sort) || "active_transfer_count".equalsIgnoreCase(sort)) {
			sortColumn = "active_transfer_count";
		} else if ("code".equalsIgnoreCase(sort)) {
			sortColumn = "b.code";
		} else if ("name".equalsIgnoreCase(sort)) {
			sortColumn = "b.name";
		} else {
			throw new IllegalArgumentException("Invalid sort key: " + sort);
		}

		String dir = "DESC";
		if (direction != null && !direction.isBlank()) {
			if ("ASC".equalsIgnoreCase(direction)) {
				dir = "ASC";
			} else if ("DESC".equalsIgnoreCase(direction)) {
				dir = "DESC";
			} else {
				throw new IllegalArgumentException("Invalid direction: " + direction);
			}
		}

		YearMonth yearMonth = YearMonth.of(year, month);
		Instant from = yearMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant to = yearMonth.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

		String countSql = "SELECT COUNT(*) FROM branches WHERE is_active = TRUE";
		Long total = jdbcClient.sql(countSql).query(Long.class).single();
		long totalElements = total != null ? total : 0L;

		int limit = Math.max(size, 1);
		long offset = (long) Math.max(page, 0) * limit;

		String querySql = """
				WITH month_sales AS (
				    SELECT
				        s.branch_id,
				        COUNT(DISTINCT s.id) AS sales_count,
				        COALESCE(SUM(s.total_amount), 0) AS sales_amount,
				        COALESCE(SUM(si.quantity), 0) AS units_sold
				    FROM sales s
				    LEFT JOIN sale_items si ON si.sale_id = s.id
				    WHERE s.status = 'COMPLETED'
				      AND s.created_at >= :from
				      AND s.created_at < :to
				    GROUP BY s.branch_id
				),
				inventory_vals AS (
				    SELECT
				        bi.branch_id,
				        COALESCE(SUM(bi.current_stock * bi.average_cost), 0) AS inventory_value,
				        COUNT(CASE WHEN bi.current_stock <= bi.min_stock_threshold THEN 1 END) AS critical_product_count
				    FROM branch_inventories bi
				    JOIN products p ON p.id = bi.product_id
				    WHERE p.is_active = TRUE
				    GROUP BY bi.branch_id
				),
				active_transfers AS (
				    SELECT
				        b.id AS branch_id,
				        COUNT(DISTINCT t.id) AS active_transfer_count
				    FROM branches b
				    LEFT JOIN transfers t ON (
				        (t.origin_branch_id = b.id OR t.destination_branch_id = b.id)
				        AND t.status IN ('REQUESTED', 'IN_PREPARATION', 'IN_TRANSIT')
				    )
				    WHERE b.is_active = TRUE
				    GROUP BY b.id
				)
				SELECT
				    b.external_id AS branch_external_id,
				    b.code AS code,
				    b.name AS name,
				    COALESCE(ms.sales_amount, 0) AS sales_amount,
				    COALESCE(ms.sales_count, 0) AS sales_count,
				    COALESCE(ms.units_sold, 0) AS units_sold,
				    COALESCE(iv.inventory_value, 0) AS inventory_value,
				    COALESCE(iv.critical_product_count, 0) AS critical_product_count,
				    COALESCE(at.active_transfer_count, 0) AS active_transfer_count
				FROM branches b
				LEFT JOIN month_sales ms ON ms.branch_id = b.id
				LEFT JOIN inventory_vals iv ON iv.branch_id = b.id
				LEFT JOIN active_transfers at ON at.branch_id = b.id
				WHERE b.is_active = TRUE
				ORDER BY """ + " " + sortColumn + " " + dir + ", b.code ASC " + """
				LIMIT :limit OFFSET :offset
				""";

		List<BranchPerformance> content = jdbcClient.sql(querySql)
				.param("from", from)
				.param("to", to)
				.param("limit", limit)
				.param("offset", offset)
				.query((rs, rowNum) -> new BranchPerformance(
						rs.getObject("branch_external_id", UUID.class),
						rs.getString("code"),
						rs.getString("name"),
						rs.getBigDecimal("sales_amount"),
						rs.getLong("sales_count"),
						rs.getBigDecimal("units_sold"),
						rs.getBigDecimal("inventory_value"),
						rs.getLong("critical_product_count"),
						rs.getLong("active_transfer_count")
				))
				.list();

		return new AnalyticsPage<>(content, totalElements, Math.max(page, 0), limit);
	}
}
