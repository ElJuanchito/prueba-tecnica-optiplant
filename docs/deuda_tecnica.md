# Registro de Deuda Técnica
## Sistema de Gestión de Inventario Multi-Sucursal — OptiPlant Consultores

| Versión | Fecha | Cambios |
| :--- | :--- | :--- |
| 1.0 | 2026-08-26 | Registro inicial con seis ítems identificados durante el diseño. |

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
3. Se **elimina el montaje de `init-db/`** del `docker-compose.yml`.
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
