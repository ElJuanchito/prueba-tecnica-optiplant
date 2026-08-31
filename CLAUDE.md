# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Proyecto

Sistema de inventario multi-sucursal (prueba técnica para OptiPlant). **Estado actual: backend completo y frontend implementado.** Los diez módulos de dominio del backend están construidos, verificados y archivados vía SDD (39/39 casos de uso; detalle en `openspec/PLAN.md`). El `frontend/` es una SPA de React que cubre esos dominios con enrutamiento, i18n y aislamiento por rol; se contenedoriza y se sirve con Nginx (`frontend/Dockerfile`, servicio `frontend` en `compose.yml`).

Toda la documentación está en español. Mantener ese idioma al extenderla.

## Verificación

```bash
python3 scripts/validar_trazabilidad.py   # referencias y enlaces entre documentos; sin dependencias
./scripts/validar_esquema.sh              # 34 invariantes contra PostgreSQL 17 real; requiere Docker
cd backend && ./mvnw verify               # fronteras de arquitectura + integración con Testcontainers
```

Los dos primeros deben pasar antes de dar por terminado cualquier cambio en `docs/` o en `backend/init-db/`; el tercero, ante cualquier cambio en `backend/`.

**Regla de trabajo del proyecto: no se afirma nada sin ejecutarlo.** Cada defecto grave de este repositorio apareció al ejecutar, nunca al leer. Un SQL que "se ve bien" no está verificado; un diagrama Mermaid sin renderizar tampoco; una clase de Spring importada de memoria, menos que ninguna.

Al levantar PostgreSQL manualmente, esperar `PostgreSQL init process complete` en los registros antes de consultar: el servidor acepta conexiones **mientras** aún corre los scripts de `init-db`, y una consulta prematura devuelve un esquema a medio crear.

**Tras cualquier cambio en `backend/init-db/`, recrear el volumen con `docker compose down -v`.** PostgreSQL ejecuta los scripts de inicialización sólo cuando el directorio de datos está vacío: sobre un volumen ya inicializado, el esquema nuevo se ignora en silencio y la aplicación arranca contra el viejo. El síntoma es una tabla o columna que existe en el SQL y no en la base.

## Invariantes que ya rompieron el proyecto

| Regla | Por qué |
| :--- | :--- |
| Los roles son `ADMIN`, `BRANCH_MANAGER`, `OPERATOR` — **sin prefijo `ROLE_`** | El `CHECK` de `users` los rechaza. Este error se propagó del documento de arquitectura a las semillas y rompió el arranque. Con Spring Security usar `hasAuthority()`, no `hasRole()`, que antepone el prefijo. |
| Los `external_id` de las semillas son UUID: **solo dígitos hexadecimales** | 29 literales con prefijos `p`, `r`, `s`, `t`, `u` hacían fallar la carga entera. La convención de prefijos está documentada en la cabecera de `backend/init-db/02-seed-data.sql`. |
| Toda mutación de stock escribe su movimiento en el Kardex **en la misma transacción** | El saldo es una proyección del histórico. Sin eso, ambos quedan desalineados sin forma de reconciliarlos. |
| Los efectos atómicos van por **puerto de salida síncrono**, nunca por evento | Un evento asíncrono queda fuera de la transacción. Los eventos de dominio se usan solo en `AFTER_COMMIT` para lo que puede fallar sin revertir la operación: alertas y analítica. |
| La sucursal se deriva **de la sesión autenticada**, nunca de un parámetro del cliente | Es la frontera de aislamiento entre sucursales. |
| La API expone **solo `external_id`**, jamás los `id` numéricos internos | Evita enumeración directa de recursos. |

## Trazabilidad

Los identificadores encadenan los documentos: `RF` / `RNF` / `RN` → `CU` → `HU`.

Al agregar un requerimiento hay que agregar también su caso de uso y su fila en la matriz de trazabilidad de `docs/casos_de_uso.md`, o `scripts/validar_trazabilidad.py` falla. Lo mismo al renombrar o eliminar.

**Al eliminar un identificador, no lo cites por su ID en el historial de versiones.** El validador exige que todo `RF` / `RNF` / `RN` citado en `docs/` esté definido en el SRS, así que una entrada de changelog que dice «se elimina RNF-XX-01» lo resucita y hace fallar la validación. Nombrarlo por su título. Los identificadores retirados no se reasignan.

