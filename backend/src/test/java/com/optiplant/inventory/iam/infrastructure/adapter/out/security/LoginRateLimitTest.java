package com.optiplant.inventory.iam.infrastructure.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.optiplant.inventory.iam.domain.exception.TooManyLoginAttemptsException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

/** RNF-SEC-06: fixed-window throttle, keyed by username+IP, with a fixed {@link Clock}
 * so the window boundary is deterministic instead of racing a real 5-minute sleep. */
class LoginRateLimitTest {

	private static final int MAX_ATTEMPTS = 5;
	private static final Duration WINDOW = Duration.ofMinutes(5);

	private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
	private final InMemoryLoginThrottle throttle = new InMemoryLoginThrottle(clock, MAX_ATTEMPTS, WINDOW);

	@Test
	void permiteIntentosPorDebajoDelLimite() {
		String key = "operador|127.0.0.1";
		for (int i = 0; i < MAX_ATTEMPTS - 1; i++) {
			throttle.checkAllowed(key);
			throttle.recordFailure(key);
		}

		assertThatCode(() -> throttle.checkAllowed(key)).doesNotThrowAnyException();
	}

	@Test
	void bloqueaAlAlcanzarElLimiteDentroDeLaVentana() {
		String key = "operador|127.0.0.1";
		for (int i = 0; i < MAX_ATTEMPTS; i++) {
			throttle.checkAllowed(key);
			throttle.recordFailure(key);
		}

		assertThatThrownBy(() -> throttle.checkAllowed(key)).isInstanceOf(TooManyLoginAttemptsException.class);
	}

	@Test
	void unLoginExitosoLimpiaElContador() {
		String key = "operador|127.0.0.1";
		throttle.recordFailure(key);
		throttle.recordFailure(key);
		throttle.recordSuccess(key);

		assertThatCode(() -> throttle.checkAllowed(key)).doesNotThrowAnyException();
	}

	@Test
	void laVentanaExpiradaReiniciaElContador() {
		String key = "operador|127.0.0.1";
		for (int i = 0; i < MAX_ATTEMPTS; i++) {
			throttle.checkAllowed(key);
			throttle.recordFailure(key);
		}
		clock.advance(WINDOW.plusSeconds(1));

		assertThatCode(() -> throttle.checkAllowed(key)).doesNotThrowAnyException();
	}

	@Test
	void clavesDistintasNoSeAfectanEntreSi() {
		String bloqueada = "operador|10.0.0.1";
		String otra = "operador|10.0.0.2";
		for (int i = 0; i < MAX_ATTEMPTS; i++) {
			throttle.checkAllowed(bloqueada);
			throttle.recordFailure(bloqueada);
		}

		assertThatCode(() -> throttle.checkAllowed(otra)).doesNotThrowAnyException();
	}

	private static final class MutableClock extends Clock {

		private Instant instant;

		MutableClock(Instant instant) {
			this.instant = instant;
		}

		void advance(Duration duration) {
			this.instant = this.instant.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("UTC");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
