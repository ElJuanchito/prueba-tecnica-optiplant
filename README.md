# Sistema de Gestión de Inventario Multi-Sucursal

Prueba técnica para **OptiPlant Consultores**.

Sistema para que varias sucursales de una organización gestionen su inventario con autonomía operativa local, manteniendo visibilidad compartida sobre el inventario de toda la red: cada sucursal opera sus transacciones sin depender de las demás, consulta las existencias de cualquier otra y solicita transferencias de mercancía entre nodos.

> **Principio rector del proyecto:** cada decisión de diseño debe poder responder *«¿por qué se hizo así?»*. Este repositorio está organizado para que esa respuesta esté siempre a un enlace de distancia.

---

## Estado del Proyecto

**El sistema está completo.** Los diez módulos de negocio, sus 39 casos de uso, la API REST y la interfaz web están construidos y verificados.

| Entregable | Estado |
| :--- | :--- |
| Levantamiento de requerimientos, casos de uso e historias | ✅ Completo — 43 RF · 34 RNF · 17 RN · 39 CU |
| Diagramas de ingeniería (casos de uso, actividad, arquitectura, E-R) | ✅ Completo |
| Modelo de datos y esquema SQL | ✅ Completo — 21 tablas, 34 invariantes verificadas contra PostgreSQL 17 |
| Decisiones de arquitectura documentadas | ✅ Completo |
| Documentación del uso de IA | ✅ Completo |
| Registro de deuda técnica | ✅ Completo — 15 ítems, cada uno con su disparador y plan de pago |
| Backend — los diez módulos de negocio | ✅ Completo — 573 clases, 94 endpoints |
| Backend — pruebas | ✅ Completo — 85 clases `*Test` y 51 `*IT` con Testcontainers |
| Frontend (SPA) | ✅ Completo — 11 módulos, 13 rutas |
| `compose.yml` | ◐ Parcial — contenedoriza `db` y `backend`; el frontend corre nativo con `make up` |

