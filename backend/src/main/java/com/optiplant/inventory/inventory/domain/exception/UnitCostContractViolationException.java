package com.optiplant.inventory.inventory.domain.exception;

/**
 * Thrown by {@code StockMutationPolicy} when P-03's cost-presence rule is violated: a unit cost
 * is missing for a valued inbound movement type, or supplied for an outbound one (where
 * {@code inventory} stamps the branch's current average cost instead, RN-03). The web layer maps
 * this to {@code 400 invalid_request}.
 */
public class UnitCostContractViolationException extends RuntimeException {

	public UnitCostContractViolationException(String message) {
		super(message);
	}
}
