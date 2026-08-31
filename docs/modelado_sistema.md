# Modelado del Sistema
## Sistema de Gestión de Inventario Multi-Sucursal — OptiPlant Consultores
### Cumplimiento: Sección 7.1 — Diagramas Obligatorios

---

## 1. Inventario de Diagramas

El enunciado exige cuatro diagramas de ingeniería. Cada uno se entrega en **Excalidraw** (editable y presentable) y, cuando la notación lo justifica, también en **PlantUML** (UML estricto) y **Mermaid** (renderizado directo en GitHub).

| # | Diagrama obligatorio | Estado | Archivos |
| :--- | :--- | :--- | :--- |
| 1 | **Casos de uso** | ✅ | 9 diagramas — ver [`casos_de_uso.md`](./casos_de_uso.md), sección 4.3 |
| 2 | **Actividades / flujo** | ✅ | 3 diagramas — venta, transferencia y recepción de compra (sección 2) |
| 3 | **Arquitectura** | ✅ | 3 vistas — capas, módulos y puertos/adaptadores (sección 3) |
| 4 | **Entidad-Relación** | ✅ | Mermaid, PlantUML y Excalidraw — ver [`diagrama_er.md`](./diagrama_er.md) |

**Criterio de descomposición:** ningún diagrama intenta contarlo todo. Un lienzo saturado obliga al lector a hacer el trabajo que el diagrama debía hacer por él. Cada vista responde **una** pregunta, y este documento indica cuál.

---

## 2. Diagramas de Actividad

El enunciado exige como mínimo el flujo de transferencia y el flujo de venta. Se agrega un tercero —la recepción de compra— porque es la única operación que altera la **valorización** del inventario, y sin ella el modelo de costos queda sin representar.

Los tres usan **calles verticales por actor**: un diagrama de actividad que no muestra *quién* ejecuta cada paso deja de responder la mitad de la pregunta.

| Archivo | Flujo | Caso de uso | Responde |
| :--- | :--- | :--- | :--- |
| [`diagrams/actividad_01_venta.excalidraw`](./diagrams/actividad_01_venta.excalidraw) | Venta con validación de stock | CU-VEN-01 | ¿Cómo se impide la sobreventa bajo concurrencia? |
| [`diagrams/actividad_02_transferencia.excalidraw`](./diagrams/actividad_02_transferencia.excalidraw) | Transferencia entre sucursales | CU-TRA-01 … CU-TRA-06 | ¿Cómo se mueve stock entre dos sucursales sin perderlo ni duplicarlo? |
| [`diagrams/actividad_03_recepcion_compra.excalidraw`](./diagrams/actividad_03_recepcion_compra.excalidraw) | Recepción de compra y CPP | CU-COM-04 | ¿Cuándo y cómo se recalcula el costo del inventario? |

### 2.1. Flujo de Venta

```mermaid
flowchart TD
    START([Inicio del registro de venta]) --> A1[Operador: agregar productos,<br/>cantidad y unidad de medida]
    A1 --> S1[Sistema: resolver precio vigente<br/>de la lista de la sucursal · RN-16]
    S1 --> A2[Operador: aplicar descuento por ítem]
    A2 --> D1{¿Descuento dentro del<br/>tope de la lista? · RN-17}
    D1 -- No --> AUT[Solicitar autorización<br/>del Gerente de Sucursal]
    AUT --> A3[Operador: confirmar venta]
    D1 -- Sí --> A3
    A3 --> S2[Sistema: abrir transacción atómica · RNF-INT-01]
    S2 --> B1[(BD: bloquear inventario<br/>SELECT ... FOR UPDATE)]
    B1 --> D2{¿Stock disponible<br/>suficiente? · RN-01}
    D2 -- No --> ERR[ROLLBACK: informar cantidad<br/>solicitada y disponible]
    ERR --> FINE([Venta no registrada · stock intacto])
    D2 -- Sí --> B2[(BD: descontar stock e<br/>insertar sale + sale_items)]
    B2 --> B3[(BD: insertar movimiento<br/>SALE en el Kardex · RN-02)]
    B3 --> S3[Sistema: COMMIT]
    S3 --> D3{¿Stock bajo el<br/>umbral mínimo?}
    D3 -- Sí --> AL[Generar alerta STOCK_MINIMUM]
    AL --> END([Comprobante emitido])
    D3 -- No --> END

    style ERR fill:#ffe3e3,stroke:#c92a2a
    style FINE fill:#f1f3f5,stroke:#495057
    style AL fill:#ffdeeb,stroke:#a61e4d
    style END fill:#d3f9d8,stroke:#2f9e44
    style START fill:#d3f9d8,stroke:#2f9e44
```

