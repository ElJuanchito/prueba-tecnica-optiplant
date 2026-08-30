package com.optiplant.inventory.purchases.domain.model;

import com.optiplant.inventory.purchases.domain.exception.CancellationReasonRequiredException;

/**
 * The sole reader and writer of the F-3 {@code CANCEL_REASON:<text>} first-line token persisted
 * in {@code purchase_orders.notes} (D-8) — the technique {@code transfers} uses for priority and
 * {@code sales} for its void reason.
 *
 * <p><strong>{@link #parse(String)} MUST NOT throw on notes carrying no token</strong>: it
 * returns {@code cancellationReason == null} and the whole text as {@link #humanNote()}. Free
 * prose is reachable from {@code POST /orders}, and this is the leniency {@code TransferNotes}
 * and {@code SaleNotes} already guarantee. The token never leaves this model — {@link #humanNote()}
 * is what the API exposes as {@code notes}, {@link #cancellationReason()} as {@code cancellationReason}.
 */
public record PurchaseOrderNotes(String cancellationReason, String humanNote) {

	private static final String TOKEN_PREFIX = "CANCEL_REASON:";

	public PurchaseOrderNotes {
		cancellationReason = blankToNull(cancellationReason);
		humanNote = blankToNull(humanNote);
	}

	public static PurchaseOrderNotes empty() {
		return new PurchaseOrderNotes(null, null);
	}

	public static PurchaseOrderNotes fromHumanNote(String humanNote) {
		return new PurchaseOrderNotes(null, humanNote);
	}

	/**
	 * @param raw the persisted {@code purchase_orders.notes} column, possibly {@code null}
	 * @return no reason with the whole text as the human note when {@code raw} carries no
	 *     {@code CANCEL_REASON:} first line — never throws
	 */
	public static PurchaseOrderNotes parse(String raw) {
		if (raw == null || raw.isBlank()) {
			return empty();
		}
		String[] lines = raw.split("\n", -1);
		if (lines[0].startsWith(TOKEN_PREFIX)) {
			String reasonText = lines[0].substring(TOKEN_PREFIX.length()).strip();
			StringBuilder rest = new StringBuilder();
			for (int i = 1; i < lines.length; i++) {
				if (i > 1) {
					rest.append('\n');
				}
				rest.append(lines[i]);
			}
			String humanPortion = rest.toString().strip();
			return new PurchaseOrderNotes(reasonText.isEmpty() ? null : reasonText,
					humanPortion.isEmpty() ? null : humanPortion);
		}
		return new PurchaseOrderNotes(null, raw);
	}

	/** The persisted shape: {@code CANCEL_REASON:<text>} first line, human note joined after it. */
	public String render() {
		if (cancellationReason == null) {
			return humanNote;
		}
		StringBuilder sb = new StringBuilder(TOKEN_PREFIX).append(cancellationReason);
		if (humanNote != null) {
			sb.append('\n').append(humanNote);
		}
		return sb.toString();
	}

	/**
	 * Returns a copy carrying the cancellation reason (R-13).
	 *
	 * @throws CancellationReasonRequiredException {@code reason} is {@code null} or blank
	 */
	public PurchaseOrderNotes withCancellationReason(String reason) {
		if (reason == null || reason.isBlank()) {
			throw new CancellationReasonRequiredException();
		}
		return new PurchaseOrderNotes(reason.strip(), this.humanNote);
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value;
	}
}
