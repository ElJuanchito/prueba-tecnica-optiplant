package com.optiplant.inventory.iam.domain.exception;

/**
 * Thrown by {@link com.optiplant.inventory.iam.application.port.out.LoginThrottlePort}
 * when a caller exceeds the allowed failed-login rate (RNF-SEC-06).
 */
public class TooManyLoginAttemptsException extends RuntimeException {

	public TooManyLoginAttemptsException() {
		super("Too many login attempts");
	}
}