**Lo que este flujo garantiza.** La validación de stock ocurre **después** del bloqueo pesimista, no antes. Ese orden es la diferencia entre prevenir la sobreventa y solo esperar que no ocurra: si dos operadores confirman a la vez la última unidad, el segundo espera el bloqueo, revalida y es rechazado.

### 2.2. Flujo de Transferencia entre Sucursales

```mermaid
flowchart TD
    START([Detección de faltante local]) --> A1[Destino: consultar stock<br/>disponible en la red · CU-INV-04]
    A1 --> A2[Destino: crear solicitud con<br/>producto, cantidad y urgencia]
    A2 --> S1[Sistema: registrar transferencia<br/>en estado REQUESTED]
    S1 --> O1[Origen: evaluar disponibilidad]
    O1 --> D1{¿Aprueba la solicitud?}
    D1 -- No --> CANC[Destino: recibir notificación de rechazo]
    CANC --> FIN1([Transferencia CANCELLED · stock intacto])
    D1 -- Sí --> O2[Origen: aprobar cantidad total<br/>o parcial · IN_PREPARATION]
    O2 --> O3[Origen: registrar despacho con<br/>transportista y fecha estimada]
    O3 --> D2{¿El stock sigue disponible<br/>al despachar?}
    D2 -- No --> O2
    D2 -- Sí --> B1[Sistema: descontar origen · incrementar tránsito en destino<br/>Kardex TRANSFER_OUT · IN_TRANSIT]
    B1 --> A3[Destino: recibir la mercancía física y contarla]
    A3 --> D3{¿Lo recibido iguala<br/>a lo despachado?}
    D3 -- Sí --> B2[Sistema: ingresar cantidad completa<br/>Kardex TRANSFER_IN · RECEIVED]
    B2 --> END([Transferencia cerrada · inventarios consistentes])
    D3 -- No --> B3[Destino: registrar faltantes<br/>y discrepancia · RN-06]
    B3 --> AL[Alerta TRANSFER_DISCREPANCY<br/>severidad crítica · RN-07]
    AL --> TRAT[Origen: definir tratamiento —<br/>reenvío, merma o reclamación]
    TRAT --> END

    style CANC fill:#ffe3e3,stroke:#c92a2a
    style FIN1 fill:#f1f3f5,stroke:#495057
    style B3 fill:#ffdeeb,stroke:#a61e4d
    style AL fill:#ffdeeb,stroke:#a61e4d
    style END fill:#d3f9d8,stroke:#2f9e44
    style START fill:#d3f9d8,stroke:#2f9e44
```

**El detalle que suele omitirse.** La aprobación **no reserva** stock. Entre que el gerente aprueba y el operador despacha pueden pasar horas, y en ese lapso la sucursal origen sigue vendiendo. Por eso el despacho revalida disponibilidad y puede devolver la transferencia a `IN_PREPARATION` — esa es la rama roja del diagrama, y es la que distingue un modelo pensado de uno dibujado.

### 2.3. Flujo de Recepción de Compra y Costo Promedio

```mermaid
flowchart TD
    START([Arribo de mercancía del proveedor]) --> A1[Operador: localizar la orden<br/>de compra pendiente]
    A1 --> D0{¿Orden en estado APPROVED<br/>o PARTIALLY_RECEIVED? · RN-15}
    D0 -- No --> ERR[Rechazar: estado inválido]
    ERR --> FIN1([Recepción no registrada])
    D0 -- Sí --> A2[Operador: registrar cantidad<br/>físicamente recibida por ítem]
    A2 --> S1[Sistema: abrir transacción<br/>y bloquear el inventario]
    S1 --> S2[Sistema: recalcular Costo<br/>Promedio Ponderado · RN-10]
    S2 --> B1[(BD: incrementar stock disponible<br/>de la sucursal receptora)]
    B1 --> B2[(BD: insertar movimiento<br/>PURCHASE_RECEIPT en el Kardex)]
    B2 --> D1{¿Se completaron todas<br/>las cantidades ordenadas?}
    D1 -- No --> B4[PARTIALLY_RECEIVED<br/>conserva el saldo pendiente]
    D1 -- Sí --> B3[(RECEIVED · orden cerrada)]
    B4 --> S3[Sistema: COMMIT]
    B3 --> S3
    S3 --> END([Stock y costo actualizados])

    style ERR fill:#ffe3e3,stroke:#c92a2a
    style FIN1 fill:#f1f3f5,stroke:#495057
    style END fill:#d3f9d8,stroke:#2f9e44
    style START fill:#d3f9d8,stroke:#2f9e44
```

**Fórmula aplicada (RN-10):**

```
CPP nuevo = ((stock actual × CPP actual) + (cantidad recibida × costo unitario)) / (stock actual + cantidad recibida)
```

