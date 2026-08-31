# Documento de Decisiones de Arquitectura Técnica (ADR)
## Sistema de Gestión de Inventario Multi-Sucursal — OptiPlant Consultores
### Cumplimiento: Sección 8.1 — Separación de Responsabilidades · Sección 8.2 — Decisiones Técnicas a Documentar

| Versión | Fecha | Cambios |
| :--- | :--- | :--- |
| 1.5 | 2026-08-31 | Se incorpora el registro de deuda técnica como sección 7 de este documento; `deuda_tecnica.md` deja de existir como archivo separado. El contenido de las fichas DT-01 … DT-15 no cambia. |
| 1.4 | 2026-08-30 | Se actualiza la fila de `sales` en la sección 2.4: el módulo incorporó el sub-dominio de clientes, con lo que suma la administración de clientes y la consulta de su histórico de compras a sus casos de uso. Los clientes no constituyen un módulo aparte; viven dentro de `sales` porque no tienen ciclo de vida propio fuera de la venta. |
| 1.0 | 2026-08-25 | Decisiones de lenguaje, base de datos, autenticación, sincronización de inventario y patrones. |
| 1.3 | 2026-08-27 | Se retira Spring Modulith. El estilo arquitectónico no cambia —monolito modular con hexagonal dentro de cada módulo— pero la verificación de fronteras pasa a declararse con reglas explícitas de ArchUnit en lugar de derivarse de la detección automática del framework. Se elimina en consecuencia la justificación de la sección 3.1 que apoyaba la elección del runtime en esa herramienta: ArchUnit no es una razón para elegir Spring Boot. |
| 1.2 | 2026-08-27 | Se retira de la matriz de la sección 6 la fila del requerimiento de consistencia de estilo, eliminado del SRS en su versión 1.3. Esa fila apuntaba a la sección 3.7, que trata el versionado del esquema con Flyway y no menciona formato ni análisis estático: era una trazabilidad declarada sin respaldo. |
| 1.1 | 2026-08-26 | Se incorpora la sección 8.1 (separación de responsabilidades). Se corrige la nomenclatura de roles para alinearla con el esquema. Se separa la comunicación entre módulos en eventos de consistencia y de notificación. Se agregan las decisiones de frontend, migraciones, contrato de API y observabilidad. Se incorpora el módulo de precios y la matriz de trazabilidad RNF → decisión. |

---

## 1. Resumen Ejecutivo

La solución combina un **Monolito Modular** a nivel de sistema (*macro-arquitectura*) con **Arquitectura Hexagonal (Puertos y Adaptadores)** a nivel de módulo (*micro-arquitectura*), garantizando alta cohesión, bajo acoplamiento, aislamiento de reglas de negocio y consistencia transaccional estricta (**ACID**).

Toda decisión de este documento se justifica contra un requerimiento no funcional de [`especificacion_requerimientos.md`](./especificacion_requerimientos.md); la sección 6 cierra esa trazabilidad de forma explícita.

---

## 2. Separación de Responsabilidades (Sección 8.1)

### 2.1. Las Cuatro Capas

Definir qué hace cada capa importa; definir qué **no** hace importa más, porque es ahí donde la separación se rompe en la práctica.

| Capa | Responsabilidades | Lo que esta capa NO hace |
| :--- | :--- | :--- |
| **Presentación** (`frontend`) | Renderizar la interfaz responsiva, capturar entradas, validar formato en cliente para dar respuesta inmediata, gestionar el token de sesión y presentar los errores devueltos por la API. | No decide reglas de negocio, no calcula costos ni totales autoritativos, no valida disponibilidad de stock, no accede a la base de datos y no conoce identificadores internos. |
| **Negocio** (`backend`) | Autenticar y autorizar, ejecutar los casos de uso, hacer cumplir las reglas RN-01 … RN-17, calcular el Costo Promedio Ponderado, gobernar la máquina de estados de transferencias, delimitar las transacciones y exponer la API. | No renderiza vistas, no asume que el cliente validó nada y no delega reglas de negocio a la base de datos mediante *triggers* o procedimientos almacenados. |
| **Datos** (`db`) | Persistir de forma duradera, garantizar integridad referencial, sostener las invariantes críticas mediante restricciones declarativas, proveer aislamiento transaccional y bloqueo pesimista. | No contiene lógica de negocio ni orquesta procesos; sus restricciones son la última línea de defensa, no la primera. |
| **Infraestructura** (`docker`) | Aislar cada servicio en su contenedor, definir la red, los volúmenes, los *healthchecks* y las variables de entorno; levantar el sistema completo con un solo comando. | No participa del dominio ni contiene configuración específica del negocio. |

### 2.2. Reglas de Frontera

1. **La validación es responsabilidad del backend, siempre.** El frontend valida para mejorar la experiencia; el backend valida porque es lo único que no se puede evadir. Una validación que solo existe en el cliente no existe.
2. **La sucursal se deriva de la sesión, nunca del cliente** (RN-14). Ningún endpoint acepta `branch_id` como parámetro de mutación.
3. **La API expone únicamente `external_id`** (RNF-API-02). Los identificadores numéricos internos jamás cruzan la frontera del backend.
4. **Las restricciones de base de datos duplican deliberadamente validaciones del dominio** (RNF-INT-03). Es redundancia intencional: si un defecto del código deja pasar un stock negativo, el `CHECK` lo detiene.

### 2.3. Vista de Conjunto

```
+-----------------------------------------------------------------------------------+
|                    PRESENTACIÓN — servicio  frontend                              |
|            React 19 + Vite + TypeScript · SPA responsiva · sin reglas de negocio   |
+-----------------------------------------------------------------------------------+
                                         │ HTTPS · REST/JSON · sólo external_id
                                         ▼
+-----------------------------------------------------------------------------------+
|                    NEGOCIO — servicio  backend  (Spring Boot 4.1 / Java 25)       |
|                                                                                   |
|  +---------+ +-----------+ +---------+ +------------+ +-----------+               |
|  |   iam   | |  catalog  | | pricing | | inventory  | | purchases |               |
|  +---------+ +-----------+ +---------+ +------------+ +-----------+               |
|  +---------+ +-----------+ +---------+ +------------+ +-----------+               |
|  |  sales  | | transfers | |logistics| |notifications| | analytics |              |
|  +---------+ +-----------+ +---------+ +------------+ +-----------+               |
|                                                                                   |
|   Cada módulo: adaptadores → puertos → dominio puro → puertos → adaptadores       |
|   Fronteras verificadas por pruebas de arquitectura ArchUnit (RNF-MAN-02)         |
+-----------------------------------------------------------------------------------+
                                         │ JDBC · HikariCP · transacciones ACID
                                         ▼
+-----------------------------------------------------------------------------------+
|                    DATOS — servicio  db  (PostgreSQL 17)                          |
|     21 tablas · Kardex append-only · bloqueo pesimista · CHECK como red de fondo   |
+-----------------------------------------------------------------------------------+

        Todo lo anterior corre dentro de INFRAESTRUCTURA — Docker Compose
```

### 2.4. Módulos del Backend

| Módulo | Responsabilidad | Casos de uso |
| :--- | :--- | :--- |
| `iam` | Usuarios, roles, sesiones y bitácora de auditoría | CU-SEG-01 … CU-SEG-04 |
| `catalog` | Productos, categorías y unidades de medida | CU-INV-01, CU-INV-02 |
| `pricing` | Listas de precios, vigencias y topes de descuento | CU-VEN-02 |
| `inventory` | Existencias por sucursal, Kardex, ajustes y umbrales | CU-INV-03 … CU-INV-08 |
| `purchases` | Órdenes de compra, recepción y Costo Promedio Ponderado | CU-COM-01 … CU-COM-05 |
| `sales` | Ventas, comprobantes, anulaciones y el sub-dominio de clientes | CU-VEN-01, CU-VEN-03 … CU-VEN-06 |
| `transfers` | Máquina de estados de traslados entre sucursales | CU-TRA-01 … CU-TRA-06 |
| `logistics` | Rutas, tiempos estimados y cumplimiento | CU-LOG-01 … CU-LOG-03 |
| `notifications` | Motor de alertas y eventos operativos | CU-ALE-01, CU-ALE-02 |
| `analytics` | Dashboards y KPI — **solo lectura**, no muta estado | CU-DSH-01 … CU-DSH-03 |

---

## 3. Decisiones Técnicas Fundamentales (Sección 8.2)

### 3.1. Lenguaje y Runtime de Backend: Java 25 (LTS) + Spring Boot 4.1

#### Decisión
**Java 25 (LTS)** sobre **Spring Boot 4.1** (Spring Framework 7, Jakarta EE 11).

#### Justificación Técnica
1. **Tipado fuerte y modelado expresivo del dominio.** *Records* inmutables, *pattern matching* exhaustivo y *sealed interfaces* permiten blindar la máquina de estados de transferencias contra estados inválidos **en tiempo de compilación**, no en tiempo de ejecución.
2. **Concurrencia con hilos virtuales (Project Loom).** El sistema es intensivo en E/S: consultas de catálogo, lecturas cross-branch y transacciones. Los hilos virtuales maximizan el rendimiento sin obligar a escribir código reactivo, que complicaría un dominio ya de por sí transaccional. *Sostiene RNF-PER-01 y RNF-PER-02.*
3. **Gestión transaccional declarativa madura.** `@Transactional` con *rollback* automático es exactamente lo que exige RNF-INT-01: una venta que falla a mitad de camino no deja stock descontado sin su asiento en el Kardex.

### 3.2. Stack de Frontend: React 19 + Vite + TypeScript + Ecosistema TanStack & shadcn/ui