**Por qué se declara así.** Un README que prometa más de lo que arranca se desmiente en diez segundos. Cada ✅ de esta tabla corresponde a algo que se ejecutó, y la sección [Ejecución](#ejecución) dice con qué comando reproducirlo.

### Cómo se construyó

El backend se levantó en ocho ciclos de desarrollo guiado por especificación, uno por unidad de trabajo. Cada ciclo produjo, **antes** de escribir código, un contrato de aceptación —alcance, trazabilidad `RF`/`RN` → `CU` → `HU`, superficie de API, matriz de autorización y taxonomía de errores— y un diseño con el modelo de dominio, los puertos y las fronteras transaccionales. Recién después se implementó, en tres cortes revisables: dominio y aplicación, infraestructura y web, y verificación transversal.

Los ocho ciclos están archivados en [`openspec/changes/archive/`](./openspec/changes/archive/) con su contrato, su diseño, su lista de tareas, su informe de verificación y su informe de cierre. **Cualquier decisión de este sistema se puede rastrear hasta el documento donde se tomó y hasta la razón que la sostuvo.**

Ese proceso encontró defectos que ninguna lectura habría encontrado: una consulta que compilaba pero fallaba contra PostgreSQL real siempre que se la invocaba sin rango de fechas, y una regresión entre módulos que sólo apareció cuando un puerto dejó de ser un contrato vacío. Por eso las pruebas de integración se escribieron contra invariantes —atomicidad del Kardex, aislamiento entre sucursales, concurrencia sobre el mismo saldo— y no contra cobertura.

---

## Ejecución

### Levantar el sistema

Desde la raíz del repositorio, sin configuración previa:

```bash
docker compose up
```

Levanta dos servicios: `db` (PostgreSQL 17, con el esquema y las semillas de `backend/init-db/`) y `backend` (Java 25 + Spring Boot 4.1).

**El frontend no está contenedorizado.** Para levantar el sistema completo:

```bash
make up    # db y backend en contenedores; frontend nativo con recarga en caliente
```

`make help` lista los demás objetivos. La SPA queda fuera del Compose a propósito durante el desarrollo: servirla desde un contenedor obliga a reconstruir la imagen en cada cambio y se pierde la recarga en caliente de Vite. Para un despliegue real corresponde agregarle su `Dockerfile` de construcción estática y su servicio en el Compose: está registrado como `DT-15`.

> **Sobre el nombre del archivo.** El enunciado pide un `docker-compose.yml`; este repositorio entrega un **`compose.yml`**. Desde Compose V2 ese es el nombre canónico que la herramienta busca primero, y `docker-compose.yml` se conserva únicamente por compatibilidad con la V1. El entregable real es que un solo comando levante la solución, y `docker compose up` funciona igual con cualquiera de los dos nombres —con la salvedad del frontend, que hoy corre fuera del Compose (`DT-15`). La divergencia se declara acá en lugar de dejarla implícita.

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

El segundo levanta un contenedor efímero de PostgreSQL 17, carga el esquema y los datos semilla, ejercita 34 invariantes de negocio —stock negativo, roles válidos, jerarquía de precios, tope de descuento, estados de transferencia— y destruye el contenedor al terminar.

### Levantar solo la base de datos

```bash
docker run --rm -d --name optiplant_db \
  -e POSTGRES_PASSWORD=optiplant -e POSTGRES_DB=optiplant -p 5432:5432 \
  -v "$PWD/backend/init-db:/docker-entrypoint-initdb.d:ro" \
  postgres:17-alpine
```

Los scripts de `backend/init-db/` se ejecutan automáticamente al inicializar el volumen. Verificado: crean **21 tablas** y cargan 3 sucursales, 7 usuarios, 5 productos, 3 listas de precios con 16 precios vigentes e históricos, 6 rutas logísticas, 1 transferencia en curso y 2 alertas activas.

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

### Lo que `docker compose up` no hace

No ejecuta migraciones. Flyway está **declarado y explícitamente desactivado**: mientras el volumen se inicialice con `backend/init-db/`, Flyway encontraría tablas que no creó y fallaría. El procedimiento de sustitución está en `DT-01` de [`docs/deuda_tecnica.md`](./docs/deuda_tecnica.md).

**Tras cualquier cambio en `backend/init-db/`, recreá el volumen con `docker compose down -v`.** PostgreSQL ejecuta los scripts de inicialización sólo cuando el directorio de datos está vacío: sobre un volumen ya inicializado el esquema nuevo se ignora en silencio y la aplicación arranca contra el viejo, sin ningún error que lo delate.

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
│       ├── 01-init-schema.sql   21 tablas, restricciones e índices
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
Datos          PostgreSQL 17 · 21 tablas · Kardex append-only · bloqueo pesimista
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
| `sales` | Ventas, comprobantes, anulaciones y el sub-dominio de clientes | CU-VEN-01, 03 … 06, CU-EXT-02 |
| `transfers` | Máquina de estados de traslados entre sucursales | CU-TRA-01 … 06 |
| `logistics` | Rutas, tiempos estimados y cumplimiento | CU-LOG-01 … 03 |
| `notifications` | Motor de alertas y eventos operativos | CU-ALE-01, 02 |
| `analytics` | Dashboards y KPI — solo lectura | CU-DSH-01 … 03, CU-EXT-01 |

Los clientes viven **dentro** de `sales` y no como módulo aparte: no tienen ciclo de vida propio fuera de la venta, así que darles un módulo habría creado una frontera que ninguna regla de negocio pedía.

`analytics` es el único módulo que no declara ninguna entidad JPA. Lee por consulta nativa las tablas de los otros nueve, de modo que cada tabla conserva un solo dueño y la frontera existe también en la base de datos, no sólo en el código.

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
| [Especificación de requerimientos](docs/especificacion_requerimientos.md) | 43 RF, 34 RNF, 17 reglas de negocio, restricciones, supuestos, glosario y alcance excluido | 6.1 |
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
| `validar_trazabilidad.py` | Que todo identificador citado exista, que todo requerimiento tenga caso de uso, que todo caso de uso tenga requerimiento, que toda deuda tenga ficha y que ningún enlace esté roto | `43 RF · 34 RNF · 17 RN · 39 CU · 14 DT` |
| `validar_esquema.sh` | 34 invariantes de negocio contra PostgreSQL 17 real | `34 comprobaciones correctas` |
| `cd backend && ./mvnw verify` | Las fronteras de arquitectura con ArchUnit, las pruebas unitarias de dominio y las de integración con Testcontainers | `BUILD SUCCESS` |

Los 9 bloques Mermaid y los 2 archivos PlantUML se verificaron renderizándolos con `mermaid-cli` y `plantuml.jar`.

---

## Deuda Técnica

Las decisiones de postergar trabajo están registradas en [`deuda_tecnica.md`](docs/deuda_tecnica.md), cada una con su justificación, su plan de pago y la condición que lo dispara. La más relevante:

**DT-01 — versionado del esquema con Flyway.** Hoy el esquema se carga con el mecanismo de inicialización de la imagen de PostgreSQL, que sólo actúa sobre un volumen vacío: sirve para reconstruir desde cero y es inservible en cuanto exista un dato que preservar. Es la deuda de mayor severidad del registro y la primera que se pagaría en un despliegue real.

De las 15 registradas, **una ya está saldada**: `DT-07` difería la exposición HTTP del cambio de unidad base porque la regla dependía de una respuesta que sólo `inventory` podía dar. Cuando ese módulo se construyó, la razón del diferimiento desapareció y la deuda se pagó, con sus dos códigos de error diferenciados —uno para el rechazo de negocio, otro para la carencia de infraestructura— porque unificarlos habría hecho que un fallo del servidor se leyera como una decisión del dominio.

Ninguna deuda de este registro es una tarea olvidada: cada una nombra qué la dispara. Una deuda sin condición de pago es una excusa con formato de tabla.

---

## Resumen

Un sistema de inventario multi-sucursal completo: **diez módulos de negocio, 39 casos de uso, 94 endpoints y una interfaz web de once módulos**, sobre un esquema de 21 tablas cuyas invariantes se verifican contra un PostgreSQL real.

Las tres decisiones que más lo condicionan, y que responden a la pregunta rectora de este repositorio:

**Monolito modular con hexagonal dentro de cada módulo, verificado a mano.** Las fronteras se declaran explícitamente con reglas de ArchUnit —el dominio sin Spring ni JPA, la aplicación sin adaptadores, ningún módulo alcanzando el interior de otro, `shared` como hoja— en lugar de derivarse de la detección automática de un framework. Spring Modulith se retiró a propósito: una frontera que un framework infiere es una frontera que nadie escribió.

**La atomicidad no viaja por eventos.** Descontar stock, escribir el movimiento en el Kardex y cerrar la operación ocurren en una sola transacción, a través de un puerto síncrono. Los eventos de dominio se reservan para lo que puede fallar sin revertir la operación original: alertas y proyecciones analíticas, siempre después del commit. El desacoplamiento lo da la interfaz, no el evento.

**El saldo es una proyección del histórico.** El Kardex es *append-only*: ninguna anulación borra un movimiento, agrega uno de reversión. Reproducir el histórico desde la carga inicial tiene que devolver el saldo actual, y una prueba de integración lo verifica.

Cada una de estas decisiones —y las cuarenta que no caben acá— está escrita con su razón en [`docs/`](./docs/) y en los ocho ciclos archivados de [`openspec/changes/archive/`](./openspec/changes/archive/).