Caso verificable: 100 unidades a $10 más 100 unidades a $20 arroja un CPP de exactamente **$15**. Ese es el criterio de aceptación de la historia HU-INV-03.

---

## 3. Diagramas de Arquitectura

La vista técnica se descompone en tres niveles de acercamiento. Intentar mostrar capas, módulos y puertos en un solo lienzo produce un diagrama que nadie lee.

| Archivo | Nivel | Responde |
| :--- | :--- | :--- |
| [`diagrams/arquitectura_01_capas.excalidraw`](./diagrams/arquitectura_01_capas.excalidraw) | Sistema y despliegue | ¿Qué se despliega y cómo se comunica? |
| [`diagrams/arquitectura_02_modulos.excalidraw`](./diagrams/arquitectura_02_modulos.excalidraw) | Interior del backend | ¿Cómo se organiza el dominio y por qué monolito modular? |
| [`diagrams/arquitectura_03_hexagonal.excalidraw`](./diagrams/arquitectura_03_hexagonal.excalidraw) | Interior de un módulo | ¿Cómo se aísla la lógica de negocio del framework? |

### 3.1. Capas y Despliegue

```mermaid
flowchart TD
    USER["Navegador del usuario<br/>escritorio y tablet · RNF-USA-01"]
    USER -->|"HTTPS · TLS · RNF-SEC-04"| FE

    subgraph DOCKER["Docker Compose · red optiplant-net · docker compose up"]
        direction TB
        FE["Capa de Presentación — servicio frontend<br/>React 19 + Vite + TypeScript · SPA sin lógica de negocio"]
        BE["Capa de Negocio — servicio backend<br/>Java 25 + Spring Boot 4.1 · puerto 8080"]
        DB[("Capa de Datos — servicio db<br/>PostgreSQL 17 · 21 tablas · Kardex append-only")]
        VOL[["Volumen pgdata"]]
        INIT[["init-db/*.sql · esquema y semillas"]]

        FE -->|"API REST / JSON · sólo external_id · RNF-API-02"| BE
        BE -->|"JDBC · HikariCP · transacciones ACID · RNF-INT-01"| DB
        DB --- VOL
        INIT --> DB
    end

    style FE fill:#e7f5ff,stroke:#1971c2
    style BE fill:#f3f0ff,stroke:#6741d9
    style DB fill:#e6fcf5,stroke:#0ca678
    style USER fill:#f1f3f5,stroke:#495057
```

**Las tres capas son tres servicios Docker aislados**, cada uno con su ciclo de vida y su *healthcheck*. El frontend no contiene ni una regla de negocio y jamás toca la base de datos: esa es la restricción técnica número 2 del enunciado, y la separación en servicios la hace estructuralmente imposible de violar.

### 3.2. Módulos del Backend

```mermaid
flowchart TB
    subgraph MONO["Backend — Monolito Modular · fronteras verificadas con ArchUnit · RNF-MAN-02"]
        direction TB
        subgraph FILA1[" "]
            direction LR
            IAM["iam<br/>usuarios, roles y sesiones"]
            CAT["catalog<br/>productos y unidades"]
            PRE["pricing<br/>listas de precios y vigencias"]
            INV["inventory<br/>existencias y Kardex"]
            COM["purchases<br/>órdenes, recepción y CPP"]
        end
        subgraph FILA2[" "]
            direction LR
            VEN["sales<br/>transacciones y comprobantes"]
            TRA["transfers<br/>máquina de estados"]
            LOG["logistics<br/>rutas y cumplimiento"]
            NOT["notifications<br/>alertas y eventos"]
            ANA["analytics<br/>dashboards y KPI · solo lectura"]
        end
        SYNC["Puertos de salida · llamada SÍNCRONA<br/>dentro de la misma transacción<br/>RN-01 · RN-02 · RNF-INT-01"]
        EVT["Eventos de dominio · AFTER_COMMIT<br/>reacciones que no abortan la operación<br/>alertas y proyecciones analíticas"]
        FILA1 --> SYNC
        FILA2 --> SYNC
        FILA2 --> EVT
    end

    style IAM fill:#ffe3e3,stroke:#c92a2a
    style CAT fill:#d0ebff,stroke:#1971c2
    style PRE fill:#fff0f6,stroke:#a61e4d
    style INV fill:#d0ebff,stroke:#1971c2
    style COM fill:#d3f9d8,stroke:#2f9e44
    style VEN fill:#fff3bf,stroke:#e67700
    style TRA fill:#e5dbff,stroke:#6741d9
    style LOG fill:#ffe8cc,stroke:#d9480f
    style NOT fill:#ffdeeb,stroke:#a61e4d
    style ANA fill:#c5f6fa,stroke:#0c8599
    style SYNC fill:#e7f5ff,stroke:#1971c2
    style EVT fill:#f1f3f5,stroke:#495057
```

