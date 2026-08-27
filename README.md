# Sistema de Gestión de Inventario Multi-Sucursal

Prueba técnica para **OptiPlant Consultores**.

Sistema para que varias sucursales de una organización gestionen su inventario con autonomía operativa local, manteniendo visibilidad compartida sobre el inventario de toda la red: cada sucursal opera sus transacciones sin depender de las demás, consulta las existencias de cualquier otra y solicita transferencias de mercancía entre nodos.

> **Principio rector del proyecto:** cada decisión de diseño debe poder responder *«¿por qué se hizo así?»*. Este repositorio está organizado para que esa respuesta esté siempre a un enlace de distancia.

---

## Estado del Proyecto

Este repositorio contiene, a la fecha, **la ingeniería completa del sistema, su capa de datos verificada y el esqueleto ejecutable del backend**. La lógica de negocio y el frontend están pendientes.

| Entregable | Estado |
| :--- | :--- |
| Levantamiento de requerimientos, casos de uso e historias | ✅ Completo |
| Diagramas de ingeniería (casos de uso, actividad, arquitectura, E-R) | ✅ Completo — 18 diagramas |
| Modelo de datos y esquema SQL | ✅ Completo — 19 tablas, verificado contra PostgreSQL 17 |
| Decisiones de arquitectura documentadas | ✅ Completo |
| Documentación del uso de IA | ✅ Completo |
| Registro de deuda técnica | ✅ Completo |
| Backend — esqueleto ejecutable | ✅ Completo — compila, se contenedoriza y responde su sonda de salud |
| Backend — lógica de negocio y API REST | ⏳ Pendiente |
| Frontend (SPA) | ⏳ Pendiente |
| `compose.yml` | ◐ Parcial — `db` y `backend`; el servicio `frontend` entra cuando exista código |

