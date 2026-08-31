package com.optiplant.inventory.analytics.domain.model;

import java.math.BigDecimal;

/**
 * Aggregated sales figures for one calendar month (CU-DSH-01, RF-DSH-01).
 */
public record MonthlySales(int year, int month, long salesCount, BigDecimal unitsSold, BigDecimal totalAmount) {
}
