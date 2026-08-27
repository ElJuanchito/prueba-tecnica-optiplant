# Documento de Decisiones de Arquitectura Técnica (ADR)
## Sistema de Gestión de Inventario Multi-Sucursal — OptiPlant Consultores
### Cumplimiento: Sección 8.1 — Separación de Responsabilidades · Sección 8.2 — Decisiones Técnicas a Documentar

| Versión | Fecha | Cambios |
| :--- | :--- | :--- |
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
|     19 tablas · Kardex append-only · bloqueo pesimista · CHECK como red de fondo   |
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
| `sales` | Ventas, comprobantes y anulaciones | CU-VEN-01, CU-VEN-03, CU-VEN-04 |
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

### 3.2. Framework de Frontend: React 19 + Vite + TypeScript

#### Decisión
**React 19** con **Vite** como herramienta de construcción y **TypeScript** en modo estricto.

#### Justificación Técnica
1. **TypeScript en modo estricto extiende el contrato de la API hasta el cliente.** Los tipos generados desde la especificación OpenAPI (sección 3.8) hacen que un cambio incompatible en el backend rompa la compilación del frontend, no la producción.
2. **El dominio es de formularios y tablas densas, no de animación.** React tiene el ecosistema más maduro para tablas virtualizadas, formularios con validación y gráficas — que es literalmente todo lo que este sistema necesita.
3. **Vite entrega arranque en frío casi instantáneo**, lo que importa en un entorno contenerizado donde el ciclo de desarrollo pasa por reconstruir la imagen.
4. **Es una decisión reversible.** El frontend consume exclusivamente la API REST; reemplazarlo por Vue o Angular no toca una sola línea del backend. Esa reversibilidad es consecuencia directa de la restricción técnica 2, y es la razón por la que esta decisión es la de menor riesgo del documento.

### 3.3. Motor de Base de Datos y Modelo de Datos: PostgreSQL 17

#### Decisión
**PostgreSQL 17** como motor relacional único, con **modelo normalizado (3FN)**, patrón **PK numérica (`BIGINT IDENTITY`) + token público (`external_id UUID`)** y tablas *append-only* para Kardex y auditoría. **19 tablas.**

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

## 7. Asuntos Abiertos

Las decisiones de postergar trabajo se registran de forma centralizada en [`deuda_tecnica.md`](./deuda_tecnica.md), con su justificación, su plan de pago y la condición que lo dispara.

| ID en el registro | Asunto derivado de este documento | Estado |
| :--- | :--- | :--- |
| **DT-01** | Convertir `init-db/01-init-schema.sql` en la migración `V1__initial_schema.sql` de Flyway y retirar el arranque por `init-db/` para no sostener dos fuentes de verdad (sección 3.7). | Aceptada · se paga al montar el backend |
| **DT-02** | Separar los datos de demostración de las migraciones versionadas mediante una *location* activada por el perfil `dev` (sección 3.7). | Aceptada · se paga junto con DT-01 |
| **DT-03** | Restringir el solapamiento de rangos históricos de precio con `EXCLUDE USING gist` (sección 3.3). | Aceptada · se paga antes de habilitar precios retroactivos |
| **DT-05** | La coherencia entre el precio congelado en la venta y la lista vigente se garantiza en el dominio, no en el esquema (sección 3.6). | Aceptada · se mitiga con pruebas |
