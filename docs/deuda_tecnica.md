# Registro de Deuda Técnica
## Sistema de Gestión de Inventario Multi-Sucursal — OptiPlant Consultores

| Versión | Fecha | Cambios |
| :--- | :--- | :--- |
| 1.0 | 2026-08-26 | Registro inicial con seis ítems identificados durante el diseño. |
| 1.1 | 2026-08-28 | Se agregan dos ítems surgidos del diseño del módulo `catalog`: la exposición HTTP diferida del cambio de unidad base y la estrategia de escalada de la búsqueda de productos por texto libre. |
| 1.2 | 2026-08-29 | Se agrega un ítem surgido del diseño del módulo `inventory`: la deduplicación de alertas operativas sin restricción de unicidad en el esquema. |

---

## 1. Propósito

Este documento registra las **decisiones deliberadas de postergar trabajo** y las **limitaciones conocidas** del diseño. Existe porque una deuda no documentada no es una decisión: es un olvido esperando a que alguien lo descubra en el peor momento.

### 1.1. Criterio de Inclusión

| Sí es deuda técnica | No es deuda técnica |
| :--- | :--- |
| Se eligió un atajo consciente que costará más caro después. | Una funcionalidad que se decidió no construir (eso es **alcance excluido**, ver [`especificacion_requerimientos.md`](./especificacion_requerimientos.md) §1.3). |
| Una limitación real del diseño que alguien podría dar por resuelta. | Trabajo que simplemente todavía no se hizo y está planificado. |
| Una inconsistencia conocida entre documentos o artefactos. | Una preferencia estética sin consecuencia funcional. |

### 1.2. Escala de Severidad

| Nivel | Significado |
| :--- | :--- |
| **Alta** | Bloquea o encarece de forma significativa una etapa futura; hay que pagarla antes de un hito concreto. |
| **Media** | Genera riesgo real de defecto o de trabajo doble, pero el sistema opera correctamente sin resolverla. |
| **Baja** | Limitación conocida y aceptada; se documenta para que nadie la asuma resuelta. |

---

## 2. Registro

| ID | Título | Severidad | Estado | Disparador para pagarla |
| :--- | :--- | :--- | :--- | :--- |
| **DT-01** | Versionado del esquema con Flyway | Alta | Aceptada | Al montar el backend |
| **DT-02** | Datos de demostración acoplados al bootstrap del esquema | Media | Aceptada | Al montar el backend |
| **DT-03** | Rangos históricos de precio solapados no restringidos por el esquema | Media | Aceptada | Antes de habilitar la edición de precios históricos |
| **DT-04** | Cliente sin entidad propia en las ventas | Baja | Aceptada | Si se requiere historial o segmentación por cliente |
| **DT-05** | La coherencia del precio congelado sólo se verifica en el dominio | Baja | Aceptada | Ninguno; se mitiga con pruebas |
| **DT-06** | Tipografía inconsistente en el diagrama E-R | Baja | Aceptada | Si se rehace el diagrama E-R |
| **DT-07** | Exposición HTTP del cambio de unidad base, diferida | Baja | Aceptada | Al construir el módulo `inventory` |
| **DT-08** | Búsqueda de productos por texto libre resuelta con recorrido secuencial | Baja | Aceptada | Si el catálogo supera ~50 000 productos o si la prueba de latencia falla |
| **DT-09** | Deduplicación de alertas operativas sin restricción de unicidad en el esquema | Media | Aceptada | Cuando llegue el próximo cambio de esquema |

---

## 3. Fichas

### DT-01 — Versionado del esquema con Flyway

**Severidad:** Alta · **Estado:** Aceptada · **Esfuerzo estimado:** pequeño (menos de media jornada)

#### Situación actual
El esquema y los datos semilla viven en `backend/init-db/01-init-schema.sql` y `02-seed-data.sql`, ejecutados por el mecanismo de inicialización de la imagen de PostgreSQL. Ambos scripts están verificados contra PostgreSQL 17: crean las 19 tablas y cargan los datos sin errores.

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
Sección 3.7 y asunto abierto OI-A1 de [`decisiones_arquitectura_tecnica.md`](./decisiones_arquitectura_tecnica.md) · RNF-DIS-03.

---

### DT-02 — Datos de demostración acoplados al bootstrap del esquema

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

### DT-03 — Rangos históricos de precio solapados no restringidos por el esquema

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
RN-16 · RF-VEN-03 · sección 1.1 de [`diagrama_er.md`](./diagrama_er.md).

---

### DT-04 — Cliente sin entidad propia en las ventas

**Severidad:** Baja · **Estado:** Aceptada

#### Situación actual
`sales` guarda el cliente de forma desnormalizada en `customer_name` y `customer_tax_id`. No existe tabla de clientes.

#### Consecuencia
No hay historial de compras por cliente, no se puede segmentar una lista de precios por cliente y el mismo cliente puede quedar escrito de varias formas distintas. Es la razón por la que `RF-VEN-03` se materializa como listas de precios por sucursal y no por perfil de cliente.

#### Por qué se aceptó
El dominio de la prueba es la gestión de inventario multi-sucursal, no el CRM. La segmentación por cliente está declarada explícitamente fuera de alcance.

#### Referencias
Asunto abierto OI-02 de [`especificacion_requerimientos.md`](./especificacion_requerimientos.md).

---

### DT-05 — La coherencia del precio congelado sólo se verifica en el dominio

**Severidad:** Baja · **Estado:** Aceptada

