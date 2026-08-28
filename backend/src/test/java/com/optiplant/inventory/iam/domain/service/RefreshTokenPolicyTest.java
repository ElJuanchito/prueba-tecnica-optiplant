package com.optiplant.inventory.iam.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.optiplant.inventory.iam.domain.model.RefreshTokenGrant;
import com.optiplant.inventory.iam.domain.model.RefreshTokenState;
import com.optiplant.inventory.iam.domain.model.RevocationReason;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure evaluation, no Spring/Docker — every case fixes "now" explicitly, the
 * equivalent of a fixed {@link java.time.Clock}. */
class RefreshTokenPolicyTest {

	private static final Instant NOW = Instant.parse("2026-01-15T12:00:00Z");
	private static final Duration IDLE_WINDOW = Duration.ofHours(8);

	private final RefreshTokenPolicy policy = new RefreshTokenPolicy();

	@Test
	void unaSesionVigenteYReciénUsadaEsValida() {
		RefreshTokenGrant grant = grant(NOW.minus(Duration.ofHours(1)), NOW.minus(Duration.ofMinutes(1)),
				NOW.plus(Duration.ofDays(6)), null, null);

		assertThat(policy.evaluate(grant, NOW, IDLE_WINDOW)).isEqualTo(RefreshTokenState.VALID);
	}

	@Test
	void unaSesionYaRevocadaEsReutilizacion() {
		RefreshTokenGrant grant = grant(NOW.minus(Duration.ofHours(1)), NOW.minus(Duration.ofMinutes(1)),
				NOW.plus(Duration.ofDays(6)), NOW.minus(Duration.ofMinutes(30)), RevocationReason.ROTATED);

		assertThat(policy.evaluate(grant, NOW, IDLE_WINDOW)).isEqualTo(RefreshTokenState.REUSE_DETECTED);
	}

	@Test
	void unaSesionConExpiresAtEnElPasadoHaExpirado() {
		RefreshTokenGrant grant = grant(NOW.minus(Duration.ofDays(8)), NOW.minus(Duration.ofMinutes(1)),
				NOW.minus(Duration.ofSeconds(1)), null, null);

		assertThat(policy.evaluate(grant, NOW, IDLE_WINDOW)).isEqualTo(RefreshTokenState.EXPIRED);
	}

	@Test
	void unaSesionInactivaMasAlláDeLaVentanaDeInactividadEstaExpiradaPorInactividad() {
		RefreshTokenGrant grant = grant(NOW.minus(Duration.ofHours(9)), NOW.minus(Duration.ofHours(9)),
				NOW.plus(Duration.ofDays(6)), null, null);

		assertThat(policy.evaluate(grant, NOW, IDLE_WINDOW)).isEqualTo(RefreshTokenState.IDLE_EXPIRED);
	}

	@Test
	void justoEnElLimiteDeLaVentanaDeInactividadTodaviaEsValida() {
		RefreshTokenGrant grant = grant(NOW.minus(Duration.ofHours(8)), NOW.minus(Duration.ofHours(8)),
				NOW.plus(Duration.ofDays(6)), null, null);

		assertThat(policy.evaluate(grant, NOW, IDLE_WINDOW)).isEqualTo(RefreshTokenState.VALID);
	}

	@Test
	void expiracionAbsolutaPrevaleceSobreInactividadReciente() {
		// last_used_at is recent (well inside the idle window) but expires_at is
		// already in the past — the absolute cap must still reject.
		RefreshTokenGrant grant = grant(NOW.minus(Duration.ofDays(7)), NOW.minus(Duration.ofMinutes(1)),
				NOW.minus(Duration.ofSeconds(1)), null, null);

		assertThat(policy.evaluate(grant, NOW, IDLE_WINDOW)).isEqualTo(RefreshTokenState.EXPIRED);
	}

	private static RefreshTokenGrant grant(Instant issuedAt, Instant lastUsedAt, Instant expiresAt, Instant revokedAt,
			RevocationReason revokedReason) {
		return new RefreshTokenGrant(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), issuedAt, lastUsedAt,
				expiresAt, revokedAt, revokedReason);
	}
}