| Necesitás… | Fuente de verdad |
| :--- | :--- |
| Requerimientos, reglas de negocio `RN-xx`, alcance excluido | `docs/especificacion_requerimientos.md` |
| Justificación de una decisión técnica | `docs/decisiones_arquitectura_tecnica.md` |
| Trabajo postergado y su plan de pago | `docs/decisiones_arquitectura_tecnica.md` (sección 7) |
| Modelo de datos | `docs/modelado_sistema.md` (sección 4) + `backend/init-db/01-init-schema.sql` |

## Backend

Java 25 · Spring Boot 4.1 · Maven con wrapper (`./mvnw`) · raíz en `backend/`.

**Monolito modular con hexagonal dentro de cada módulo, sin Spring Modulith.** Se retiró de forma deliberada: las fronteras se declaran a mano con reglas de ArchUnit en `ModuleBoundariesTest`, no se derivan de la detección automática de un framework. No reintroducirlo.

Los diez módulos y sus responsabilidades están en la sección 2.4 del documento de arquitectura; la estructura de paquetes canónica, en la sección 5. Respetarlas al crear paquetes.

| Regla del backend | Por qué |
| :--- | :--- |
| **Ninguna clase nueva en un subpaquete directo del paquete base** salvo que sea un módulo de negocio | La regla de fronteras trata cada subpaquete de `com.optiplant.inventory` como un módulo. Un `config/` o un `util/` agregarían al grafo verificado una frontera que nadie declaró. Por eso `InventoryApplication` y `SecurityConfig` viven en el paquete base. `JwtProperties` vivía ahí como recurso provisional y ya migró a `iam/infrastructure/config`, que es su lugar definitivo: una clase de configuración pertenece al módulo que la usa, no a la raíz. |
| **Las pruebas que necesitan Docker terminan en `IT`**, no en `Test` | `*Test` corre en `package` (surefire) y `*IT` en `verify` (failsafe). Con Data JPA en el classpath, un `@SpringBootTest` sin base no levanta contexto: si esa prueba corriera en `package`, la construcción de la imagen exigiría un demonio Docker. |
| Las reglas de ArchUnit llevan **`allowEmptyShould(true)`** | Mientras los paquetes de módulo no existan, las reglas no encuentran clases que evaluar y ArchUnit falla ante un conjunto vacío por defecto. El vacío es el estado legítimo del proyecto, no un error de la regla. Cada módulo que aparezca entra automáticamente bajo esas comprobaciones. |
| `shared/` será **módulo abierto** y debe ser **hoja** | Ningún módulo puede aparecer en sus importaciones, o el desacoplamiento por puertos se rompe por la puerta de atrás. Hay una regla de ArchUnit que lo verifica. |
| **No agregar Flyway junto a `backend/init-db/`** | La dependencia está declarada y `spring.flyway.enabled` en `false`. El volumen ya inicializado hace que Flyway encuentre tablas que no creó y falle; `baseline-on-migrate` no lo resuelve, solo le pide ignorar un estado que no comprende. La migración es sustitución, no coexistencia — el procedimiento está en `DT-01` de `docs/decisiones_arquitectura_tecnica.md` (sección 7). |

### Spring Boot 4 no es Spring Boot 3

Verificado ejecutando, no leyendo. Un `pom.xml` o un `import` copiado de un tutorial de Boot 3 no compila:

| En Boot 3 | En Boot 4 |
| :--- | :--- |
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `flyway-core` con el starter genérico | `spring-boot-starter-flyway` |
| `org.springframework.boot.test.web.client.TestRestTemplate`, autoconfigurado con `webEnvironment = RANDOM_PORT` | `org.springframework.boot.resttestclient.TestRestTemplate`, requiere `@AutoConfigureTestRestTemplate` **y** el módulo `spring-boot-restclient`, que no viene con el starter de webmvc |

Ante la duda sobre el nombre o el paquete de una clase de Spring, confirmarlo contra el JAR resuelto en `~/.m2` antes de escribirlo. Las pruebas de integración usan `RestClient` de `spring-web` justamente para no arrastrar un módulo extra.

### Levantar el sistema

```bash
docker compose up          # db + backend + frontend (Nginx sirve la SPA en :8081)
docker compose down -v
```

`compose.yml` vive en la raíz. La variable `JWT_SECRET` se propaga **sin `=`** a propósito: con un valor por defecto vacío pisaría el de `application-dev.yml` y la validación de `JwtProperties` fallaría.
