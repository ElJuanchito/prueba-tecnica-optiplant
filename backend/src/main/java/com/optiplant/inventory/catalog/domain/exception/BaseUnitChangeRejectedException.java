package com.optiplant.inventory.catalog.domain.exception;

/**
 * Thrown by {@code BaseUnitChangePolicy} when R-08's precondition is not met: the
 * product has balances or movement history, or the stock-presence port has no
 * implementation available and the precondition cannot be verified (design §3.4).
 *
 * <p>It carries a {@link Reason} so {@code CatalogExceptionHandler} can emit
 * <strong>two</strong> distinct error codes — {@code base_unit_has_history} and
 * {@code base_unit_precondition_unverifiable} — without reopening the domain
 * (contract §7, DT-07). Collapsing them would make an infrastructure gap look like
 * a business rejection in the logs.
 *
 * <p>Reachable through {@code PATCH /api/catalog/products/{externalId}/base-unit}
 * (DT-07, paid once {@code inventory} implemented
 * {@code shared/stock/ProductStockPresencePort}).
 */
public class BaseUnitChangeRejectedException extends RuntimeException {

	/**
	 * Why the base-unit change was refused. Both values exist now so the deferred
	 * slice can map each to its own error code without a domain change.
	 */
	public enum Reason {
		/** The product has stock or Kardex history recorded in the old base unit (RN-13). */
		HAS_HISTORY,
		/** No stock-presence port implementation is available, so the precondition cannot be proven (fail closed). */
		PRECONDITION_UNVERIFIABLE
	}

	private final transient Reason reason;

	public BaseUnitChangeRejectedException(Reason reason) {
		super(messageFor(reason));
		this.reason = reason;
	}

	public Reason reason() {
		return reason;
	}

	private static String messageFor(Reason reason) {
		return switch (reason) {
			case HAS_HISTORY -> "base unit cannot change: the product has balances or movement history";
			case PRECONDITION_UNVERIFIABLE ->
				"base unit cannot change: the stock-presence precondition could not be verified";
		};
	}
}
