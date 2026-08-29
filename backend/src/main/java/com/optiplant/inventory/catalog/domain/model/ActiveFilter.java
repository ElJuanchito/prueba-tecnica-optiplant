package com.optiplant.inventory.catalog.domain.model;

/**
 * R-12's tri-state listing filter. The listing returns only active rows unless
 * the caller asks otherwise, so {@link #ACTIVE} is the default the web layer
 * applies when the {@code active} query parameter is absent.
 *
 * <p>{@link #parse(String)} accepts exactly {@code "true"}, {@code "false"} and
 * {@code "all"} and throws {@link IllegalArgumentException} on anything else, so
 * {@code active=maybe} becomes {@code 400 invalid_request} rather than a Spring
 * type-mismatch page — and {@code all} stays expressible, which a direct
 * {@code Boolean} binding could not do.
 */
public enum ActiveFilter {

	ACTIVE,
	INACTIVE,
	ALL;

	public static ActiveFilter parse(String raw) {
		if (raw == null) {
			throw new IllegalArgumentException("active must be one of: true, false, all");
		}
		return switch (raw) {
			case "true" -> ACTIVE;
			case "false" -> INACTIVE;
			case "all" -> ALL;
			default -> throw new IllegalArgumentException("active must be one of: true, false, all");
		};
	}
}
