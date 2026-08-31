package com.optiplant.inventory.analytics.domain.service;

import com.optiplant.inventory.analytics.domain.model.AbcClass;
import com.optiplant.inventory.analytics.domain.model.AnalyticsPage;
import com.optiplant.inventory.analytics.domain.model.RotationLine;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Pure assembler joining raw rotation rows from persistence to {@link AbcClassifier}
 * and {@link CoveragePolicy} (contract R-08, R-09, R-10, design §4 D-5, D-7, §5).
 */
public final class RotationPageAssembler {

	private RotationPageAssembler() {
	}

	public record RawRotationRow(UUID productExternalId, String sku, String name, BigDecimal unitsSold,
			BigDecimal salesAmount, BigDecimal sharePercent, BigDecimal cumulativeSharePercent,
			BigDecimal currentStock) {
	}

	public static RotationLine assembleLine(RawRotationRow row, int periodDays) {
		AbcClass abcClass = AbcClassifier.classify(row.cumulativeSharePercent());
		BigDecimal coverageDays = CoveragePolicy.calculateCoverageDays(row.currentStock(), row.unitsSold(), periodDays);
		return new RotationLine(row.productExternalId(), row.sku(), row.name(), row.unitsSold(),
				row.salesAmount(), row.sharePercent(), row.cumulativeSharePercent(), abcClass, coverageDays);
	}

	public static AnalyticsPage<RotationLine> assemblePage(List<RawRotationRow> rows, long totalElements,
			int page, int size, int periodDays) {
		List<RotationLine> lines = rows.stream()
				.map(r -> assembleLine(r, periodDays))
				.toList();
		return new AnalyticsPage<>(lines, totalElements, page, size);
	}
}