#### Decisión
**React 19** con **Vite**, **TypeScript** (modo estricto) y el siguiente conjunto arquitectónico de soporte:
* **Enrutamiento:** TanStack Router (enrutamiento seguro con tipado estricto y sincronización de *search params* en URL).
* **Estado de Servidor / Data Fetching:** TanStack Query v5 (gestión de caché, reintentos, *optimistic updates* e invalidación tras mutaciones).
* **Componentes y Estilos:** Tailwind CSS + shadcn/ui (componentes accesibles basados en Radix UI, sin runtime overhead).
* **Formularios y Validación:** React Hook Form + Zod (validación de esquemas en cliente replicando el contrato OpenAPI).
* **Tablas de Datos:** TanStack Table (tablas densas de inventario y Kardex con soporte de ordenamiento, filtros y virtualización).

#### Justificación Técnica
1. **Tipado de extremo a extremo y validación en cliente.** TypeScript estricto más Zod y tipos OpenAPI aseguran que cambios en los contratos del backend rompan la compilación del frontend antes de llegar a producción.
2. **Sincronización de estado en tablas densas.** TanStack Router combinado con TanStack Table y Zod permite validar y persistir filtros, paginación y ordenamiento en los *search params* de la URL de forma completamente tipada, permitiendo compartir enlaces y mantener estado de vistas de stock.
3. **Desacoplamiento de reglas de negocio y Optimistic UI.** TanStack Query gestiona el ciclo de vida de peticiones y permite actualizar la interfaz de manera optimista en operaciones clave de inventario sin trasladar autoridad de dominio al cliente (*sostiene RNF-USA-02*).
4. **Vite entrega arranque en frío casi instantáneo**, optimizando el flujo de desarrollo local y la construcción de la imagen contenerizada en Docker.
5. **Es una decisión reversible.** El frontend consume exclusivamente la API REST; reemplazarlo no modifica el backend, cumpliendo la separación de responsabilidades y la restricción técnica 2.

### 3.3. Motor de Base de Datos y Modelo de Datos: PostgreSQL 17

#### Decisión
**PostgreSQL 17** como motor relacional único, con **modelo normalizado (3FN)**, patrón **PK numérica (`BIGINT IDENTITY`) + token público (`external_id UUID`)** y tablas *append-only* para Kardex y auditoría. **21 tablas.**

#### Justificación Técnica
1. **Integridad transaccional ACID innegociable.** El inventario es un activo financiero. No se admite consistencia eventual en ventas concurrentes ni en traslados de mercancía. *Sostiene RNF-INT-01.*
2. **PK `BIGINT` + `external_id` UUID.**
   * **Rendimiento:** las claves foráneas y los *JOIN* operan sobre enteros de 64 bits, reduciendo a la mitad el tamaño de los índices B-Tree frente a UUID de 128 bits y evitando la fragmentación por inserción aleatoria.
   * **Seguridad:** la API jamás expone los identificadores internos; todas las rutas usan `external_id`, bloqueando ataques de enumeración (IDOR/BOLA). *Sostiene RNF-SEC-05 y RNF-API-02.*
3. **Prevención de condiciones de carrera.** Bloqueo pesimista (`SELECT ... FOR UPDATE`) durante el descuento de existencias, más `CHECK (current_stock >= 0)` a nivel de esquema como última línea de defensa. *Sostiene RN-01 y RNF-INT-03.*
4. **Trazabilidad absoluta mediante Kardex inmutable.** Cada movimiento se inserta con *timestamp* UTC, responsable, sucursal, motivo, cantidad, stock previo y stock resultante. El saldo actual es una proyección verificable contra el histórico. *Sostiene RN-02 y RNF-INT-02.*
5. **Modelo de precios versionado por vigencia.** `price_lists` define listas comerciales con su tope de descuento; `price_list_items` guarda el precio por producto con excepción opcional por sucursal y vigencia acotada (`valid_from` / `valid_to`). El precio no se sobrescribe: se cierra y se sucede, de modo que toda venta pasada sigue siendo reconstruible. Dos índices únicos parciales impiden precios vigentes duplicados. *Sostiene RF-VEN-03, RN-16 y RN-17.*

### 3.4. Estrategia de Autenticación y Autorización

#### Decisión
Esquema **stateless con JWT** firmados (HMAC-SHA256 o RSA), **Spring Security 7**, **RBAC** y **aislamiento por contexto de sucursal**.

#### Justificación Técnica
1. **Ausencia de estado en el servicio.** El token transporta los *claims* (`sub`, `role`, `branch_id`), eliminando la sesión en memoria y permitiendo ejecutar varias instancias del backend sin afinidad de sesión. *Sostiene RNF-ESC-03.*
2. **Modelo de roles.** Los valores son exactamente los que admite la restricción `CHECK` de la tabla `users`, sin prefijos ni variantes:

   | Valor en el sistema | Alcance |
   | :--- | :--- |
   | `ADMIN` | Visibilidad y potestad corporativa sobre toda la red, configuración y auditoría global |
   | `BRANCH_MANAGER` | Supervisión, aprobaciones y dashboard de su propia sucursal |
   | `OPERATOR` | Ejecución operativa dentro de su sucursal |

   > El valor persistido, el *claim* del token y la autoridad de Spring Security deben ser **la misma cadena**. Introducir un prefijo `ROLE_` en cualquiera de los tres puntos rompe la validación contra el esquema; si se usa `hasRole()` —que antepone el prefijo internamente— debe configurarse explícitamente el prefijo vacío o emplearse `hasAuthority()`.

3. **Aislamiento lógico por sucursal.** Un filtro de seguridad inyecta el `branch_id` del token en el contexto de cada caso de uso. Las **mutaciones** quedan restringidas a la sucursal propia; las **consultas** de inventario de otras sucursales se permiten en modo solo lectura. *Sostiene RNF-SEC-03, RN-08 y RN-14.*
4. **Contraseñas.** Cifrado con BCrypt (factor de trabajo ≥ 10) o Argon2id. *Sostiene RNF-SEC-02.*

### 3.5. Mecanismo de Sincronización de Inventario entre Sucursales

#### Decisión
**Base de datos centralizada con aislamiento lógico**, más una **máquina de estados transaccional** para los traslados.

```
[ Sucursal Origen ]                                                [ Sucursal Destino ]
        │                                                                   │
        │ 1. Solicitud de traslado (REQUESTED)                              │
        │ ◄─────────────────────────────────────────────────────────────────┤
        │ 2. Aprobación y preparación (IN_PREPARATION)                      │
        ├─────────────────────────────────────────────────────────────────► │
        │ 3. Despacho — una sola transacción:                               │
        │      origen.current_stock      −= Q                               │
        │      destino.in_transit_stock  += Q                               │
        │      Kardex origen: TRANSFER_OUT      (Estado: IN_TRANSIT)        │
        ├─────────────────────────────────────────────────────────────────► │
        │ 4. Recepción — una sola transacción:                              │
        │      destino.in_transit_stock  −= Q                               │
        │      destino.current_stock     += Q_recibida                      │
        │      Kardex destino: TRANSFER_IN                                  │
        │      (RECEIVED  o  RECEIVED_WITH_DISCREPANCY)                     │
        │ ◄─────────────────────────────────────────────────────────────────┤
```

#### Justificación Técnica
1. **Visibilidad inmediata sin complejidad distribuida.** Al residir todas las sucursales en una base de datos única, la consulta cross-branch es una lectura indexada ordinaria: no hay replicación, ni ventana de inconsistencia, ni reconciliación. El objetivo de latencia es el de RNF-PER-01 (95% bajo 200 ms con la volumetría de referencia) y se verifica con las métricas de RNF-OBS-03, no por afirmación.
2. **Conservación de masa del inventario.** `in_transit_stock` es una columna de `branch_inventories` —el saldo por sucursal y producto— y en el despacho se incrementa el de la **sucursal destino**. Mientras la mercancía viaja, la unidad ya no está disponible en origen y aún no es vendible en destino, pero es visible en ambos lados. **Invariante: el stock corporativo total nunca se pierde ni se duplica durante el viaje.** *Sostiene RN-04.*
3. **La aprobación no reserva stock.** Entre la aprobación y el despacho la sucursal origen sigue vendiendo, de modo que el despacho **revalida** disponibilidad y puede devolver la transferencia a `IN_PREPARATION`. Reservar en la aprobación habría inmovilizado mercancía por tiempo indefinido a costa de las ventas locales; se eligió deliberadamente lo contrario.
4. **Gestión atómica de discrepancias.** Ante recepción parcial se ingresa únicamente lo recibido, se libera la totalidad del tránsito, se registra el faltante y se emite una alerta crítica. *Sostiene RN-06 y RN-07.*

### 3.6. Comunicación entre Módulos: Puertos Síncronos y Eventos de Dominio

#### Decisión
Se distinguen **dos mecanismos con propósitos distintos**, y la elección entre ellos depende de si la operación debe ser atómica:

| Mecanismo | Cuándo se usa | Implementación |
| :--- | :--- | :--- |
| **Puerto de salida (interfaz), llamada síncrona** | Cuando el efecto debe ocurrir **dentro de la misma transacción** que lo origina | `sales` depende de `InventoryPort`; el adaptador ejecuta el descuento y el asiento del Kardex en la transacción abierta por el caso de uso |
| **Evento de dominio posterior al commit** | Cuando el efecto es una **reacción** que no debe poder abortar la operación original | `@TransactionalEventListener(phase = AFTER_COMMIT)` sobre `SaleCompletedEvent`: generar alertas, actualizar proyecciones de analítica |

#### Justificación Técnica
1. **La atomicidad no es negociable y por eso no viaja por eventos.** Descontar stock, insertar el movimiento en el Kardex y cerrar la venta ocurren juntos o no ocurren. Si ese efecto se delegara a un escucha asíncrono, quedaría fuera de la transacción y bastaría un fallo del escucha para dejar una venta confirmada sin descuento de inventario — violando RN-01, RN-02 y RNF-INT-01 de una sola vez.
2. **El desacoplamiento se logra con la interfaz, no con el evento.** `sales` no conoce la implementación de `inventory`: conoce un puerto. Eso da la frontera de módulo sin sacrificar la transacción.
3. **Las notificaciones sí van por evento, y después del commit.** Si la generación de una alerta de stock mínimo falla, la venta ya está confirmada y no debe revertirse por ello. Publicar en `AFTER_COMMIT` es exactamente esa garantía.
4. **`@Async` queda prohibido para todo efecto que deba ser atómico.** Un escucha asíncrono corre en otro hilo y en otra transacción: es la herramienta correcta para enviar un correo y la herramienta equivocada para tocar existencias.

