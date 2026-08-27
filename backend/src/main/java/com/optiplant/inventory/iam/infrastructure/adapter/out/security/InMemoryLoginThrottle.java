package com.optiplant.inventory.iam.infrastructure.adapter.out.security;

import com.optiplant.inventory.iam.application.port.out.LoginThrottlePort;
import com.optiplant.inventory.iam.domain.exception.TooManyLoginAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * Fixed-window, per-instance login throttle (RNF-SEC-06; design decision "in-memory
 * login throttle, per instance, stated as a limitation"). Keyed by the caller on
 * {@code lower(username) + "|" + clientIp} so a shared IP cannot lock out other users.
 *
 * <p><b>Openly limited</b>, as the design records: with N instances the effective
 * ceiling is N × {@link #DEFAULT_MAX_ATTEMPTS}, and stale keys are evicted lazily (on
 * their own next {@link #checkAllowed(String)}) rather than by a background sweep — a
 * distributed implementation replaces this adapter without touching
 * {@code AuthenticationService}.
 */
@Component
public class InMemoryLoginThrottle implements LoginThrottlePort {

	private static final int DEFAULT_MAX_ATTEMPTS = 5;
	private static final Duration DEFAULT_WINDOW = Duration.ofMinutes(5);

	private final Clock clock;
	private final int maxAttempts;
	private final Duration window;
	private final ConcurrentHashMap<String, Window> attemptsByKey = new ConcurrentHashMap<>();

	public InMemoryLoginThrottle() {
		this(Clock.systemUTC(), DEFAULT_MAX_ATTEMPTS, DEFAULT_WINDOW);
	}

	/** Package-visible so tests can inject a fixed {@link Clock} and tighter limits. */
	InMemoryLoginThrottle(Clock clock, int maxAttempts, Duration window) {
		this.clock = clock;
		this.maxAttempts = maxAttempts;
		this.window = window;
	}

	@Override
	public void checkAllowed(String key) {
		Window current = attemptsByKey.get(key);
		if (current == null) {
			return;
		}
		if (windowElapsed(current)) {
			attemptsByKey.remove(key, current);
			return;
		}
		if (current.failureCount.get() >= maxAttempts) {
			throw new TooManyLoginAttemptsException();
		}
	}

	@Override
	public void recordFailure(String key) {
		Instant now = clock.instant();
		attemptsByKey.compute(key, (ignoredKey, existing) -> {
			if (existing == null || windowElapsed(existing)) {
				return new Window(now, new AtomicInteger(1));
			}
			existing.failureCount.incrementAndGet();
			return existing;
		});
	}

	@Override
	public void recordSuccess(String key) {
		attemptsByKey.remove(key);
	}

	private boolean windowElapsed(Window entry) {
		return clock.instant().isAfter(entry.windowStart.plus(window));
	}

	private record Window(Instant windowStart, AtomicInteger failureCount) {
	}
}
