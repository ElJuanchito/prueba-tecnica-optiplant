package com.optiplant.inventory.catalog.domain.exception;

/**
 * Thrown by {@code BaseUnitChangePolicy} when R-08's precondition is not met: the
 * product has balances or movement history, or the stock-presence port has no
 * implementation available and the precondition cannot be verified (design §3.4).
 *
 * <p>It carries a {@link Reason} so the future slice that finally exposes the
 * HTTP endpoint (DT-07, when {@code inventory} ships) can emit <strong>two</strong>
 * distinct error codes — one for "the product has history", one for "the port
 * cannot answer" — without reopening the domain (contract §7). Collapsing them
 * later would make an infrastructure gap look like a business rejection in the
 * logs.
 *
 * <p><strong>This exception deliberately has no {@code CatalogExceptionHandler}
 * mapping.</strong> PA-08 defers the endpoint, so no reachable path raises it
 * outside {@code BaseUnitChangePolicyTest} and {@code ProductAdminServiceTest}. A
 * code with no reachable path is dead contract (design §3.4, contract §7).
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
