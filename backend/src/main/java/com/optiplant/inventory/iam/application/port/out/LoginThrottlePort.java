package com.optiplant.inventory.iam.application.port.out;

import com.optiplant.inventory.iam.domain.exception.TooManyLoginAttemptsException;

/**
 * Per-instance login rate limiting (RNF-SEC-06). The default implementation is
 * in-memory and openly limited to a single instance's ceiling — see design decision
 * "in-memory login throttle, per instance, stated as a limitation".
 */
public interface LoginThrottlePort {

	/** @throws TooManyLoginAttemptsException when {@code key} is currently blocked */
	void checkAllowed(String key);

	void recordFailure(String key);

	void recordSuccess(String key);
}
