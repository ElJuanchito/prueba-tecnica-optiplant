# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Etapa de construcción — JDK completo, se descarta al terminar.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS builder

WORKDIR /build

# El orden importa: las dependencias se resuelven en su propia capa, de modo que
# un cambio en src/ no obligue a bajar el árbol entero otra vez.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
# Sin pruebas acá: las de integración usan Testcontainers y necesitan un demonio
# Docker desde dentro de la construcción. Corren en ./mvnw verify, fuera de la imagen.
RUN ./mvnw -B package -DskipTests

# ---------------------------------------------------------------------------
# Etapa de ejecución — sólo JRE y el JAR. Sin compilador, sin fuentes.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS runtime

# Cliente HTTP para la sonda de salud que consulta el healthcheck de Compose.
RUN apt-get update \
	&& apt-get install --no-install-recommends -y curl \
	&& rm -rf /var/lib/apt/lists/*

# Usuario sin privilegios con UID/GID fijos: java -jar no necesita root.
RUN groupadd --gid 10001 optiplant \
	&& useradd --uid 10001 --gid 10001 --no-create-home --shell /usr/sbin/nologin optiplant

WORKDIR /app
COPY --from=builder --chown=10001:10001 /build/target/inventory-*.jar /app/app.jar

USER 10001:10001
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
