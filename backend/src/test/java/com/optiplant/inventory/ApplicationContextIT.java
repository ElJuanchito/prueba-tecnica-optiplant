package com.optiplant.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/**
 * Prueba de integración: contexto real contra PostgreSQL 17 de Testcontainers.
 *
 * <p>Corre en la fase {@code verify} mediante failsafe, no en {@code test}. Con Data
 * JPA en el classpath un {@code @SpringBootTest} sin base no levanta contexto; si
 * esta prueba corriera en {@code test}, {@code ./mvnw package} exigiría Docker y la
 * construcción de la imagen dejaría de funcionar.
 *
 * <p>Usa {@link RestClient} de spring-web y no {@code TestRestTemplate}: este último
 * necesita el módulo {@code spring-boot-restclient}, que Spring Boot 4 separó y que
 * este proyecto no declara.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApplicationContextIT {

	@LocalServerPort
	private int puerto;

	@Test
	void laSondaDeReadinessRespondeOk() {
		ResponseEntity<String> respuesta = RestClient.create()
				.get()
				.uri("http://localhost:{puerto}/actuator/health/readiness", puerto)
				// Sin este manejador, RestClient lanza ante un estado que no sea 2xx
				// y la prueba fallaría con una excepción en lugar de con una aserción.
				.retrieve()
				.onStatus(estado -> true, (peticion, resultado) -> {
				})
				.toEntity(String.class);

		assertThat(respuesta.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(respuesta.getBody()).contains("UP");
	}
}
