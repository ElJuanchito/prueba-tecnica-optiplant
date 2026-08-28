package com.optiplant.inventory.catalog.domain.model;

/**
 * The closed allow-list of product sort fields (R-12, contract §6.2). Nothing
 * outside this enum can reach a query: {@link #parse(String)} rejects anything
 * else with {@link IllegalArgumentException}, so {@code sort=(select 1)} becomes
 * {@code 400 invalid_request} and is never interpolated. The web layer defaults
 * to {@link #SKU} when the {@code sort} parameter is absent.
 */
public enum ProductSort {

	SKU,
	NAME,
	CREATED_AT;

	public static ProductSort parse(String raw) {
		if (raw == null) {
			throw new IllegalArgumentException("sort must be one of: sku, name, createdAt");
		}
		return switch (raw) {
			case "sku" -> SKU;
			case "name" -> NAME;
			case "createdAt" -> CREATED_AT;
			default -> throw new IllegalArgumentException("sort must be one of: sku, name, createdAt");
		};
	}
}
