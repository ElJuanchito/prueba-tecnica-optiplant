package com.optiplant.inventory.analytics.domain.service;

import com.optiplant.inventory.analytics.domain.model.AbcClass;
import java.math.BigDecimal;

/**
 * Pure function for ABC / Pareto classification (contract R-09, PA-02, design §4 D-5).
 * Cut-points: A while cumulative share <= 80 %, B while <= 95 %, C beyond.
 */
public final class AbcClassifier {

	private static final BigDecimal THRESHOLD_A = new BigDecimal("80.00");
	private static final BigDecimal THRESHOLD_B = new BigDecimal("95.00");

	private AbcClassifier() {
	}

	public static AbcClass classify(BigDecimal cumulativeSharePercent) {
		if (cumulativeSharePercent == null) {
			return AbcClass.C;
		}
		if (cumulativeSharePercent.compareTo(THRESHOLD_A) <= 0) {
			return AbcClass.A;
		}
		if (cumulativeSharePercent.compareTo(THRESHOLD_B) <= 0) {
			return AbcClass.B;
		}
		return AbcClass.C;
	}
}
