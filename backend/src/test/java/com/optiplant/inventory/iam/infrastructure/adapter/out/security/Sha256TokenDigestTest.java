package com.optiplant.inventory.iam.infrastructure.adapter.out.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Sha256TokenDigestTest {

	private final Sha256TokenDigest digest = new Sha256TokenDigest();

	@Test
	void esDeterministaParaElMismoValor() {
		String raw = "una-cadena-cualquiera-de-alta-entropía";

		assertThat(digest.hex(raw)).isEqualTo(digest.hex(raw));
	}

	@Test
	void esUnHexadecimalDeSesentaYCuatroCaracteres() {
		String hex = digest.hex("otro-valor");

		assertThat(hex).hasSize(64).matches("^[0-9a-f]{64}$");
	}

	@Test
	void valoresDistintosProducenDigestosDistintos() {
		assertThat(digest.hex("token-a")).isNotEqualTo(digest.hex("token-b"));
	}

	@Test
	void coincideConElVectorDePruebaConocidoDeSha256() {
		// SHA-256("") — vector de prueba estándar, confirma el algoritmo correcto.
		assertThat(digest.hex("")).isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
	}
}
