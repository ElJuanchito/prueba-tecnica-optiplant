package com.optiplant.inventory.analytics.domain.model;

import java.util.List;

/**
 * Generic paginated response envelope for analytics queries (design §5).
 */
public record AnalyticsPage<T>(List<T> content, long totalElements, int page, int size) {
}