**Por qué se declara así.** Un README que prometa más de lo que arranca se desmiente en diez segundos. Cada ✅ de esta tabla corresponde a algo que se ejecutó, y la sección [Ejecución](#ejecución) dice con qué comando reproducirlo.

---

## Ejecución

### Levantar el sistema

Desde la raíz del repositorio, sin configuración previa:

```bash
docker compose up
```

Levanta dos servicios: `db` (PostgreSQL 17, con el esquema y las semillas de `backend/init-db/`) y `backend` (Java 25 + Spring Boot 4.1). El servicio `frontend` todavía no existe.

> **Sobre el nombre del archivo.** El enunciado pide un `docker-compose.yml`; este repositorio entrega un **`compose.yml`**. Desde Compose V2 ese es el nombre canónico que la herramienta busca primero, y `docker-compose.yml` se conserva únicamente por compatibilidad con la V1. El entregable real es que un solo comando levante la solución, y `docker compose up` funciona igual con cualquiera de los dos nombres. La divergencia se declara acá en lugar de dejarla implícita.

Cuando el backend queda `healthy`:

```bash
curl http://localhost:8080/actuator/health/readiness
# {"status":"UP"}
```

La configuración se inyecta por variables de entorno con valores por defecto operativos, de modo que el comando funciona sin crear ningún archivo. Para ajustarlos, copiar [`.env.example`](./.env.example) a `.env`.

> **El backend arranca aunque la base todavía no acepte conexiones.** `spring.datasource.hikari.initialization-fail-timeout: -1` es deliberado: la aplicación levanta y su sonda de *readiness* reporta `DOWN` hasta que PostgreSQL responde. Por eso el `healthcheck` del servicio consulta esa sonda y no el puerto.

Para bajarlo, incluyendo el volumen de datos:

```bash
docker compose down -v
```

### Construir y probar el backend

Desde `backend/`, con el wrapper versionado —no hace falta tener Maven instalado:

```bash
./mvnw package   # compila y empaqueta el JAR; no requiere Docker
./mvnw verify    # además ejecuta la prueba de integración contra PostgreSQL 17 real
```

La separación es intencional. `ModuleBoundariesTest` verifica las fronteras entre módulos y capas con reglas de ArchUnit —análisis estático puro— y corre en `package`; `ApplicationContextIT` levanta el contexto contra un PostgreSQL 17 de Testcontainers y corre sólo en `verify`. Así `package` sigue funcionando en un clon limpio sin demonio Docker.

### Lo que se puede verificar hoy

Ambos comandos se ejecutan desde la raíz del repositorio.

```bash
# 1. Integridad de la trazabilidad documental — no requiere nada instalado
python3 scripts/validar_trazabilidad.py

# 2. Esquema e invariantes de negocio contra PostgreSQL 17 real — requiere Docker
./scripts/validar_esquema.sh
```

El segundo levanta un contenedor efímero de PostgreSQL 17, carga el esquema y los datos semilla, ejercita 19 invariantes de negocio —stock negativo, roles válidos, jerarquía de precios, tope de descuento, estados de transferencia— y destruye el contenedor al terminar.

### Levantar solo la base de datos

```bash
docker run --rm -d --name optiplant_db \
  -e POSTGRES_PASSWORD=optiplant -e POSTGRES_DB=optiplant -p 5432:5432 \
  -v "$PWD/backend/init-db:/docker-entrypoint-initdb.d:ro" \
  postgres:17-alpine
```

Los scripts de `backend/init-db/` se ejecutan automáticamente al inicializar el volumen. Verificado: crean **19 tablas** y cargan 3 sucursales, 7 usuarios, 5 productos, 3 listas de precios con 16 precios vigentes e históricos, 6 rutas logísticas, 1 transferencia en curso y 2 alertas activas.

> **Esperá a que termine la inicialización antes de consultar.** PostgreSQL acepta conexiones por el socket local *mientras* todavía está ejecutando los scripts de `init-db`, así que una consulta prematura devuelve un esquema a medio crear. La señal fiable es el mensaje `PostgreSQL init process complete` en los registros:
>
> ```bash
> docker logs -f optiplant_db | grep -m1 "init process complete"
> ```

| Usuario | Rol | Sucursal |
| :--- | :--- | :--- |
| `admin.corp` | `ADMIN` | — (corporativo) |
| `gerente.bogota`, `gerente.medellin`, `gerente.cali` | `BRANCH_MANAGER` | Bogotá, Medellín, Cali |
| `operador.bogota`, `operador.medellin`, `operador.cali` | `OPERATOR` | Bogotá, Medellín, Cali |

### Lo que todavía no hace `docker compose up`

No levanta el `frontend`, porque `frontend/` no tiene código: un servicio que apunta a un directorio vacío produce un contenedor que no arranca. Entra en el Compose cuando exista algo que servir.

Tampoco ejecuta migraciones. Flyway está **declarado y explícitamente desactivado**: mientras el volumen se inicialice con `backend/init-db/`, Flyway encontraría tablas que no creó y fallaría. El procedimiento de sustitución está en `DT-01` de [`docs/deuda_tecnica.md`](./docs/deuda_tecnica.md).

---

## Estructura del Repositorio

```
.
├── README.md                  Este documento
├── compose.yml                Servicios db y backend
├── .env.example               Plantilla de configuración; ningún secreto real
├── backend/
│   ├── pom.xml                Java 25, Spring Boot 4.1
│   ├── mvnw                   Wrapper de Maven versionado
│   ├── Dockerfile             Multi-etapa: JDK 25 construye, JRE 25 ejecuta
│   ├── src/main/java/com/optiplant/inventory/
│   │   ├── InventoryApplication.java   Clase de arranque
│   │   ├── SecurityConfig.java         Cadena de filtros sin estado
│   │   └── JwtProperties.java          Clave de firma, validada al arrancar
│   ├── src/main/resources/    application.yml + perfiles dev y prod
│   ├── src/test/java/...      Fronteras de módulo e integración con Testcontainers
│   └── init-db/               Esquema y datos semilla de PostgreSQL
│       ├── 01-init-schema.sql   19 tablas, restricciones e índices
│       └── 02-seed-data.sql     Datos de demostración
├── frontend/                  (pendiente)
├── docs/                      Documentación de ingeniería
│   ├── especificacion_requerimientos.md
│   ├── casos_de_uso.md
│   ├── historias_de_usuario.md
│   ├── modelado_sistema.md
│   ├── diagrama_er.md
│   ├── decisiones_arquitectura_tecnica.md
│   ├── deuda_tecnica.md
│   ├── uso_de_ia.md
│   └── diagrams/              16 archivos Excalidraw + 2 PlantUML
└── scripts/                   Validación ejecutable
    ├── validar_trazabilidad.py
    └── validar_esquema.sh
```

---

## Arquitectura

**Monolito modular** a nivel de sistema, **hexagonal (puertos y adaptadores)** a nivel de módulo, sobre tres capas aisladas en contenedores independientes.

```
Presentación   React 19 + Vite + TypeScript · SPA responsiva, sin reglas de negocio
                          │  HTTPS · REST/JSON · sólo identificadores públicos
Negocio        Java 25 + Spring Boot 4.1 · 10 módulos con fronteras verificadas
                          │  JDBC · HikariCP · transacciones ACID
Datos          PostgreSQL 17 · 19 tablas · Kardex append-only · bloqueo pesimista
```

**Por qué monolito modular y no microservicios.** El inventario exige transacciones ACID que abarcan varios módulos: descontar stock, escribir el Kardex y cerrar la venta ocurren juntos o no ocurren. Con microservicios esa atomicidad exigiría sagas y compensaciones, complejidad enorme para un dominio que cabe en una sola base de datos. El monolito modular entrega la frontera del microservicio sin pagar su costo operativo, y las reglas de ArchUnit impiden que esa frontera se erosione en silencio.

Detalle completo en [`decisiones_arquitectura_tecnica.md`](docs/decisiones_arquitectura_tecnica.md) y en los tres diagramas de arquitectura.

---

## Módulos del Sistema

| Módulo | Responsabilidad | Casos de uso |
| :--- | :--- | :--- |
| `iam` | Usuarios, roles, sesiones y bitácora de auditoría | CU-SEG-01 … 04 |
| `catalog` | Productos, categorías y unidades de medida | CU-INV-01, 02 |
| `pricing` | Listas de precios, vigencias y topes de descuento | CU-VEN-02 |
| `inventory` | Existencias por sucursal, Kardex, ajustes y umbrales | CU-INV-03 … 08 |
| `purchases` | Órdenes de compra, recepción y costo promedio ponderado | CU-COM-01 … 05 |
| `sales` | Ventas, comprobantes y anulaciones | CU-VEN-01, 03, 04 |
| `transfers` | Máquina de estados de traslados entre sucursales | CU-TRA-01 … 06 |
| `logistics` | Rutas, tiempos estimados y cumplimiento | CU-LOG-01 … 03 |
| `notifications` | Motor de alertas y eventos operativos | CU-ALE-01, 02 |
| `analytics` | Dashboards y KPI — solo lectura | CU-DSH-01 … 03 |

---

## Decisiones de Diseño

Las siete que más condicionan el sistema. Cada una está justificada en extenso en el [documento de arquitectura](docs/decisiones_arquitectura_tecnica.md).

| Decisión | Razón |
| :--- | :--- |
| **PostgreSQL relacional, no NoSQL** | El inventario es un activo financiero: no se tolera consistencia eventual en ventas concurrentes ni faltantes fantasma. |
| **Bloqueo pesimista al descontar stock** | En operaciones cortas de alta contención sobre las mismas filas, el bloqueo optimista degenera en reintentos y rechazos al usuario. |
| **Clave primaria `BIGINT` + `external_id` UUID público** | Índices B-Tree la mitad de grandes, y cero exposición de identificadores internos en la API (anti-enumeración). |
| **Kardex *append-only*** | El saldo de stock es una proyección verificable contra el histórico; ningún movimiento se modifica ni se borra. |
| **Puertos síncronos para lo atómico, eventos post-commit para lo demás** | Si el descuento de stock viajara por un evento asíncrono quedaría fuera de la transacción de la venta. El desacoplamiento lo da la interfaz, no el evento. |
| **La aprobación de una transferencia no reserva stock** | Reservar inmovilizaría mercancía por tiempo indefinido a costa de las ventas locales; por eso el despacho revalida disponibilidad. |
| **Listas de precios versionadas por vigencia** | Una columna de precio en el producto impide precios por sucursal, por segmento y el histórico necesario para auditar descuentos pasados. |

---

## Documentación

| Documento | Contenido | Sección del enunciado |
| :--- | :--- | :--- |
| [Especificación de requerimientos](docs/especificacion_requerimientos.md) | 42 RF, 35 RNF, 17 reglas de negocio, restricciones, supuestos, glosario y alcance excluido | 6.1 |
| [Casos de uso](docs/casos_de_uso.md) | 5 actores, matriz RBAC, 37 casos de uso, 7 especificaciones extendidas, matriz de trazabilidad | 6.2 |
| [Historias de usuario](docs/historias_de_usuario.md) | 31 historias en 8 épicas con criterios de aceptación en Gherkin, MoSCoW y definición de terminado | 6.3 |
| [Modelado del sistema](docs/modelado_sistema.md) | Índice de los diagramas obligatorios, flujos de actividad y vistas de arquitectura | 7.1 |
| [Diagrama entidad-relación](docs/diagrama_er.md) | Modelo de datos completo en Mermaid, PlantUML y Excalidraw | 7.1 |
| [Decisiones de arquitectura](docs/decisiones_arquitectura_tecnica.md) | Separación de responsabilidades y las decisiones técnicas justificadas | 8.1 y 8.2 |
| [Uso de inteligencia artificial](docs/uso_de_ia.md) | Herramientas, prompts reales, evaluación crítica y estimación de asistencia | 9 |
| [Deuda técnica](docs/deuda_tecnica.md) | 6 ítems con plan de pago y condición que lo dispara | — |

### Diagramas

Los archivos `.excalidraw` se abren arrastrándolos sobre [excalidraw.com](https://excalidraw.com) o con la extensión de Excalidraw para VS Code. Los bloques Mermaid embebidos en los documentos se renderizan directamente en GitHub.

| Grupo | Cantidad | Ubicación |
| :--- | :---: | :--- |
| Casos de uso — mapa general y uno por módulo | 9 | `docs/diagrams/casos_de_uso*.excalidraw` |
| Actividad — venta, transferencia y recepción de compra | 3 | `docs/diagrams/actividad_*.excalidraw` |
| Arquitectura — capas, módulos y puertos/adaptadores | 3 | `docs/diagrams/arquitectura_*.excalidraw` |
| Entidad-relación | 1 | `docs/diagrams/diagrama_er.excalidraw` |
| Notación UML estricta | 2 | `docs/diagrams/*.puml` |

---

## Verificación

Toda afirmación de verificación de este proyecto es reproducible. Los dos scripts de `scripts/` no comprueban estilo: ejercitan los invariantes que la documentación promete.

| Script | Comprueba | Salida esperada |
| :--- | :--- | :--- |
| `validar_trazabilidad.py` | Que todo identificador citado exista, que todo requerimiento tenga caso de uso, que todo caso de uso tenga requerimiento, que toda deuda tenga ficha y que ningún enlace esté roto | `42 RF · 35 RNF · 17 RN · 37 CU · 6 DT` |
| `validar_esquema.sh` | 19 invariantes contra PostgreSQL 17 real | `19 comprobaciones correctas` |

Los 9 bloques Mermaid y los 2 archivos PlantUML se verificaron renderizándolos con `mermaid-cli` y `plantuml.jar`.

---

## Deuda Técnica

Las decisiones de postergar trabajo están registradas en [`deuda_tecnica.md`](docs/deuda_tecnica.md), cada una con su justificación, su plan de pago y la condición que lo dispara. La más relevante:

**DT-01 — versionado del esquema con Flyway.** Hoy el esquema se carga con el mecanismo de inicialización de la imagen de PostgreSQL, que sólo actúa sobre un volumen vacío: sirve para reconstruir desde cero y es inservible en cuanto exista un dato que preservar. Se paga al montar el backend.
