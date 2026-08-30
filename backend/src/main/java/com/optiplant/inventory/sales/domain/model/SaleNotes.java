package com.optiplant.inventory.sales.domain.model;

import java.util.Optional;

/**
 * The sole reader and writer of the F-3 cancellation reason token persisted in
 * {@code sales.notes} (design §4).
 *
 * <p>{@code render()} writes {@code VOID_REASON:<reason>} as the first line and joins
 * the human note after it; {@link #parse(String)} reads it back.
 *
 * <p><strong>Notes with no {@code VOID_REASON:} first line parse to no reason with the
 * whole text as the human note</strong> — an external POS or an operator can write free
 * prose and this parser MUST NOT throw on it.
 *
 * <p>{@link #humanNote()} is what the API exposes as {@code notes}; {@link #cancellationReason()}
 * is exposed as {@code cancellationReason}; the raw token never leaves this model.
 */
public record SaleNotes(CancellationReason cancellationReason, String humanNote) {

	private static final String TOKEN_PREFIX = "VOID_REASON:";

	public static SaleNotes empty() {
		return new SaleNotes(null, null);
	}

	public static SaleNotes fromHumanNote(String humanNote) {
		return new SaleNotes(null, humanNote);
	}

	public static SaleNotes parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return empty();
		}
		String[] lines = raw.split("\n", -1);
		if (lines[0].startsWith(TOKEN_PREFIX)) {
			String reasonText = lines[0].substring(TOKEN_PREFIX.length()).strip();
			CancellationReason reason = reasonText.isEmpty() ? null : new CancellationReason(reasonText);
			StringBuilder rest = new StringBuilder();
			for (int i = 1; i < lines.length; i++) {
				if (i > 1) {
					rest.append('\n');
				}
				rest.append(lines[i]);
			}
			String restStr = rest.toString().strip();
			return new SaleNotes(reason, restStr.isEmpty() ? null : restStr);
		}
		return new SaleNotes(null, raw);
	}

	public String render() {
		if (cancellationReason != null) {
			StringBuilder sb = new StringBuilder(TOKEN_PREFIX).append(cancellationReason.value());
			if (humanNote != null && !humanNote.isBlank()) {
				sb.append('\n').append(humanNote);
			}
			return sb.toString();
		}
		return humanNote;
	}

	public SaleNotes withCancellationReason(CancellationReason reason) {
		return new SaleNotes(reason, this.humanNote);
	}

	public Optional<CancellationReason> optionalCancellationReason() {
		return Optional.ofNullable(cancellationReason);
	}
}