### 3.7. Versionado y Evolución del Esquema

#### Decisión
**Flyway** como mecanismo de versionado del esquema, con las migraciones bajo control de versiones junto al código.

#### Justificación Técnica
1. **Los scripts actuales de `init-db/` sirven para arrancar, no para evolucionar.** El mecanismo de inicialización de la imagen de PostgreSQL solo ejecuta scripts sobre un volumen vacío: al segundo arranque no hace nada. Es correcto para un entorno reproducible desde cero, e insuficiente en cuanto exista un dato que preservar.
2. **Una migración versionada es una decisión auditable.** Cada cambio de esquema queda registrado, ordenado y verificado contra la base al arrancar la aplicación, lo que impide el clásico desfase entre el esquema del desarrollador y el del entorno de evaluación.
3. **Estado actual y transición.** Mientras no exista backend, `01-init-schema.sql` y `02-seed-data.sql` cumplen su función de bootstrap. Al iniciar la implementación se convierten en `V1__initial_schema.sql` y en un *seeder* de datos de demostración separado de las migraciones, y el arranque por `init-db/` se retira para evitar dos fuentes de verdad. Registrado como asunto abierto **OI-A1**.

### 3.8. Contrato de API y Observabilidad

#### Decisión
**OpenAPI 3.1** generada desde el código, **versionado por prefijo de ruta** (`/api/v1`), **logs estructurados en JSON**, **sondas de salud** y **métricas** vía Spring Boot Actuator y Micrometer.

#### Justificación Técnica
1. **El enunciado exige una API bien definida; una API sin contrato publicado no lo está.** La especificación se genera desde las anotaciones del código, de modo que no pueda quedar desactualizada respecto de la implementación. *Sostiene RNF-API-01.*
2. **Versionado desde el día uno.** Introducir `/api/v1` cuando existe un solo consumidor cuesta nada; introducirlo cuando ya hay un POS externo integrado cuesta una migración coordinada. *Sostiene RF-EXT-01 y RF-EXT-02.*
3. **Errores con estructura uniforme.** Todas las respuestas de error comparten forma —código de negocio, mensaje y detalle—, lo que permite al frontend tratarlas de manera genérica. *Sostiene RNF-API-02 y RNF-USA-02.*
4. **Sondas de salud consumidas por Docker Compose.** El `healthcheck` de cada servicio consulta el endpoint de estado, de modo que el backend no se declare disponible antes que la base de datos. *Sostiene RNF-OBS-02.*
5. **Logs con identificador de correlación y sin datos sensibles**, y métricas de latencia, tasa de error y volumen por endpoint que permiten verificar empíricamente los objetivos de rendimiento. *Sostiene RNF-OBS-01 y RNF-OBS-03.*

### 3.9. Patrones de Diseño y Arquitectura Aplicados

| Patrón | Nivel | Justificación Técnica |
| :--- | :--- | :--- |
| **Monolito Modular** | Sistema | Agrupa la lógica en los diez módulos de la sección 2.4. Las fronteras se verifican con reglas de ArchUnit declaradas explícitamente —dominio sin Spring ni JPA, aplicación sin adaptadores, ningún módulo alcanzando el interior de otro, `shared` como hoja— y no por convención ni por la detección automática de un framework. Entrega el aislamiento del microservicio sin su costo operativo ni sus transacciones distribuidas. *Sostiene RNF-MAN-02.* |
| **Arquitectura Hexagonal (Puertos y Adaptadores)** | Módulo | Aísla las reglas de negocio —CPP, validación de existencias, máquina de estados— en un dominio puro, sin dependencias de Spring, JPA ni HTTP. Permite probar el núcleo sin levantar infraestructura. *Sostiene RNF-MAN-01.* |
| **Repository Pattern** | Persistencia | Abstrae el acceso a datos tras puertos de salida, permitiendo sustituir la persistencia por dobles de prueba sin levantar base de datos. |
| **State Pattern / Máquina de Estados** | Transferencias y compras | Hace imposible una transición ilegal: pasar de `REQUESTED` a `RECEIVED` sin atravesar `IN_TRANSIT` no compila ni se ejecuta. *Sostiene RN-05 y RN-15.* |
| **Domain Events (posteriores al commit)** | Comunicación entre módulos | Desacopla las reacciones que no deben abortar la operación original: alertas y proyecciones analíticas. Ver la distinción de la sección 3.6. |
| **Value Object (DDD)** | Dominio | Modela conceptos inmutables con sus reglas incorporadas (`SKU`, `Money`, `Quantity`, `Threshold`), evitando la obsesión por primitivos y concentrando la validación en el tipo. |
| **Optimistic UI con confirmación del servidor** | Presentación | El frontend refleja el cambio de inmediato pero solo lo consolida con la respuesta de la API; ante rechazo revierte. Mejora la percepción sin trasladar autoridad al cliente. *Sostiene RNF-USA-02.* |

---

## 4. Matriz de Compensación de Decisiones (Trade-offs)

| Alternativa evaluada | Decisión tomada | Justificación |
| :--- | :--- | :--- |
| Microservicios distribuidos vs. **Monolito modular** | **Monolito modular** | Los microservicios exigirían sagas y compensaciones para sostener la atomicidad entre stock, Kardex y venta. Es una complejidad enorme para un dominio que cabe cómodamente en una sola base de datos. |
| NoSQL (MongoDB) vs. **PostgreSQL 17** | **PostgreSQL 17** | El modelo BASE prioriza disponibilidad sobre consistencia inmediata, lo que es inaceptable para inventario y auditoría financiera: no se toleran faltantes fantasma ni duplicados. |
| Lógica en el frontend o en *triggers* SQL vs. **dominio en el backend** | **Dominio en el backend** | Una única fuente de verdad, verificable y reutilizable por varios clientes (web, POS, API externa). Los *triggers* esconden reglas donde nadie las prueba. |
| Bloqueo optimista (versionado) vs. **bloqueo pesimista** | **Pesimista en las mutaciones de stock** | En operaciones cortas y de alta contención sobre las mismas filas, el optimista degenera en reintentos y falsos rechazos al usuario. El pesimista da una respuesta determinista. |
| Reservar stock al aprobar vs. **revalidar al despachar** | **Revalidar al despachar** | Reservar inmovilizaría mercancía por tiempo indefinido a costa de las ventas locales de la sucursal origen. |
| Precio como columna de `products` vs. **listas de precios versionadas** | **Listas de precios** | El enunciado exige gestionar varias listas, y una columna única impide precios por sucursal, por segmento y el histórico necesario para auditar descuentos pasados. |
| Solo `init-db/` vs. **Flyway** | **Flyway** | `init-db/` únicamente actúa sobre un volumen vacío: no permite evolucionar un esquema con datos. Ver sección 3.7. |

---

## 5. Estructura de Paquetes Canónica

```
com.optiplant.inventory/
├── [módulo]/                            # iam, catalog, pricing, inventory, purchases,
│   │                                    # sales, transfers, logistics, notifications, analytics
│   ├── domain/                          # NÚCLEO PURO — Java, sin anotaciones de Spring ni JPA
│   │   ├── model/                       # Entidades y Value Objects inmutables
│   │   ├── exception/                   # Excepciones de negocio
│   │   └── service/                     # Servicios de dominio (CPP, reglas RN)
│   │
│   ├── application/                     # CASOS DE USO Y PUERTOS
│   │   ├── port/in/                     # Interfaces de casos de uso
│   │   ├── port/out/                    # Interfaces de repositorios y publicación de eventos
│   │   └── service/                     # Implementación de los casos de uso
│   │
│   └── infrastructure/                  # ADAPTADORES TÉCNICOS
│       ├── adapter/in/web/              # Controladores REST, DTO de petición y respuesta
│       ├── adapter/out/persistence/     # Entidades JPA, repositorios Spring Data, mapeadores
│       └── config/                      # Configuración de Spring del módulo
│
└── shared/                              # Tipos transversales de dominio y eventos base
```

---

## 6. Trazabilidad: Requerimiento No Funcional → Decisión

Cierra la matriz declarada en la sección 8 de [`especificacion_requerimientos.md`](./especificacion_requerimientos.md): ningún RNF queda sin una decisión que lo sostenga.

| RNF | Decisión que lo materializa |
| :--- | :--- |
| RNF-PER-01, RNF-PER-02 | 3.1 — hilos virtuales · 3.3 — índices sobre PK numérica |
| RNF-PER-03, RNF-PER-04 | 3.5 — base centralizada, lectura indexada · 3.8 — paginación en el contrato |
| RNF-INT-01 | 3.1 — `@Transactional` · 3.6 — puertos síncronos para efectos atómicos |
| RNF-INT-02 | 3.3 — Kardex y auditoría *append-only* |
| RNF-INT-03 | 3.3 — restricciones `CHECK` como última línea de defensa |
| RNF-SEC-01, RNF-SEC-03 | 3.4 — RBAC y aislamiento por contexto de sucursal |
| RNF-SEC-02 | 3.4 — BCrypt/Argon2id y JWT firmado |
| RNF-SEC-04 … RNF-SEC-08 | 3.4 — Spring Security 7 · 3.8 — contrato y errores uniformes |
| RNF-DIS-01 | 2.1 — aislamiento por datos y autorización, no por despliegue |
| RNF-DIS-02, RNF-DIS-03 | 3.7 — migraciones versionadas · 2.1 — infraestructura reproducible |
| RNF-OBS-01 … RNF-OBS-03 | 3.8 — logs estructurados, sondas de salud y métricas |
| RNF-MAN-01 | 3.9 — hexagonal: el núcleo se prueba sin infraestructura |
| RNF-MAN-02 | 3.9 — monolito modular con fronteras verificadas por reglas de ArchUnit |
| RNF-ESC-01, RNF-ESC-02 | 3.9 — hexagonal y monolito modular |
| RNF-ESC-03 | 3.4 — JWT sin estado en el servicio |
| RNF-USA-01 … RNF-USA-04 | 3.2 — React responsivo · 3.9 — *optimistic UI* con confirmación |
| RNF-CON-01, RNF-CON-02 | 2.1 — capa de infraestructura contenerizada y configurada por entorno |
| RNF-API-01, RNF-API-02 | 3.8 — OpenAPI, versionado y `external_id` |