#### Situación actual
`sale_items.list_unit_price` congela el precio de lista al momento de la venta, y la restricción `check_applied_price_not_above_list` garantiza que el precio aplicado nunca lo supere. Pero **nada en el esquema obliga a que ese `list_unit_price` sea efectivamente el precio que la lista tenía vigente en esa fecha**.

#### Por qué se aceptó
Verificarlo en la base de datos exigiría un *trigger* que consulte `price_list_items` en cada inserción — precisamente el antipatrón que la sección 2.1 del ADR prohíbe: reglas de negocio escondidas donde nadie las prueba. La garantía correcta es de dominio.

#### Mitigación
El caso de uso `CU-VEN-01` resuelve el precio y lo congela en la misma operación; una prueba automatizada debe verificar que un `list_unit_price` inconsistente con la lista vigente sea rechazado por el dominio. Queda cubierto por el objetivo de cobertura de RNF-MAN-01.

---

### DT-06 — Tipografía inconsistente en el diagrama E-R

**Severidad:** Baja · **Estado:** Aceptada · **Esfuerzo estimado:** trivial

#### Situación actual
`diagrams/diagrama_er.excalidraw` usa `fontFamily` 1 y 3, mientras los otros quince diagramas del repositorio usan `fontFamily` 5. Las entidades de precios agregadas después respetaron la tipografía original del archivo para no mezclar dos fuentes dentro del mismo lienzo.

#### Por qué se aceptó
Es puramente estético y no afecta legibilidad ni contenido. Unificar exigiría regenerar el diagrama E-R completo.

---

### DT-07 — Exposición HTTP del cambio de unidad base, diferida

**Severidad:** Baja · **Estado:** Aceptada · **Esfuerzo estimado:** pequeño · **Origen:** diseño del módulo `catalog`

#### Situación actual
El módulo `catalog` entrega la regla de dominio que gobierna el cambio de `products.base_unit`, el puerto entrante `shared/stock/ProductStockPresencePort`, la política que la aplica y sus pruebas unitarias. **Ningún endpoint las alcanza.** Dentro del alcance de `catalog`, `base_unit` es de hecho inmutable: se fija al crear el producto y `PUT /api/catalog/products/{externalId}` rechaza el campo con `400 invalid_request`.

#### Por qué se aceptó
La regla sólo permite el cambio cuando el producto no tiene saldos ni movimientos de Kardex, y quien puede responder esa pregunta es `inventory`, que todavía no existe. Sin implementación del puerto la política falla cerrada —nunca abierta—, de modo que toda llamada respondería un conflicto para siempre. Publicar una operación que jamás ha tenido éxito es peor que no publicarla: los clientes programarían contra algo que nunca funcionó, y el documento OpenAPI mentiría.

#### Por qué es deuda
La regla, el puerto y la política son código de producción sin ruta de entrada. Están cubiertos por pruebas unitarias precisamente para que no se degraden a código muerto, pero nadie los ejercita de punta a punta hasta que llegue su implementador.

#### Plan de pago
El cambio que construya `inventory`:

1. Implementa `ProductStockPresencePort` con un adaptador propio. El predicado es exacto: un producto está intacto cuando **(a)** no tiene fila de `branch_inventories` con existencia actual, reservada o en tránsito distinta de cero, **y (b)** no tiene ninguna fila de `kardex_movements`, en ninguna sucursal, nunca. La cláusula (b) no es redundante: un producto cuyo saldo volvió a cero conserva historial escrito en la unidad base **anterior**, y RN-13 existe justamente para impedir que ese historial se reinterprete en silencio.
2. Publica `PATCH /api/catalog/products/{externalId}/base-unit`.
3. Define **dos** códigos de error distintos: uno para «el producto tiene historial» y otro para «no puedo verificarlo». Unificarlos haría que una carencia de infraestructura pareciera un rechazo de negocio, tanto para quien llama como para quien lee los registros.
4. Verifica que la comprobación de la precondición y la escritura de `base_unit` ocurran en **la misma transacción**: si se separan, una recepción de mercadería concurrente crea el primer movimiento entre la comprobación y el `commit`.

#### Referencias
RN-13 · RF-INV-01 · RF-INV-02 · `openspec/changes/add-catalog-module/contract.md` §2.2 y decisión PA-08.

---

### DT-08 — Búsqueda de productos por texto libre resuelta con recorrido secuencial

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

### DT-09 — Deduplicación de alertas operativas sin restricción de unicidad en el esquema

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

## 4. Lo que NO es Deuda Técnica

Estas decisiones son **alcance excluido**, no deuda. Se listan aquí porque suelen confundirse:

facturación fiscal y timbrado electrónico · contabilidad general · nómina y recursos humanos · comercio electrónico · multimoneda · multiempresa (*multi-tenant*) · trazabilidad por lote y fecha de caducidad · aplicación móvil nativa.

El detalle y su justificación están en la sección 1.3 de [`especificacion_requerimientos.md`](./especificacion_requerimientos.md).

---

## 5. Mantenimiento de este Registro

1. **Toda decisión de postergar trabajo se registra aquí en el momento en que se toma**, no al final. Una deuda documentada tarde ya causó su daño.
2. Cada ítem debe indicar **qué la dispara**: una deuda sin condición de pago es una excusa con formato de tabla.
3. Al pagar una deuda se marca **Resuelta** con la fecha y el cambio que la saldó; no se borra. El histórico explica por qué el sistema es como es.
4. Antes de cada entrega se revisa el registro completo y se reevalúa la severidad: una deuda baja puede volverse alta cuando cambia el contexto.
