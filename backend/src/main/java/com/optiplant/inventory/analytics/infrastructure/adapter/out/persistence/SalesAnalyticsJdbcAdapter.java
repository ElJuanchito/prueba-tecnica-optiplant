package com.optiplant.inventory.analytics.infrastructure.adapter.out.persistence;

import com.optiplant.inventory.analytics.application.port.out.SalesAnalyticsPort;
import com.optiplant.inventory.analytics.domain.model.MonthlySales;
import com.optiplant.inventory.analytics.domain.model.RotationDirection;
import com.optiplant.inventory.analytics.domain.service.RotationPageAssembler.RawRotationRow;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Native-SQL read adapter for sales analytics (design §4 Q-1, Q-2, Q-3, D-4).
 * Reads foreign tables {@code sales}, {@code sale_items}, {@code products}, {@code branches},
 * and {@code branch_inventories} directly via {@link JdbcClient} without JPA entities (P-01).
 * Filters {@code status = 'COMPLETED'} on every sales aggregation (F-2, R-03) and never reads
 * {@code kardex_movements} (F-4, PA-06).
 */
@Component
public class SalesAnalyticsJdbcAdapter implements SalesAnalyticsPort {

	private final JdbcClient jdbcClient;

	public SalesAnalyticsJdbcAdapter(JdbcClient jdbcClient) {
		this.jdbcClient = jdbcClient;
	}

	@Override
	public List<MonthlySales> monthlySales(UUID branchExternalId, Instant from, Instant to) {
		String sql = """
				WITH sale_data AS (
				    SELECT
				        s.id,
				        date_trunc('month', s.created_at) AS month_date,
				        s.total_amount,
				        COALESCE(SUM(si.quantity), 0) AS units_sold
				    FROM sales s
				    JOIN branches b ON b.id = s.branch_id
				    LEFT JOIN sale_items si ON si.sale_id = s.id
				    WHERE b.external_id = :branchExternalId
				      AND s.status = 'COMPLETED'
				      AND s.created_at >= :from
				      AND s.created_at < :to
				    GROUP BY s.id, date_trunc('month', s.created_at), s.total_amount
				)
				SELECT
				    EXTRACT(YEAR FROM month_date)::int AS sales_year,
				    EXTRACT(MONTH FROM month_date)::int AS sales_month,
				    COUNT(id) AS sales_count,
				    COALESCE(SUM(units_sold), 0) AS units_sold,
				    COALESCE(SUM(total_amount), 0) AS total_amount
				FROM sale_data
				GROUP BY month_date
				ORDER BY month_date ASC
				""";

		return jdbcClient.sql(sql)
				.param("branchExternalId", branchExternalId)
				.param("from", Timestamp.from(from))
				.param("to", Timestamp.from(to))
				.query((rs, rowNum) -> new MonthlySales(
						rs.getInt("sales_year"),
						rs.getInt("sales_month"),
						rs.getLong("sales_count"),
						rs.getBigDecimal("units_sold"),
						rs.getBigDecimal("total_amount")
				))
				.list();
	}

	@Override
	public List<RawRotationRow> rotation(UUID branchExternalId, Instant from, Instant to,
			RotationDirection direction, int page, int size) {
		String orderClause = (direction == RotationDirection.BOTTOM)
				? "ORDER BY sales_amount ASC, sku ASC"
				: "ORDER BY sales_amount DESC, sku ASC";

		String sql = """
				WITH product_sales AS (
				    SELECT
				        p.external_id AS product_external_id,
				        p.sku AS sku,
				        p.name AS name,
				        COALESCE(SUM(si.quantity), 0) AS units_sold,
				        COALESCE(SUM(si.subtotal), 0) AS sales_amount,
				        COALESCE(bi.current_stock, 0) AS current_stock
				    FROM sales s
				    JOIN branches b ON b.id = s.branch_id
				    JOIN sale_items si ON si.sale_id = s.id
				    JOIN products p ON p.id = si.product_id
				    LEFT JOIN branch_inventories bi ON (bi.branch_id = s.branch_id AND bi.product_id = p.id)
				    WHERE b.external_id = :branchExternalId
				      AND s.status = 'COMPLETED'
				      AND s.created_at >= :from
				      AND s.created_at < :to
				      AND p.is_active = TRUE
				    GROUP BY p.id, p.external_id, p.sku, p.name, bi.current_stock
				),
				ranked AS (
				    SELECT
				        product_external_id,
				        sku,
				        name,
				        units_sold,
				        sales_amount,
				        CASE
				            WHEN SUM(sales_amount) OVER () = 0 THEN 0.00
				            ELSE ROUND((sales_amount / SUM(sales_amount) OVER ()) * 100, 2)
				        END AS share_percent,
				        CASE
				            WHEN SUM(sales_amount) OVER () = 0 THEN 0.00
				            ELSE ROUND((SUM(sales_amount) OVER (ORDER BY sales_amount DESC, sku ASC) / SUM(sales_amount) OVER ()) * 100, 2)
				        END AS cumulative_share_percent,
				        current_stock
				    FROM product_sales
				)
				SELECT
				    product_external_id,
				    sku,
				    name,
				    units_sold,
				    sales_amount,
				    share_percent,
				    cumulative_share_percent,
				    current_stock
				FROM ranked
				""" + " " + orderClause + """
				 LIMIT :limit OFFSET :offset
				""";

		int limit = Math.max(size, 1);
		long offset = (long) Math.max(page, 0) * limit;

		return jdbcClient.sql(sql)
				.param("branchExternalId", branchExternalId)
				.param("from", Timestamp.from(from))
				.param("to", Timestamp.from(to))
				.param("limit", limit)
				.param("offset", offset)
				.query((rs, rowNum) -> new RawRotationRow(
						rs.getObject("product_external_id", UUID.class),
						rs.getString("sku"),
						rs.getString("name"),
						rs.getBigDecimal("units_sold"),
						rs.getBigDecimal("sales_amount"),
						rs.getBigDecimal("share_percent"),
						rs.getBigDecimal("cumulative_share_percent"),
						rs.getBigDecimal("current_stock")
				))
				.list();
	}

	@Override
	public long rotationCount(UUID branchExternalId, Instant from, Instant to) {
		String sql = """
				SELECT COUNT(DISTINCT si.product_id)
				FROM sales s
				JOIN branches b ON b.id = s.branch_id
				JOIN sale_items si ON si.sale_id = s.id
				JOIN products p ON p.id = si.product_id
				WHERE b.external_id = :branchExternalId
				  AND s.status = 'COMPLETED'
				  AND s.created_at >= :from
				  AND s.created_at < :to
				  AND p.is_active = TRUE
				""";

		Long count = jdbcClient.sql(sql)
				.param("branchExternalId", branchExternalId)
				.param("from", Timestamp.from(from))
				.param("to", Timestamp.from(to))
				.query(Long.class)
				.single();

		return count != null ? count : 0L;
	}
}
