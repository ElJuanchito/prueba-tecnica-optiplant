package com.optiplant.inventory;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("postgres:17"))
				// El sistema opera en America/Bogota (compose.yml, fechas de sucursal). Sin
				// fijar la zona del contenedor, `now()`/`CURRENT_DATE` corren en UTC: cerca
				// del cambio de dia UTC, una resolucion de precio con rango de validez
				// (RN-16) ve el rango como expirado y devuelve `price_not_available`, lo que
				// vuelve intermitentes las IT de ventas y pricing segun la hora de ejecucion.
				.withEnv("TZ", "America/Bogota")
				.withEnv("PGTZ", "America/Bogota")
				// Mismo mecanismo que compose.yml (docker-entrypoint-initdb.d), pero
				// copiando en vez de montar: el schema+seed reales corren acá también, no
				// solo en Compose. Necesario desde que existe el primer @Entity (slice 2a):
				// ddl-auto=validate exige que las tablas mapeadas ya existan al arrancar.
				.withCopyFileToContainer(MountableFile.forHostPath("init-db"), "/docker-entrypoint-initdb.d");
	}

}