---

## 7. Registro de Deuda Técnica

Esta sección centraliza las **decisiones deliberadas de postergar trabajo** y las **limitaciones conocidas** del diseño. Los ítems **DT-01**, **DT-02**, **DT-03** y **DT-05** derivan de las decisiones de las secciones anteriores; el resto surgió durante el diseño y la verificación de los módulos, y cada ficha indica su origen.

### Historial del registro de deuda

| Versión | Fecha | Cambios |
| :--- | :--- | :--- |
| 1.0 | 2026-08-26 | Registro inicial con seis ítems identificados durante el diseño. |
| 1.1 | 2026-08-28 | Se agregan dos ítems surgidos del diseño del módulo `catalog`: la exposición HTTP diferida del cambio de unidad base y la estrategia de escalada de la búsqueda de productos por texto libre. |
| 1.2 | 2026-08-29 | Se agrega un ítem surgido del diseño del módulo `inventory`: la deduplicación de alertas operativas sin restricción de unicidad en el esquema. |
| 1.3 | 2026-08-29 | Se salda la exposición HTTP del cambio de unidad base: `inventory` ya implementa `ProductStockPresencePort`, así que se publicó `PATCH /api/catalog/products/{externalId}/base-unit` con sus dos códigos de error distintos y la transacción única ya verificada. |
| 1.4 | 2026-08-29 | Se agrega un ítem surgido de la verificación del módulo `inventory`: el tope de tamaño de página se clampea en `catalog` y se rechaza en el resto de los módulos. |
| 1.5 | 2026-08-30 | Se agrega un ítem surgido del diseño del módulo `sales`: la asignación de `invoice_number` sin una secuencia de base de datos. |
| 1.6 | 2026-08-30 | Se salda el ítem «Cliente sin entidad propia en las ventas»: el sub-dominio de clientes dentro de `sales` incorpora la tabla `customers`, el CRUD, la asociación opcional a la venta con congelado del nombre y la identificación, y el histórico de compras por cliente. La segmentación de listas de precios por cliente se mantiene fuera de alcance. |
| 1.7 | 2026-08-30 | Se agrega un ítem surgido del contrato del módulo `purchases`: la asignación de `order_number` sin una secuencia de base de datos. |
| 1.8 | 2026-08-31 | Se agrega un ítem surgido del contrato del módulo `analytics`: el rollup corporativo mensual une `sale_items` sin un índice cubriente. |
| 1.9 | 2026-08-31 | Se agrega un ítem surgido de la revisión de entrega: el frontend no está contenedorizado, de modo que `docker compose up` levanta la mitad de servidor de la solución y no su interfaz. |
| 1.10 | 2026-08-31 | Se salda `DT-15`: el frontend se contenedorizó con `frontend/Dockerfile` (construcción multietapa servida por Nginx) y el servicio `frontend` en `compose.yml`, publicado en `${FRONTEND_PORT:-8081}` con `healthcheck` y `depends_on: backend`. Verificado end-to-end contra el stack completo levantado con un solo comando. |

---

### 7.1. Propósito

Esta sección registra las **decisiones deliberadas de postergar trabajo** y las **limitaciones conocidas** del diseño. Existe porque una deuda no documentada no es una decisión: es un olvido esperando a que alguien lo descubra en el peor momento.

#### Criterio de Inclusión

| Sí es deuda técnica | No es deuda técnica |
| :--- | :--- |
| Se eligió un atajo consciente que costará más caro después. | Una funcionalidad que se decidió no construir (eso es **alcance excluido**, ver [`especificacion_requerimientos.md`](./especificacion_requerimientos.md) §1.3). |
| Una limitación real del diseño que alguien podría dar por resuelta. | Trabajo que simplemente todavía no se hizo y está planificado. |
| Una inconsistencia conocida entre documentos o artefactos. | Una preferencia estética sin consecuencia funcional. |

#### Escala de Severidad

| Nivel | Significado |
| :--- | :--- |
| **Alta** | Bloquea o encarece de forma significativa una etapa futura; hay que pagarla antes de un hito concreto. |
| **Media** | Genera riesgo real de defecto o de trabajo doble, pero el sistema opera correctamente sin resolverla. |
| **Baja** | Limitación conocida y aceptada; se documenta para que nadie la asuma resuelta. |

---

### 7.2. Registro

| ID | Título | Severidad | Estado | Disparador para pagarla |
| :--- | :--- | :--- | :--- | :--- |
| **DT-01** | Versionado del esquema con Flyway | Alta | Aceptada | Al montar el backend |
| **DT-02** | Datos de demostración acoplados al bootstrap del esquema | Media | Aceptada | Al montar el backend |
| **DT-03** | Rangos históricos de precio solapados no restringidos por el esquema | Media | Aceptada | Antes de habilitar la edición de precios históricos |
| **DT-04** | Cliente sin entidad propia en las ventas | Baja | Resuelta | Saldada en la versión 1.6: entidad `customers` con CRUD, asociación e histórico |
| **DT-05** | La coherencia del precio congelado sólo se verifica en el dominio | Baja | Aceptada | Ninguno; se mitiga con pruebas |
| **DT-06** | Tipografía inconsistente en el diagrama E-R | Baja | Aceptada | Si se rehace el diagrama E-R |
| **DT-07** | Exposición HTTP del cambio de unidad base, diferida | Baja | **Resuelta (2026-08-29)** | Ninguno — pagada al construir `inventory` |
| **DT-08** | Búsqueda de productos por texto libre resuelta con recorrido secuencial | Baja | Aceptada | Si el catálogo supera ~50 000 productos o si la prueba de latencia falla |
| **DT-09** | Deduplicación de alertas operativas sin restricción de unicidad en el esquema | Media | Aceptada | Cuando llegue el próximo cambio de esquema |
| **DT-10** | El tope de tamaño de página se resuelve distinto en `catalog` que en el resto de los módulos | Baja | Aceptada | Cuando se pueda ajustar el frontend de `catalog` en el mismo cambio |
| **DT-11** | `transfer_number` se asigna sin una secuencia de base de datos | Baja | Aceptada | Cuando llegue el próximo cambio de esquema |
| **DT-12** | `sales.invoice_number` se asigna sin una secuencia de base de datos | Baja | Aceptada | Cuando llegue el próximo cambio de esquema |
| **DT-13** | `purchase_orders.order_number` se asigna sin una secuencia de base de datos | Baja | Aceptada | Cuando llegue el próximo cambio de esquema |
| **DT-14** | Rollup corporativo mensual sin índice cubriente sobre `sale_items` | Media | Aceptada | Cuando llegue el próximo cambio de esquema |
| **DT-15** | El frontend no está contenedorizado ni entra en `compose.yml` | Media | **Resuelta (2026-08-31)** | Ninguno — pagada contenerizando el frontend |

---

### 7.3. Fichas

#### DT-01 — Versionado del esquema con Flyway

**Severidad:** Alta · **Estado:** Aceptada · **Esfuerzo estimado:** pequeño (menos de media jornada)

#### Situación actual
El esquema y los datos semilla viven en `backend/init-db/01-init-schema.sql` y `02-seed-data.sql`, ejecutados por el mecanismo de inicialización de la imagen de PostgreSQL. Ambos scripts están verificados contra PostgreSQL 17: crean las 21 tablas y cargan los datos sin errores.

#### Por qué se aceptó
Todavía no existe backend. Mover el esquema dentro de un proyecto Java inexistente le quitaría al repositorio la capacidad de levantar la base por sí sola, a cambio de ningún beneficio. Hoy `init-db/` es la herramienta correcta.

#### Por qué es deuda
El mecanismo de `init-db/` **sólo actúa sobre un volumen vacío**. Al segundo arranque no hace absolutamente nada. Sirve para reconstruir desde cero y es inservible en cuanto exista un dato que preservar: no hay forma de aplicar un cambio de esquema sin borrar la base.

#### Plan de pago

1. `01-init-schema.sql` se convierte en `src/main/resources/db/migration/V1__initial_schema.sql`, **sin reescribirlo**: ya está probado.
2. Los datos de demostración se separan a `db/seed/R__demo_data.sql` (ver **DT-02**).
3. Se **elimina el montaje de `init-db/`** del `compose.yml`.
4. El servicio `backend` gana `depends_on: db: { condition: service_healthy }` y el servicio `db` un `healthcheck` con `pg_isready`.

#### Las dos trampas de este cambio

**El paso 3 no es opcional.** Si el volumen se inicializa con los scripts *y* además corre Flyway, Flyway encuentra tablas que no creó y falla. La salida fácil es activar `baseline-on-migrate`, que no resuelve nada: sólo le indica a Flyway que ignore un estado que no comprende. Sostener dos fuentes de verdad sobre el mismo esquema es exactamente el problema que Flyway venía a eliminar.

**El paso 4 tampoco.** Con `init-db/` el orden de arranque es indiferente porque PostgreSQL se inicializa solo. Con Flyway el backend migra al arrancar, así que si sale antes que la base, se cae. La restricción del enunciado —`docker compose up` y nada más— se sigue cumpliendo, pero deja de ser gratuita.

#### Referencias
Sección 3.7 y asunto abierto OI-A1 de este documento · RNF-DIS-03.

---

#### DT-02 — Datos de demostración acoplados al bootstrap del esquema

**Severidad:** Media · **Estado:** Aceptada · **Esfuerzo estimado:** trivial (se resuelve dentro de DT-01)

