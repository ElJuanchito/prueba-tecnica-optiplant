package com.optiplant.inventory.logistics.domain.model;

/**
 * The closed allow-list of route sort criteria (RF-LOG-03, §3.5 "clasificar rutas por
 * prioridad, costo o tiempo"). Each value fixes a field <em>and</em> a direction at once,
 * rather than pairing a field enum with a separate ascending/descending flag: the caller only
 * ever asks one of three fixed business questions — cheapest first, fastest first, or most
 * urgent first — never an arbitrary column/order combination.
 *
 * <p>{@link #PRIORITY_DESC} is the deliberate trap. {@code priority_level} is a {@code VARCHAR}
 * restricted by its {@code CHECK} to {@code LOW}, {@code STANDARD}, {@code URGENT}, and a plain
 * alphabetical {@code ORDER BY priority_level} yields exactly {@code LOW, STANDARD, URGENT} —
 * the reverse of what "priority descending" means to the business, where urgent routes must
 * come first. The persistence adapter resolves this value through an explicit business-rank
 * expression, never a string comparison, so the alphabetical trap cannot resurface silently.
 *
 * <p>{@link #parse(String)} rejects anything outside these three literals with
 * {@link IllegalArgumentException}, so an arbitrary {@code sort} value is never interpolated
 * into a query — the same closed-allow-list shape as {@code catalog}'s {@code ProductSort}.
 */
public enum RouteSort {

	COST_ASC,
	DURATION_ASC,
	PRIORITY_DESC;

	public static RouteSort parse(String raw) {
		if (raw == null) {
			throw new IllegalArgumentException("sort must be one of: cost_asc, duration_asc, priority_desc");
		}
		return switch (raw) {
			case "cost_asc" -> COST_ASC;
			case "duration_asc" -> DURATION_ASC;
			case "priority_desc" -> PRIORITY_DESC;
			default -> throw new IllegalArgumentException("sort must be one of: cost_asc, duration_asc, priority_desc");
		};
	}
}