**Por qué monolito modular y no microservicios.** El inventario exige transacciones ACID que abarcan varios módulos: descontar stock, escribir el Kardex y cerrar la venta ocurren juntos o no ocurren. Con microservicios esa atomicidad exigiría sagas y compensaciones —complejidad enorme para un dominio que cabe cómodamente en una sola base de datos—. El monolito modular entrega las fronteras del microservicio sin pagar su costo operativo: si mañana un módulo debe extraerse, la frontera ya está trazada y verificada por reglas de ArchUnit que corren en cada construcción.

**Y por qué hay dos mecanismos de comunicación y no uno.** La atomicidad **no viaja por eventos**. Si el descuento de stock se delegara a un escucha asíncrono quedaría fuera de la transacción de la venta, y bastaría un fallo de ese escucha para dejar una venta confirmada sin descuento de inventario. Por eso los efectos que deben ser atómicos se invocan por **puerto de salida síncrono** —el desacoplamiento lo da la interfaz, no el evento— y los **eventos de dominio** se reservan, en `AFTER_COMMIT`, para lo que puede fallar sin revertir la venta: una alerta de stock mínimo, una proyección analítica. El detalle está en la sección 3.6 de [`decisiones_arquitectura_tecnica.md`](./decisiones_arquitectura_tecnica.md).

### 3.3. Puertos y Adaptadores dentro de un Módulo

```mermaid
flowchart LR
    AE["Adaptadores de entrada<br/>SaleController (REST)<br/>DTO ↔ comando"]
    PE["Puertos de entrada<br/>RegisterSaleUseCase<br/>(interfaz)"]
    DOM["Núcleo de dominio<br/>Sale · SaleItem · Money<br/>Reglas RN-01, RN-02, RN-03,<br/>RN-16, RN-17<br/><b>Cero imports de framework</b>"]
    PS["Puertos de salida<br/>InventoryPort<br/>SalesRepositoryPort<br/>KardexPort"]
    AS["Adaptadores de salida<br/>JpaSalesRepository<br/>DomainEventPublisher"]

    AE --> PE --> DOM --> PS --> AS

    style AE fill:#e7f5ff,stroke:#1971c2
    style PE fill:#f3f0ff,stroke:#6741d9
    style DOM fill:#fff3bf,stroke:#e67700
    style PS fill:#f3f0ff,stroke:#6741d9
    style AS fill:#e6fcf5,stroke:#0ca678
```

**La dependencia apunta siempre hacia adentro.** Los adaptadores conocen al dominio; el dominio no conoce a nadie. Eso compra tres cosas concretas: el núcleo se prueba sin levantar Spring ni base de datos (por eso el 80% de cobertura de RNF-MAN-01 es alcanzable), cambiar de motor de base de datos toca una sola columna del diagrama, y las reglas de negocio viven en un único lugar en vez de filtrarse a los controladores.

---

## 4. Trazabilidad de los Diagramas

| Diagrama | Requerimientos que representa | Reglas de negocio que hace visibles |
| :--- | :--- | :--- |
| Actividad — Venta | RF-VEN-01, RF-VEN-02, RF-VEN-03, RF-INV-06, RF-INV-08 | RN-01, RN-02, RN-03, RN-16, RN-17 |
| Actividad — Transferencia | RF-TRA-01 … RF-TRA-06, RF-LOG-01, RF-VAL-01 | RN-04, RN-05, RN-06, RN-07 |
| Actividad — Recepción de compra | RF-COM-02, RF-COM-04, RF-COM-05, RF-INV-05 | RN-02, RN-10, RN-15 |
| Arquitectura — Capas | RNF-CON-01, RNF-CON-02, RNF-API-02, RNF-INT-01, RNF-SEC-04 | — |
| Arquitectura — Módulos | RNF-ESC-01, RNF-ESC-02, RNF-MAN-02, RNF-DIS-01 | — |
| Arquitectura — Hexagonal | RNF-ESC-01, RNF-MAN-01, RNF-MAN-02 | RN-01 … RN-17 (residen en el núcleo) |

---

## 5. Cómo Abrir y Editar los Diagramas

* **Excalidraw** (`.excalidraw`): arrastrar el archivo sobre [excalidraw.com](https://excalidraw.com) o usar la extensión de Excalidraw para VS Code.
* **PlantUML** (`.puml`): cualquier renderizador de PlantUML o la extensión correspondiente de VS Code.
* **Mermaid**: se renderiza automáticamente al visualizar estos documentos en GitHub.