#### Situación actual
`02-seed-data.sql` se ejecuta automáticamente sobre **cualquier** volumen vacío, sin distinción de entorno. Carga tres sucursales, siete usuarios con contraseña conocida, cinco productos y sus precios.

#### Por qué es deuda
Los datos de demostración no son esquema. Hoy no hay forma de levantar la base sin ellos, lo que significa que un entorno que no sea de desarrollo arrancaría con usuarios de prueba y credenciales conocidas. Mientras el proyecto sea una prueba técnica evaluada localmente el riesgo es nulo; en cualquier otro contexto es un problema de seguridad.

#### Plan de pago
Mover las semillas a una *location* de Flyway separada, activada únicamente por el perfil `dev`: `spring.flyway.locations` incluye `classpath:db/migration` siempre y añade `classpath:db/seed` sólo en `application-dev.yml`. Las migraciones versionadas quedan libres de datos de demostración.

#### Referencias
RNF-SEC-02 · RNF-SEC-07.

---

#### DT-03 — Rangos históricos de precio solapados no restringidos por el esquema

**Severidad:** Media · **Estado:** Aceptada · **Esfuerzo estimado:** pequeño

#### Situación actual
`price_list_items` protege el precio **vigente** con dos índices únicos parciales —`uq_price_current_branch` y `uq_price_current_corporate`— que sólo aplican cuando `valid_to IS NULL`. Los registros históricos, con fecha de término, no están restringidos entre sí.

#### Evidencia
Verificado contra PostgreSQL 17. Se insertaron dos precios históricos con rangos solapados para la misma lista y producto:

```sql
INSERT INTO price_list_items (price_list_id, product_id, branch_id, unit_price, valid_from, valid_to)
VALUES (2, 2, NULL, 5000, DATE '2026-01-01', DATE '2026-06-30'),
       (2, 2, NULL, 6000, DATE '2026-03-01', DATE '2026-09-30');
-- INSERT 0 2  → aceptado
```

Al consultar el precio aplicable al `2026-04-01` compiten **dos filas**: 5000 y 6000. La resolución queda determinada por el `ORDER BY` de la consulta, no por el modelo. El control confirmó que el precio vigente sí está protegido: un segundo `valid_to IS NULL` para la misma combinación es rechazado por el índice único.

#### Por qué se aceptó
El impacto real hoy es nulo: RN-16 resuelve el precio **a la fecha de la operación**, y las operaciones ocurren en el presente, donde el índice único sí garantiza unicidad. El solapamiento sólo produciría ambigüedad al reconstruir un precio de una fecha pasada.

#### Plan de pago — verificado contra PostgreSQL 17

```sql
CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE price_list_items ADD CONSTRAINT excl_price_period
  EXCLUDE USING gist (
    price_list_id            WITH =,
    product_id               WITH =,
    (COALESCE(branch_id, 0)) WITH =,
    daterange(valid_from, valid_to, '[]') WITH &&
  );
```

Tres detalles que hacen que esto funcione, y sin los cuales no funciona:

1. **`COALESCE(branch_id, 0)` es obligatorio.** En una restricción de exclusión, `NULL = NULL` es *desconocido*, de modo que dos filas corporativas jamás se compararían entre sí — que es exactamente el defecto original. La expresión debe ir entre paréntesis por sintaxis. El valor `0` es seguro porque la identidad de `branches` arranca en 1.
2. **`valid_to` nulo no necesita `COALESCE` a infinito.** `daterange` interpreta el límite superior nulo como no acotado: `daterange('2026-08-26', NULL, '[]')` produce `[2026-08-26,)`.
3. **El límite superior es inclusivo (`'[]'`).** Un precio vigente hasta el `2026-08-25` y el siguiente desde el `2026-08-26` no se solapan; si el segundo arrancara el mismo `2026-08-25`, la restricción lo rechaza — que es la conducta correcta.

#### Comportamiento verificado

| Escenario | Resultado |
| :--- | :--- |
| Crear la restricción con datos ya solapados | **Rechazada**, indicando el par de filas en conflicto |
| Insertar un histórico que se solapa con el vigente | **Rechazado** |
| Insertar un histórico contiguo sin solape | **Aceptado** |
| Precio de sucursal y corporativo en el mismo periodo | **Aceptado** — son ámbitos distintos |
| Dos precios vigentes para la misma sucursal | **Rechazado** |

#### Consecuencia sobre los índices actuales
La restricción de exclusión **subsume** a `uq_price_current_branch` y `uq_price_current_corporate`: dos filas con `valid_to` nulo generan rangos no acotados que se solapan por definición. Verificado eliminando ambos índices y comprobando que un segundo precio vigente sigue siendo rechazado.

Aun así conviene **conservarlos**: el índice único devuelve un mensaje de error directo y legible, mientras que el de exclusión devuelve el detalle completo de ambos rangos. Además el índice parcial B-Tree es más económico que el GiST para la consulta del precio vigente, que es la ruta caliente de toda venta.

#### Orden de aplicación
La restricción **no puede crearse sobre datos ya solapados** — se comprobó. En una base con historial hay que detectar y resolver los solapamientos antes de aplicarla:

```sql
SELECT a.id, b.id, a.unit_price, b.unit_price
FROM price_list_items a JOIN price_list_items b
  ON a.id < b.id
 AND a.price_list_id = b.price_list_id
 AND a.product_id = b.product_id
 AND COALESCE(a.branch_id, 0) = COALESCE(b.branch_id, 0)
 AND daterange(a.valid_from, a.valid_to, '[]') && daterange(b.valid_from, b.valid_to, '[]');
```

#### Por qué se postergó
Introducir una dependencia de extensión en el esquema inicial cuando el escenario que la justifica —edición de precios retroactivos— todavía no existe. Con la restricción ya redactada y probada, pagarla es aplicar una migración.

#### Referencias
RN-16 · RF-VEN-03 · sección 4.2 de [`modelado_sistema.md`](./modelado_sistema.md).

---

#### DT-04 — Cliente sin entidad propia en las ventas

**Severidad:** Baja · **Estado:** Resuelta (versión 1.6)

#### Situación original
`sales` guardaba el cliente de forma desnormalizada en `customer_name` y `customer_tax_id`, sin tabla de clientes. No había historial de compras por cliente y el mismo cliente podía quedar escrito de varias formas distintas.

#### Cómo se saldó
El sub-dominio de clientes dentro del módulo `sales` (sin módulo nuevo) incorpora la tabla `customers`, un CRUD completo (`RF-VEN-06`, `CU-VEN-05`), la columna `sales.customer_id` como clave foránea nullable, la asociación opcional al registrar la venta y el histórico de compras por cliente con aislamiento por sucursal (`CU-VEN-06`). El nombre y la identificación tributaria se congelan en la venta al confirmarla, así que una edición posterior del cliente no altera un comprobante pasado. Un índice único parcial sobre `customers.tax_id` impide la duplicación por identificación tributaria.

#### Qué se mantiene fuera de alcance
La **segmentación de listas de precios por perfil de cliente**. `RF-VEN-03` sigue materializándose como listas por sucursal; el asunto abierto OI-02 queda parcialmente resuelto por esta razón.

#### Referencias
`RF-VEN-06` · `CU-VEN-05` · `CU-VEN-06` · asunto abierto OI-02 de [`especificacion_requerimientos.md`](./especificacion_requerimientos.md) · `openspec/changes/archive/` (cambio `add-sales-customers`).

---

#### DT-05 — La coherencia del precio congelado sólo se verifica en el dominio

**Severidad:** Baja · **Estado:** Aceptada

#### Situación actual
`sale_items.list_unit_price` congela el precio de lista al momento de la venta, y la restricción `check_applied_price_not_above_list` garantiza que el precio aplicado nunca lo supere. Pero **nada en el esquema obliga a que ese `list_unit_price` sea efectivamente el precio que la lista tenía vigente en esa fecha**.

#### Por qué se aceptó
Verificarlo en la base de datos exigiría un *trigger* que consulte `price_list_items` en cada inserción — precisamente el antipatrón que la sección 2.1 del ADR prohíbe: reglas de negocio escondidas donde nadie las prueba. La garantía correcta es de dominio.

#### Mitigación
El caso de uso `CU-VEN-01` resuelve el precio y lo congela en la misma operación; una prueba automatizada debe verificar que un `list_unit_price` inconsistente con la lista vigente sea rechazado por el dominio. Queda cubierto por el objetivo de cobertura de RNF-MAN-01.

---

#### DT-06 — Tipografía inconsistente en el diagrama E-R

**Severidad:** Baja · **Estado:** Aceptada · **Esfuerzo estimado:** trivial

#### Situación actual
`diagrams/diagrama_er.excalidraw` usa `fontFamily` 1 y 3, mientras los otros quince diagramas del repositorio usan `fontFamily` 5. Las entidades de precios agregadas después respetaron la tipografía original del archivo para no mezclar dos fuentes dentro del mismo lienzo.

#### Por qué se aceptó
Es puramente estético y no afecta legibilidad ni contenido. Unificar exigiría regenerar el diagrama E-R completo.

---

#### DT-07 — Exposición HTTP del cambio de unidad base, diferida

**Severidad:** Baja · **Estado:** Resuelta (2026-08-29) · **Esfuerzo estimado:** pequeño · **Origen:** diseño del módulo `catalog`

#### Situación previa
El módulo `catalog` entregó la regla de dominio que gobierna el cambio de `products.base_unit`, el puerto entrante `shared/stock/ProductStockPresencePort`, la política que la aplica y sus pruebas unitarias, pero **ningún endpoint las alcanzaba**. Dentro de ese alcance, `base_unit` era de hecho inmutable: se fijaba al crear el producto y `PUT /api/catalog/products/{externalId}` rechazaba el campo con `400 invalid_request`.

