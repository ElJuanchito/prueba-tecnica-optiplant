package com.optiplant.inventory.analytics.domain.model;

import java.time.Instant;

/**
 * Time window for analytics queries (design §5).
 */
public record AnalyticsPeriod(Instant from, Instant to, int periodDays) {
}
