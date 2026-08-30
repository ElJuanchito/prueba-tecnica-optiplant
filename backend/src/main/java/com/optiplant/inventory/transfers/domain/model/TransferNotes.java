package com.optiplant.inventory.transfers.domain.model;

import java.util.ArrayList;
import java.util.List;

/**
 * The sole reader and writer of the F-1 priority token persisted in {@code transfers.notes}
 * (design §3.5). {@code render()} writes {@code PRIORITY:<LOW|STANDARD|URGENT>} as the first
 * line and joins the observations after it; {@link #parse(String)} reads it back.
 *
 * <p><strong>Notes with no {@code PRIORITY:} first line parse to {@link TransferPriority#STANDARD}
 * with the whole text as observations</strong> — not defensiveness but a requirement:
 * {@code 02-seed-data.sql:192} seeds a transfer whose notes are free prose, and this parser MUST
 * NOT throw on it.
 *
 * <p>{@link #observations()} is what the API exposes (contract §6); the token itself never
 * leaves this type.
 */
public record TransferNotes(TransferPriority priority, List<String> observations) {

	private static final String TOKEN_PREFIX = "PRIORITY:";

	public TransferNotes {
		if (priority == null) {
			priority = TransferPriority.STANDARD;
		}
		observations = observations == null ? List.of() : List.copyOf(observations);
	}

	public static TransferNotes empty(TransferPriority priority) {
		return new TransferNotes(priority, List.of());
	}

	/**
	 * @param raw the persisted {@code transfers.notes} column, possibly {@code null}
	 * @return {@link TransferPriority#STANDARD} with the whole text as a single observation when
	 *     {@code raw} carries no {@code PRIORITY:} first line
	 */
	public static TransferNotes parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return empty(TransferPriority.STANDARD);
		}
		String[] lines = raw.split("\n", -1);
		if (lines[0].startsWith(TOKEN_PREFIX)) {
			TransferPriority parsedPriority = parsePriority(lines[0].substring(TOKEN_PREFIX.length()).strip());
			List<String> rest = new ArrayList<>();
			for (int i = 1; i < lines.length; i++) {
				if (!lines[i].isBlank()) {
					rest.add(lines[i]);
				}
			}
			return new TransferNotes(parsedPriority, rest);
		}
		return new TransferNotes(TransferPriority.STANDARD, List.of(raw));
	}

	private static TransferPriority parsePriority(String token) {
		try {
			return TransferPriority.valueOf(token);
		} catch (IllegalArgumentException ex) {
			return TransferPriority.STANDARD;
		}
	}

	/** The persisted shape: {@code PRIORITY:<level>} followed by one observation per line. */
	public String render() {
		StringBuilder rendered = new StringBuilder(TOKEN_PREFIX).append(priority.name());
		for (String observation : observations) {
			rendered.append('\n').append(observation);
		}
		return rendered.toString();
	}

	/** Returns a copy with one more observation appended (F-2, R-13, R-18, R-21 append their notes here). */
	public TransferNotes withObservation(String observation) {
		List<String> updated = new ArrayList<>(observations);
		updated.add(observation);
		return new TransferNotes(priority, updated);
	}
}