#### Por qué se había aceptado diferirla
La regla sólo permite el cambio cuando el producto no tiene saldos ni movimientos de Kardex, y quien podía responder esa pregunta era `inventory`, que todavía no existía. Sin implementación del puerto la política fallaba cerrada —nunca abierta—, de modo que toda llamada habría respondido un conflicto para siempre. Publicar una operación que jamás tuvo éxito es peor que no publicarla: los clientes programarían contra algo que nunca funcionó, y el documento OpenAPI mentiría.

#### Por qué era deuda
La regla, el puerto y la política eran código de producción sin ruta de entrada. Estaban cubiertos por pruebas unitarias precisamente para que no se degradaran a código muerto, pero nadie los ejercitaba de punta a punta.

#### Cómo se pagó
`inventory` ya existe y su `InventoryStockPresenceAdapter` implementa `ProductStockPresencePort` de verdad (el predicado exacto: un producto está intacto cuando **(a)** no tiene fila de `branch_inventories` con existencia actual, reservada o en tránsito distinta de cero, **y (b)** no tiene ninguna fila de `kardex_movements`, en ninguna sucursal, nunca), así que la política dejó de fallar cerrada por ausencia de implementación. Sobre esa base, este pago ejecutó los tres pasos que quedaban del plan original:

1. **`PATCH /api/catalog/products/{externalId}/base-unit`** se publicó en `ProductController`, con la misma autorización `ADMIN` que el resto de las mutaciones de catálogo (`SecurityConfig`'s `/api/catalog/**` matcher).
2. `CatalogExceptionHandler` mapea `BaseUnitChangeRejectedException` a **dos** códigos distintos según su `Reason`: `base_unit_has_history` (`409`, rechazo de negocio — RN-13) y `base_unit_precondition_unverifiable` (`503`, el puerto no pudo responder — carencia de infraestructura). Unificarlos habría hecho que una falla de infraestructura pareciera un rechazo de negocio, tanto para quien llama como para quien lee los registros.
3. Se verificó que `ProductAdminService.changeBaseUnit` ya ejecutaba la comprobación de la precondición y la escritura de `base_unit` bajo un único `@Transactional`: no hizo falta ningún cambio para cerrar esta condición, sólo confirmarla con una prueba de integración de punta a punta contra el endpoint real.

Sin cambios de esquema: `base_unit` ya era columna de `products` desde el modelo original.

#### Verificación
`ProductCatalogIT` prueba el endpoint contra HTTP real: éxito para un producto sin stock ni historial, y `409 base_unit_has_history` para uno con saldo en `branch_inventories`, sin tocar `base_unit`. `CatalogApiContractIT` se actualizó de "dieciséis endpoints y ninguna ruta de unidad base" a "diecisiete endpoints, exactamente uno de ellos el `PATCH .../base-unit`", y sigue probando que ningún `id` numérico escapa por esa ruta.

#### Referencias
RN-13 · RF-INV-01 · RF-INV-02 · `openspec/changes/archive/2026-08-28-add-catalog-module/contract.md` §2.2 y decisión PA-08.

---

#### DT-08 — Búsqueda de productos por texto libre resuelta con recorrido secuencial

**Severidad:** Baja · **Estado:** Aceptada · **Esfuerzo estimado:** pequeño · **Origen:** diseño del módulo `catalog`

#### Situación actual
El listado de productos admite un término libre que se compara **por contenido** contra el SKU y el nombre, sin distinguir mayúsculas. En SQL eso es un `LIKE '%término%'`, y un comodín a la izquierda inutiliza cualquier índice B-Tree: la consulta se resuelve con un recorrido secuencial de `products`.

#### Precisión sobre `idx_products_sku`
Este índice **no** atiende la búsqueda. Sirve para lo que sí es una búsqueda por igualdad: el control de unicidad del SKU al crear y editar un producto, que es lo que produce el conflicto `duplicate_sku`. Documentarlo al revés llevaría a alguien a concluir que la búsqueda ya está indexada y a no medirla nunca.

#### Por qué se aceptó
A la volumetría comprometida —10 000 productos— un recorrido secuencial sobre una tabla angosta cumple RNF-PER-01 con holgura, y el listado está paginado con tope duro, así que ninguna respuesta es de volumen no acotado (RNF-PER-04). Agregar la extensión `pg_trgm` y un índice GIN hoy sería un quinto cambio de esquema para una carga que nadie midió: se estaría pagando complejidad de esquema y de despliegue contra un problema que no existe.

#### Por qué es deuda
El coste de esta decisión crece con los datos y no avisa. Un recorrido secuencial degrada de forma lineal, así que el día que el catálogo crezca la búsqueda se vuelve lenta sin que nada falle: no hay error, sólo una latencia que sube.

#### Disparador
Cualquiera de los dos, lo que ocurra primero:

* el catálogo supera aproximadamente **50 000 productos**, o
* la prueba de integración de latencia de la búsqueda deja de cumplir su umbral.

#### Plan de pago

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX idx_products_sku_trgm  ON products USING gin (sku gin_trgm_ops);
CREATE INDEX idx_products_name_trgm ON products USING gin (name gin_trgm_ops);
```

Dos detalles que hacen que esto sirva, y sin los cuales no sirve:

1. **La consulta debe seguir siendo insensible a mayúsculas de la misma forma que el índice.** Si el índice se crea sobre `sku` pero la consulta compara `LOWER(sku)`, el planificador no puede usarlo. O ambos lados aplican `LOWER(...)`, o se usa el operador `ILIKE`, que `pg_trgm` sí resuelve con el índice GIN.
2. **`pg_trgm` necesita al menos tres caracteres para formar un trigrama.** Una búsqueda de uno o dos caracteres vuelve al recorrido secuencial aunque el índice exista. Si eso importa, el listado debe exigir un término mínimo de tres caracteres en vez de fingir que el índice lo cubre.

Antes de aplicarlo hay que medir, no suponer: `EXPLAIN (ANALYZE, BUFFERS)` sobre la consulta real, con el volumen real, antes y después.

#### Referencias
RNF-PER-01 · RNF-PER-04 · RNF-INT-03 · `openspec/changes/add-catalog-module/contract.md` §9.

---

#### DT-09 — Deduplicación de alertas operativas sin restricción de unicidad en el esquema

**Severidad:** Media · **Estado:** Aceptada · **Esfuerzo estimado:** pequeño · **Origen:** diseño del módulo `inventory`/`notifications`

#### Situación actual
`system_alerts` no tiene ninguna restricción de unicidad sobre `(branch_id, alert_type)`, ni columna `product_id` que permitiera declarar una (F-1, `01-init-schema.sql:415-433`). La regla de negocio HU-ALE-01 exige que una condición persistente (por ejemplo, stock bajo mínimo en repetidos movimientos) no duplique la alerta no resuelta. `OperationalAlertListener` resuelve esto sin tocar el esquema: antes de comprobar si ya existe una alerta no resuelta con la misma clave de deduplicación (`branch_id, alert_type, title`, donde `title` codifica el `external_id` del producto — F-1), toma un bloqueo consultivo de transacción de PostgreSQL (`pg_advisory_xact_lock(hashtext(branch:tipo:sujeto))`) como primera sentencia de su propia transacción `REQUIRES_NEW`. Eso serializa únicamente a los productores concurrentes del mismo sujeto y se libera al hacer `commit`.

#### Por qué se aceptó
El bloqueo consultivo es correcto bajo concurrencia sin requerir una migración de esquema tres días antes de la entrega (PA-04, `contract.md` §11) — la única alternativa correcta era un índice único parcial, que sí exige tocar `01-init-schema.sql`, algo que este cambio se niega deliberadamente a hacer (§2.5). El único productor de alertas hoy (`inventory`, vía `STOCK_MINIMUM`) pasa siempre por este listener, así que la garantía se sostiene en la práctica.

#### Por qué es deuda
La corrección depende enteramente de que **todo** productor futuro de `OperationalAlertRaised` (por ejemplo, `transfers` con `TRANSFER_DISCREPANCY` o `logistics` con `LOGISTIC_DELAY`, ya contempladas por el transporte compartido de P-09) pase por este mismo listener y respete el orden bloqueo-antes-que-lectura. Un productor que inserte directamente en `system_alerts`, o que reordene esas dos operaciones, duplicaría una alerta no resuelta sin que el esquema lo impida — no hay una restricción `CHECK` ni un índice que actúe como última línea de defensa, a diferencia de `current_stock >= 0` (T-07).

#### Plan de pago
Cuando llegue el próximo cambio de esquema:

1. Agregar `system_alerts.product_id BIGINT REFERENCES products(id)`.
2. `CREATE UNIQUE INDEX uq_alerts_open_dedup ON system_alerts(branch_id, alert_type, product_id) WHERE NOT is_resolved;`
3. Retirar `pg_advisory_xact_lock` de `OperationalAlertListener` — el índice único vuelve el `INSERT` concurrente seguro por sí mismo (conflicto de restricción en vez de bloqueo consultivo).
4. Mover el token de deduplicación de `title` (F-1) a la nueva columna `product_id`; `title` vuelve a ser un texto puramente legible para humanos.

#### Referencias
RF-VAL-01 · HU-ALE-01 · HU-ALE-02 · RN-07 · `openspec/changes/add-inventory-module/design.md` §6.3, §9, D-2.

---

#### DT-10 — El tope de tamaño de página se resuelve distinto en `catalog` que en el resto de los módulos

**Severidad:** Baja · **Estado:** Aceptada · **Esfuerzo estimado:** pequeño · **Origen:** verificación del módulo `inventory`

#### Situación actual
Los dos módulos acotan el tamaño de página al mismo tope de 100, pero reaccionan distinto cuando el cliente pide más. `catalog` **clampea en silencio**: `Math.min(Math.max(size, 1), MAX_PAGE_SIZE)` devuelve 100 sin avisar. `inventory` y `notifications` **rechazan** con `400 invalid_request`. Es el mismo parámetro de consulta con dos contratos distintos según el endpoint que se toque.

#### Por qué se aceptó
El descubrimiento llegó con `inventory` ya construido y `catalog` ya entregado, incluido su frontend, que fue escrito contra el comportamiento de clampeo. Unificar exige elegir uno de los dos y cambiar el otro; cualquiera de las dos direcciones altera un contrato ya publicado y consumido. A pocos días de la entrega, el riesgo de romper una pantalla que hoy funciona supera la ganancia de uniformar un comportamiento de borde que ningún requerimiento distingue: **RNF-PER-04** exige que ninguna respuesta sea de volumen no acotado, y las dos variantes lo cumplen.

Se elige además una dirección para lo que viene: los módulos que falten construir siguen el patrón de rechazo explícito, de modo que la excepción quede aislada en un solo módulo en lugar de repartirse.

#### Por qué es deuda
El clampeo silencioso es una coerción que el cliente no puede detectar. Quien pida 200 elementos recibe 100 y una respuesta sin ninguna marca de que su petición fue alterada, así que puede concluir que vio el conjunto completo cuando en realidad vio la mitad. El rechazo explícito no tiene ese modo de fallo: obliga a corregir la petición.

La inconsistencia además se paga sola con el tiempo. Cada módulo nuevo obliga a decidir de nuevo cuál de los dos patrones seguir, y cada cliente que consuma dos módulos distintos tiene que aprender que el mismo parámetro se comporta de dos maneras.

#### Plan de pago
1. Unificar en el rechazo explícito: reemplazar el clampeo de `catalog` por la validación que ya usan `inventory` y `notifications`.
2. Ajustar en el mismo cambio el frontend de `catalog`, que hoy depende de que un tamaño excesivo se corrija solo.
3. Actualizar la prueba `listingRespectsActiveFilterSizeClampAndSortAllowList`, que fija el comportamiento actual de clampeo.
4. Documentar el tope y su código de error en el contrato de API, para que la regla sea descubrible sin leer el código.

#### Referencias
RNF-PER-04 · `openspec/changes/archive/2026-08-29-add-inventory-module/verify-report.md` — advertencia 2.

---

#### DT-11 — `transfer_number` se asigna sin una secuencia de base de datos

**Severidad:** Baja · **Estado:** Aceptada · **Esfuerzo estimado:** trivial · **Origen:** diseño del módulo `transfers`

#### Situación actual
`transfers` no tiene ninguna columna de secuencia ni un `SEQUENCE` de PostgreSQL que numere `transfer_number` (`01-init-schema.sql`, §2.5 de `openspec/changes/add-transfers-module/contract.md`). RF-TRA-01 y HU-TRA-01 exigen un número legible con el formato `TRF-<yyyy>-<nnnn>`, ya usado por la fila semilla `TRF-2026-0001`. `TransferPersistenceAdapter.create` lo resuelve sin tocar el esquema: toma un bloqueo consultivo de transacción de PostgreSQL con alcance anual (`pg_advisory_xact_lock(hashtext('transfer_number:' || :year))`) como primera sentencia, calcula `MAX(...) + 1` sobre los números ya asignados ese año y recién entonces inserta — la misma técnica que **DT-09** ya usa para deduplicar alertas.

#### Por qué se aceptó
El bloqueo consultivo serializa correctamente las creaciones concurrentes dentro de un mismo año sin requerir una migración de esquema (§2.5 prohíbe deliberadamente tocar `01-init-schema.sql` en este cambio). La restricción `UNIQUE` existente sobre `transfer_number` queda como última línea de defensa (T-07): si alguna vez el bloqueo se omitiera, el `INSERT` duplicado fallaría en la base en vez de corromper silenciosamente el número.

#### Por qué es deuda
La corrección depende enteramente de que **todo** escritor futuro de `transfers` tome el mismo bloqueo consultivo antes de calcular el siguiente número. Un segundo camino de escritura — una migración de datos, un script administrativo, un módulo futuro que inserte directamente en `transfers` — que no respete ese orden puede colisionar con una creación concurrente y, en el peor caso, verse rechazado por la restricción `UNIQUE` en vez de recibir un número válido. No hay una secuencia de base de datos que lo garantice estructuralmente, a diferencia de una columna `SERIAL` o `GENERATED ALWAYS AS IDENTITY`.

#### Plan de pago
Cuando llegue el próximo cambio de esquema:

1. `CREATE SEQUENCE transfer_number_seq;`.
2. Retirar `pg_advisory_xact_lock` y la consulta `MAX(...)` de `TransferPersistenceAdapter.create`.
3. Formatear `transfer_number` a partir de `nextval('transfer_number_seq')` combinado con el año en curso, conservando el formato `TRF-<yyyy>-<nnnn>` que el frontend y los datos semilla ya asumen.

#### Referencias
RF-TRA-01 · HU-TRA-01 · `openspec/changes/add-transfers-module/design.md` §6.2, §9, D-3.

---

#### DT-12 — `sales.invoice_number` se asigna sin una secuencia de base de datos

**Severidad:** Baja · **Estado:** Aceptada · **Esfuerzo estimado:** trivial · **Origen:** diseño del módulo `sales`

#### Situación actual
`sales` no tiene ninguna columna de secuencia ni un `SEQUENCE` de PostgreSQL que numere `invoice_number` (`01-init-schema.sql`, §2.5 de `openspec/changes/archive/2026-08-30-add-sales-module/contract.md`). RF-VEN-01 y RF-VEN-02 exigen un número correlativo único legible con el formato `VEN-<yyyy>-<nnnn>`. `SalePersistenceAdapter.create` lo resuelve sin tocar el esquema —únicamente cuando la orden no suministra un número del punto de venta externo—: toma un bloqueo consultivo de transacción de PostgreSQL con alcance anual (`pg_advisory_xact_lock(hashtext('sale_invoice_number:' || :year))`) como primera sentencia, calcula `MAX(...) + 1` sobre los números internos ya asignados ese año y recién entonces inserta —la misma técnica que **DT-11** utiliza para numerar transferencias y **DT-09** para deduplicar alertas—.

#### Por qué se aceptó
El bloqueo consultivo serializa correctamente las creaciones concurrentes dentro de un mismo año sin requerir una migración de esquema (§2.5 prohíbe deliberadamente tocar `01-init-schema.sql` en este cambio). La restricción `UNIQUE` existente sobre `sales.invoice_number` queda como última línea de defensa (T-07): si alguna vez el bloqueo se omitiera, el `INSERT` duplicado fallaría en la base en vez de corromper silenciosamente la numeración. Además, el adaptador externo de punto de venta (CU-EXT-02) rechaza números con el prefijo reservado `VEN-\d{4}-\d+` para no alterar el cálculo del correlativo interno.

#### Por qué es deuda
La corrección depende enteramente de que **todo** escritor futuro de `sales` tome el mismo bloqueo consultivo antes de calcular el siguiente número correlativo. Un segundo camino de escritura —una migración de datos, un script administrativo o un proceso por lotes que inserte directamente en `sales`— que no respete ese orden puede colisionar con una creación concurrente y verse rechazado por la restricción `UNIQUE` en vez de recibir un número válido. No hay una secuencia de base de datos que garantice la asignación correlativa de forma estructural.

#### Plan de pago
Cuando llegue el próximo cambio de esquema:

1. `CREATE SEQUENCE sale_invoice_number_seq;`.
2. Retirar `pg_advisory_xact_lock` y la consulta `MAX(...)` de `SalePersistenceAdapter.create`.
3. Retirar el guardián de prefijo reservado en `InvoiceNumber`.
4. Formatear `invoice_number` a partir de `nextval('sale_invoice_number_seq')` combinado con el año en curso, conservando el formato `VEN-<yyyy>-<nnnn>` que el frontend y los clientes externos ya asumen.

#### Referencias
RF-VEN-01 · RF-VEN-02 · RF-EXT-02 · `openspec/changes/archive/2026-08-30-add-sales-module/design.md` §6.3, §10, D-5.

---

#### DT-13 — `purchase_orders.order_number` se asigna sin una secuencia de base de datos

**Severidad:** Baja · **Estado:** Aceptada · **Esfuerzo estimado:** trivial · **Origen:** contrato del módulo `purchases`

#### Situación actual
`purchase_orders` no tiene ninguna columna de secuencia ni un `SEQUENCE` de PostgreSQL que numere `order_number`: la tabla sólo declara `order_number VARCHAR(50) NOT NULL UNIQUE` (`01-init-schema.sql`, §2.5 de `openspec/changes/add-purchases-module/contract.md`, hallazgo F-9). RF-COM-01 y HU-COM-01 exigen un número correlativo único legible, que se fija con el formato `OC-<yyyy>-<nnnn>` para acompañar a los ya usados `TRF-<yyyy>-<nnnn>` y `VEN-<yyyy>-<nnnn>`. El adaptador de persistencia de `purchases` lo resolverá sin tocar el esquema: tomará un bloqueo consultivo de transacción de PostgreSQL con alcance anual (`pg_advisory_xact_lock(hashtext('purchase_order_number:' || :year))`) como primera sentencia, calculará `MAX(...) + 1` sobre los números ya asignados ese año y recién entonces insertará —la misma técnica que **DT-11** utiliza para numerar transferencias, **DT-12** para las facturas de venta y **DT-09** para deduplicar alertas—.

#### Por qué se aceptó
El bloqueo consultivo serializa correctamente las creaciones concurrentes dentro de un mismo año sin requerir una migración de esquema (§2.5 del contrato prohíbe deliberadamente tocar `01-init-schema.sql` en este cambio). La restricción `UNIQUE` existente sobre `order_number` queda como última línea de defensa: si alguna vez el bloqueo se omitiera, el `INSERT` duplicado fallaría en la base en vez de corromper silenciosamente la numeración.

#### Por qué es deuda
La corrección depende enteramente de que **todo** escritor futuro de `purchase_orders` tome el mismo bloqueo consultivo antes de calcular el siguiente número correlativo. Un segundo camino de escritura —una migración de datos, un script administrativo o una carga masiva de órdenes históricas que inserte directamente en `purchase_orders`— que no respete ese orden puede colisionar con una creación concurrente y verse rechazado por la restricción `UNIQUE` en vez de recibir un número válido. No hay una secuencia de base de datos que garantice la asignación correlativa de forma estructural.

#### Plan de pago
Cuando llegue el próximo cambio de esquema:

1. `CREATE SEQUENCE purchase_order_number_seq;`.
2. Retirar `pg_advisory_xact_lock` y la consulta `MAX(...)` del adaptador de persistencia de `purchases`.
3. Formatear `order_number` a partir de `nextval('purchase_order_number_seq')` combinado con el año en curso, conservando el formato `OC-<yyyy>-<nnnn>` que el frontend y el histórico de compras ya asumen.

#### Referencias
RF-COM-01 · RF-COM-05 · HU-COM-01 · `openspec/changes/add-purchases-module/contract.md` §2.5 F-9, §4 R-05, PA-04.

---

#### DT-14 — Rollup corporativo mensual sin índice cubriente sobre `sale_items`

**Severidad:** Media · **Estado:** Aceptada · **Esfuerzo estimado:** medio · **Origen:** contrato del módulo `analytics`

#### Situación actual
La consulta del tablero corporativo mensual (`CU-DSH-03`, `RF-DSH-05`) agrega las ventas consolidadas por sucursal realizando un `JOIN` con `sale_items` para calcular la suma de unidades vendidas (`units_sold`) y el valor de venta (`sales_amount`). En el esquema actual (`01-init-schema.sql`), `sales` cuenta con el índice compuesto `idx_sales_branch_date ON sales(branch_id, created_at)` y `sale_items` con `idx_sale_items_sale ON sale_items(sale_id)`, pero ninguno incluye las columnas agregadas (`total_amount` en `sales`; `product_id`, `quantity`, `subtotal` en `sale_items`) en el árbol del índice (`COVERING / INCLUDE`). Consecuentemente, el plan de ejecución de PostgreSQL debe acceder al *heap* de `sale_items` para cada fila coincidente durante el agregado mensual.

#### Por qué se aceptó
La mitigación se implementa a nivel de diseño y arquitectura sin modificar el esquema en este ciclo (§2.5 del contrato prohíbe deliberadamente tocar `backend/init-db/`):
1. El tablero corporativo es de uso exclusivo del rol `ADMIN` (`RNF-SEC-01`), con una concurrencia estimada baja (un puñado de usuarios corporativos dentro de la carga pico de 50 usuarios concurrentes, §5.1 de requerimientos).
2. La consulta agrega un solo mes calendario (`from` a `to`), limitando el volumen de filas evaluadas.
3. El adaptador de persistencia une `sale_items` **una sola vez** por respuesta en un CTE general (`month_sales`), en lugar de ejecutar una consulta por cada sucursal o por cada página.

#### Por qué es deuda
A medida que el volumen histórico de ventas crezca (con estimaciones de hasta cientos de miles de registros de venta por año en la red), el costo de I/O sobre el *heap* de `sale_items` degradará el tiempo de respuesta del tablero corporativo frente a un recorrido de índice puro (*index-only scan*). Sin índices cubrientes, la base de datos lee páginas de datos adicionales que incrementan el consumo de memoria en el *buffer cache*.

#### Plan de pago
Cuando llegue el próximo cambio de esquema (la migración de Flyway de **DT-01** es el vehículo natural):

1. `CREATE INDEX idx_sales_branch_date_covering ON sales(branch_id, created_at) INCLUDE (total_amount);`
2. `CREATE INDEX idx_sale_items_sale_covering ON sale_items(sale_id) INCLUDE (product_id, quantity, subtotal);`
3. Convertir las consultas A-4, A-5 y A-6 en recorridos exclusivos de índice (*index-only scans*). Si el volumen superase proyecciones de latencia (RNF-PER-01 / RNF-PER-03), evaluar una tabla de agregados periódicos nocturnos (*nightly rollup table*).

#### Referencias
RF-DSH-05 · RNF-PER-01 · RNF-PER-03 · `openspec/changes/add-analytics-module/contract.md` §9.2, §10 · `openspec/changes/add-analytics-module/design.md` §4 Q-7, §10.

---

#### DT-15 — El frontend no está contenedorizado ni entra en `compose.yml`

**Severidad:** Media · **Estado:** Resuelta (2026-08-31) · **Esfuerzo estimado:** pequeño · **Origen:** revisión de entrega

#### Situación previa
`compose.yml` definía dos servicios, `db` y `backend`. La SPA no tenía `Dockerfile` y no aparecía en el Compose: se levantaba nativa con Vite, a través del objetivo `up` del `Makefile`, que contenedorizaba la base y el backend y dejaba el frontend corriendo en primer plano con recarga en caliente.

En consecuencia, `docker compose up` **no levantaba la solución completa**: levantaba su mitad de servidor. Quien clonara el repositorio y ejecutara sólo ese comando obtenía una API funcionando y ninguna interfaz.

#### Por qué se había aceptado diferirla
Durante el desarrollo la decisión era correcta y deliberada. Servir la SPA desde un contenedor obliga a reconstruir la imagen en cada cambio de código y destruye la recarga en caliente, que es la herramienta que hace productivo el trabajo de interfaz. El `Makefile` resolvía el caso de uso real —levantar todo con un comando— sin pagar ese costo.

Lo que no se había hecho a tiempo era la otra mitad: la imagen de producción, un problema distinto y más simple, porque una SPA compilada es un directorio de archivos estáticos servido por cualquier servidor web.

#### Por qué era deuda
El enunciado de la prueba pide explícitamente que la solución se levante con Docker Compose, y hasta este pago ese comando entregaba un sistema incompleto. Además el frontend nunca se había ejecutado contra una construcción de producción servida estáticamente: Vite se comporta distinto en desarrollo —resolución de módulos, variables de entorno, ruteo del lado del cliente ante un refresco de página— y los defectos propios de ese modo sólo aparecen al probar la imagen real.

#### Cómo se pagó
1. **`frontend/Dockerfile`** agrega una construcción multietapa: una etapa de Node + pnpm compila la SPA (`pnpm build`, es decir `tsc -b && vite build`) y se descarta; la imagen final sólo contiene Nginx (`nginxinc/nginx-unprivileged`, corre como UID 101 sin root, igual que el patrón del `Dockerfile` del backend) sirviendo `/app/dist`.
2. **`frontend/nginx/default.conf.template`** resuelve el enrutamiento del lado del cliente con `try_files $uri $uri/ /index.html` en el `location /`. Esto es lo que la propia ficha anticipaba en su plan original: sin esa directiva, refrescar la página sobre una ruta del cliente responde `404`, porque ese archivo no existe en disco. La verificación lo confirmó devolviendo `200` sobre `/inventory`.
3. El proxy `/api/` combina un `resolver ${DNS_RESOLVER}` (el DNS embebido de Docker, `127.0.0.11`) con `proxy_pass` por variable (`$upstream`). Así Nginx resuelve el nombre del servicio `backend` **en cada petición** y no al arrancar: si resolviera una sola vez al inicio, un backend todavía no listo impediría que Nginx levantara — un fallo de arranque intermitente que aparece en la máquina de otro y no en la propia.
4. La caché queda diferenciada: `/assets/` (bundles con hash en el nombre) responde `Cache-Control: public, max-age=31536000, immutable`; `/` (que sirve `index.html`) responde `no-cache`. Al revés, un despliegue nuevo no se vería hasta que expirara la caché del navegador.
5. Se agregó el servicio `frontend` a `compose.yml`, publicado en `${FRONTEND_PORT:-8081}` (el backend ya ocupa el 8080 del anfitrión), con `depends_on: backend` y `healthcheck` (`wget --spider` sobre `http://127.0.0.1:8080/healthz`, con `127.0.0.1` explícito porque Nginx sólo escucha IPv4 y el `wget` de BusyBox no reintenta si `localhost` resuelve primero a `::1`).
6. Se conservó el objetivo `up` del `Makefile` para desarrollo (`db` y `backend` en contenedores, frontend nativo con HMR) y se agregó **`up-full`**: stack completo en contenedores (`docker compose up -d --build`). Son dos modos con propósitos distintos, no uno que reemplaza al otro.

#### Verificación
Se levantó el sistema con `docker compose up -d --build` desde cero. Los tres servicios quedaron arriba (`backend` healthy, `db`, `frontend` healthy). `GET http://localhost:8081/` respondió `200`; `GET http://localhost:8081/inventory` (ruta profunda del cliente) respondió `200` devolviendo `index.html`; `POST http://localhost:8081/api/auth/login` con un usuario semilla respondió `200` con un JWT válido. Esta última prueba ejercita la cadena completa navegador → Nginx → proxy `/api` → backend → PostgreSQL, dentro de Compose, con un solo comando.

#### Referencias
Sección 5 del enunciado (`prueba_tecnica_inventario.md`, en la raíz del repositorio) · `compose.yml` · `Makefile` · `frontend/Dockerfile` · `frontend/nginx/default.conf.template`

---

### 7.4. Lo que NO es Deuda Técnica

Estas decisiones son **alcance excluido**, no deuda. Se listan aquí porque suelen confundirse:

facturación fiscal y timbrado electrónico · contabilidad general · nómina y recursos humanos · comercio electrónico · multimoneda · multiempresa (*multi-tenant*) · trazabilidad por lote y fecha de caducidad · aplicación móvil nativa.

El detalle y su justificación están en la sección 1.3 de [`especificacion_requerimientos.md`](./especificacion_requerimientos.md).

---

### 7.5. Mantenimiento de este Registro

1. **Toda decisión de postergar trabajo se registra aquí en el momento en que se toma**, no al final. Una deuda documentada tarde ya causó su daño.
2. Cada ítem debe indicar **qué la dispara**: una deuda sin condición de pago es una excusa con formato de tabla.
3. Al pagar una deuda se marca **Resuelta** con la fecha y el cambio que la saldó; no se borra. El histórico explica por qué el sistema es como es.
4. Antes de cada entrega se revisa el registro completo y se reevalúa la severidad: una deuda baja puede volverse alta cuando cambia el contexto.
