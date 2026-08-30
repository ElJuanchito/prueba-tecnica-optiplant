# Historias de Usuario y Backlog del Producto
## Sistema de Gestión de Inventario Multi-Sucursal — OptiPlant Consultores
### Cumplimiento: Sección 6.3 — Historias de Usuario

---

## 1. Propósito y Convenciones

Este documento traduce los requerimientos funcionales de [`especificacion_requerimientos.md`](./especificacion_requerimientos.md) y los casos de uso de [`casos_de_uso.md`](./casos_de_uso.md) al lenguaje del negocio, expresando **para quién** se construye cada capacidad y **qué valor** entrega.

### 1.1. Formato Adoptado

Cada historia se enuncia con la plantilla estándar y se acompaña de **criterios de aceptación verificables** en notación Gherkin (`Dado / Cuando / Entonces`), de modo que cada criterio sea traducible directamente a una prueba automatizada.

> **Como** \<rol\>, **quiero** \<capacidad\>, **para** \<beneficio de negocio\>.

### 1.2. Convenciones de Identificación y Priorización

| Convención | Significado |
| :--- | :--- |
| `HU-<MÓDULO>-<NN>` | Identificador unívoco de la historia. |
| **Prioridad MoSCoW** | `Must` (imprescindible para el MVP), `Should` (importante, no bloqueante), `Could` (deseable), `Won't` (fuera del alcance actual). |
| **Estimación** | Puntos de historia en escala Fibonacci (1, 2, 3, 5, 8, 13) según complejidad relativa. |
| **Trazabilidad** | Requerimientos funcionales y casos de uso que la historia materializa. |

### 1.3. Criterio de Calidad (INVEST)

Toda historia del backlog cumple: **I**ndependiente, **N**egociable, **V**aliosa, **E**stimable, **S**uficientemente pequeña y **T**esteable. Las historias que superaron los 13 puntos fueron divididas por flujo de negocio (caso testigo: el ciclo de transferencia se descompuso en seis historias, una por transición de estado, en lugar de una única historia monolítica).

---

## 2. Épicas del Producto

| Épica | Nombre | Valor de Negocio | Historias |
| :--- | :--- | :--- | :---: |
| **EP-01** | Seguridad y Control de Acceso Multi-Sucursal | Garantiza autonomía operativa con aislamiento de datos entre sucursales. | 3 |
| **EP-02** | Gestión de Inventario y Trazabilidad | Núcleo del sistema: saldos confiables y auditables en todo momento. | 6 |
| **EP-03** | Abastecimiento y Valorización de Compras | Permite reponer stock y conocer el costo real del inventario. | 4 |
| **EP-04** | Operación Comercial de Ventas | Convierte inventario en ingresos sin permitir sobreventa. | 4 |
| **EP-05** | Transferencias entre Sucursales | Optimiza el inventario de la red moviendo stock donde se necesita. | 6 |
| **EP-06** | Logística y Cumplimiento de Envíos | Da visibilidad y control sobre los tiempos de traslado. | 3 |
| **EP-07** | Analítica y Toma de Decisiones | Convierte datos operativos en decisiones de compra y reposición. | 3 |
| **EP-08** | Alertas Inteligentes (Valor Agregado) | Anticipa quiebres de stock y desvíos logísticos sin intervención humana. | 2 |

---

## 3. EP-01 — Seguridad y Control de Acceso Multi-Sucursal

### HU-SEG-01 — Autenticación con contexto de sucursal

> **Como** usuario del sistema, **quiero** autenticarme con mis credenciales y recibir un contexto de sucursal asociado a mi sesión, **para** operar únicamente sobre la información que me corresponde sin riesgo de afectar otras sucursales.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Must** | 5 | RF-SEG-01 · RNF-SEC-01, RNF-SEC-02, RNF-SEC-03 · CU-SEG-01 |

**Criterios de aceptación**
* **Dado** un usuario activo con credenciales válidas, **cuando** se autentica, **entonces** el sistema devuelve un token firmado que contiene su identificador, su rol y su sucursal asignada.
* **Dado** un usuario con credenciales inválidas, **cuando** intenta autenticarse, **entonces** el sistema responde con un mensaje genérico que no revela si el usuario existe.
* **Dado** un usuario deshabilitado, **cuando** intenta autenticarse con credenciales correctas, **entonces** el acceso es denegado.
* **Dado** cualquier solicitud a la API sin token válido, **cuando** se procesa, **entonces** el sistema responde `401 Unauthorized` sin ejecutar lógica de negocio.
* **Dado** un token expirado o manipulado, **cuando** se utiliza, **entonces** la solicitud es rechazada.

---

### HU-SEG-02 — Aislamiento de mutaciones por sucursal

> **Como** administrador general, **quiero** que cada gerente y operador solo pueda modificar el inventario de su propia sucursal, **para** evitar que un error humano en una sucursal corrompa el stock de otra.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Must** | 5 | RF-SEG-01 · RNF-SEC-03 · CU-SEG-01, CU-INV-04 |

**Criterios de aceptación**
* **Dado** un operador de la Sucursal A, **cuando** intenta registrar una venta imputada a la Sucursal B, **entonces** el sistema responde `403 Forbidden` y no persiste cambio alguno.
* **Dado** un operador de la Sucursal A, **cuando** consulta el inventario de la Sucursal B, **entonces** el sistema devuelve los saldos en modo solo lectura.
* **Dado** un administrador general, **cuando** opera sobre cualquier sucursal, **entonces** el sistema autoriza la operación y la registra en la bitácora de auditoría.
* **Dado** cualquier operación de mutación, **cuando** se ejecuta, **entonces** la sucursal se deriva del token de sesión y nunca de un parámetro manipulable por el cliente.

---

### HU-SEG-03 — Administración de usuarios, roles y sucursales

> **Como** administrador general, **quiero** dar de alta sucursales y usuarios con su rol correspondiente, **para** incorporar nuevos nodos a la red sin requerir cambios de código ni de esquema.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Must** | 3 | RF-SEG-02, RF-SEG-03 · RNF-ESC-02, RNF-SEC-01 · CU-SEG-02, CU-SEG-03 |

**Criterios de aceptación**
* **Dado** un administrador general, **cuando** crea una sucursal con código único, **entonces** la sucursal queda disponible para asignación de usuarios e inventario.
* **Dado** un intento de crear una sucursal con un código ya existente, **cuando** se confirma, **entonces** el sistema rechaza la operación indicando el conflicto.
* **Dado** un usuario creado con rol `OPERATOR`, **cuando** inicia sesión, **entonces** solo accede a las capacidades autorizadas para ese rol.
* **Dado** un usuario deshabilitado, **cuando** se consulta el histórico, **entonces** sus movimientos previos permanecen visibles e intactos (baja lógica, nunca física).
* **Dado** un gerente de sucursal, **cuando** da de alta, edita o deshabilita un usuario con rol `OPERATOR` de su propia sucursal, **entonces** la operación se ejecuta; si el usuario objetivo pertenece a otra sucursal o no es `OPERATOR`, el sistema la rechaza.

---

## 4. EP-02 — Gestión de Inventario y Trazabilidad

### HU-INV-01 — Consulta de disponibilidad en la red de sucursales

> **Como** operador de inventario, **quiero** consultar la disponibilidad de un producto en todas las sucursales de la red, **para** resolver un faltante local solicitando una transferencia en lugar de perder la venta.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Must** | 5 | RF-INV-03, RF-INV-04 · CU-INV-03, CU-INV-04 |

**Criterios de aceptación**
* **Dado** un producto del catálogo, **cuando** el operador consulta su disponibilidad en la red, **entonces** el sistema muestra stock disponible, reservado y en tránsito por cada sucursal activa.
* **Dado** el resultado de la consulta, **cuando** se presenta, **entonces** la sucursal propia aparece destacada y las sucursales con excedente quedan claramente identificadas.
* **Dado** un producto sin stock en ninguna sucursal, **cuando** se consulta, **entonces** el sistema informa el faltante global y sugiere generar una orden de compra.
* **Dado** un volumen normal de operación, **cuando** se ejecuta la consulta, **entonces** el 95 % de las respuestas se entregan en menos de 200 ms.

---

### HU-INV-02 — Trazabilidad completa de movimientos (Kardex)

> **Como** gerente de sucursal, **quiero** consultar el histórico completo de movimientos de cada producto con fecha, responsable, motivo y cantidad, **para** auditar diferencias de inventario y responder ante cualquier discrepancia.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Must** | 8 | RF-INV-08, RNF-INT-02 · CU-INV-08 |

**Criterios de aceptación**
* **Dado** cualquier operación que altere el stock (venta, compra, transferencia, ajuste o merma), **cuando** se confirma, **entonces** se inserta un movimiento en el Kardex con producto, sucursal, tipo, cantidad, costo unitario, stock previo, stock resultante, motivo, usuario y marca temporal UTC.
* **Dado** un movimiento ya registrado, **cuando** se intenta modificarlo o eliminarlo, **entonces** el sistema lo impide: la tabla es de solo inserción.
* **Dado** un rango de fechas y un producto, **cuando** el gerente consulta el Kardex, **entonces** obtiene los movimientos ordenados cronológicamente con su saldo resultante.
* **Dado** el saldo actual de un producto, **cuando** se recalcula sumando su Kardex desde la carga inicial, **entonces** el resultado coincide exactamente con el stock registrado.

---

### HU-INV-03 — Ingreso de productos con actualización de costo promedio

> *Historia priorizada explícitamente en el enunciado de la prueba técnica.*
>
> **Como** operador de inventario, **quiero** registrar el ingreso de productos con su precio de compra, **para** mantener el costo promedio del inventario actualizado y generar órdenes de pago a proveedores.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Must** | 8 | RF-COM-02, RF-COM-04, RF-INV-05 · CU-COM-04 |

**Criterios de aceptación**
* **Dado** una orden de compra aprobada, **cuando** el operador registra la recepción con su costo unitario, **entonces** el stock de la sucursal se incrementa por la cantidad recibida.
* **Dado** un producto con 100 unidades a un costo promedio de $10 y una recepción de 100 unidades a $20, **cuando** se confirma la recepción, **entonces** el nuevo costo promedio ponderado del producto es exactamente $15.
* **Dado** una recepción confirmada, **cuando** se consulta el Kardex, **entonces** existe un movimiento `PURCHASE_RECEIPT` referenciando la orden de compra que lo originó.
* **Dado** una recepción por una cantidad menor a la ordenada, **cuando** se confirma, **entonces** la orden queda en estado `PARTIALLY_RECEIVED` conservando el saldo pendiente.
* **Dado** un fallo en cualquier paso de la recepción, **cuando** ocurre, **entonces** ni el stock ni el costo promedio se modifican (transacción atómica).

---

### HU-INV-04 — Ajuste manual de inventario con justificación obligatoria

> **Como** gerente de sucursal, **quiero** ajustar el stock del sistema al conteo físico real indicando un motivo obligatorio, **para** corregir diferencias de inventario dejando constancia auditable de quién lo hizo y por qué.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Must** | 5 | RF-INV-05, RF-INV-06, RF-VAL-02 · CU-INV-05 |

**Criterios de aceptación**
* **Dado** un gerente de sucursal, **cuando** registra un ajuste con motivo, **entonces** el stock se actualiza y se inserta un movimiento `ADJUSTMENT_POS` o `ADJUSTMENT_NEG` según el signo de la diferencia.
* **Dado** un ajuste sin motivo informado, **cuando** se intenta confirmar, **entonces** el sistema rechaza la operación.
* **Dado** un operador de inventario, **cuando** intenta registrar un ajuste manual, **entonces** el sistema deniega la operación por rol insuficiente y registra el intento.
* **Dado** un ajuste que dejaría el stock en valor negativo, **cuando** se intenta confirmar, **entonces** el sistema lo rechaza.
* **Dado** un ajuste confirmado, **cuando** se consulta la bitácora de auditoría, **entonces** existe un registro con el estado previo y posterior de la operación.

---

### HU-INV-05 — Registro de mermas, daños y caducidad

> **Como** operador de inventario, **quiero** registrar la baja de productos dañados o vencidos indicando el motivo, **para** que el inventario del sistema refleje la realidad física del depósito.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Must** | 3 | RF-INV-06, RF-INV-08 · CU-INV-06 |

**Criterios de aceptación**
* **Dado** un producto con stock disponible, **cuando** el operador registra una merma con motivo, **entonces** el stock se descuenta y se inserta un movimiento `DAMAGE_WASTE` en el Kardex.
* **Dado** una merma por una cantidad superior al stock disponible, **cuando** se intenta confirmar, **entonces** el sistema la rechaza.
* **Dado** una merma registrada, **cuando** se consulta el reporte de pérdidas del período, **entonces** la operación aparece valorizada al costo promedio vigente.

---

### HU-INV-06 — Gestión de múltiples unidades de medida

> **Como** administrador general, **quiero** definir varias unidades de medida por producto con su factor de conversión, **para** que las sucursales puedan operar en cajas, pallets o unidades sin errores de cálculo.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Should** | 5 | RF-INV-01, RF-INV-02 · CU-INV-01, CU-INV-02 |

**Criterios de aceptación**
* **Dado** un producto con unidad base "unidad", **cuando** se define la unidad "caja" con factor 12, **entonces** el sistema permite operar en ambas unidades.
* **Dado** una venta de 2 cajas de un producto con factor 12, **cuando** se confirma, **entonces** el stock se descuenta en 24 unidades base.
* **Dado** un factor de conversión menor o igual a cero, **cuando** se intenta guardar, **entonces** el sistema lo rechaza.
* **Dado** cualquier consulta de stock, **cuando** se presenta, **entonces** el saldo se expresa siempre en unidad base, evitando ambigüedad.

---

## 5. EP-03 — Abastecimiento y Valorización de Compras

### HU-COM-01 — Creación y aprobación de órdenes de compra

> **Como** operador de inventario, **quiero** generar órdenes de compra a proveedores con precios, descuentos y plazos de pago, **para** formalizar la reposición de stock y dejar constancia de las condiciones pactadas.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Must** | 5 | RF-COM-01, RF-COM-05 · CU-COM-02, CU-COM-03 |

**Criterios de aceptación**
* **Dado** un proveedor activo, **cuando** el operador crea una orden con uno o más productos, **entonces** la orden se persiste en estado `PENDING` con número correlativo único.
* **Dado** una orden en estado `PENDING`, **cuando** el gerente la aprueba, **entonces** su estado pasa a `APPROVED` y queda habilitada para recepción.
* **Dado** una orden en estado `PENDING` o `APPROVED`, **cuando** el gerente la cancela, **entonces** su estado pasa a `CANCELLED` y ya no admite recepciones.
* **Dado** una orden con ítems, **cuando** se calcula su total, **entonces** el monto refleja precios unitarios, descuentos por ítem y suma de subtotales.
* **Dado** un operador de inventario, **cuando** intenta aprobar una orden, **entonces** el sistema deniega la operación por rol insuficiente.

---

### HU-COM-02 — Histórico de compras por proveedor y producto

> **Como** gerente de sucursal, **quiero** consultar el histórico de compras filtrando por proveedor, producto y rango de fechas, **para** negociar mejores condiciones comerciales con evidencia concreta.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Should** | 3 | RF-COM-03 · CU-COM-05 |

**Criterios de aceptación**
* **Dado** un rango de fechas y un proveedor, **cuando** el gerente consulta el histórico, **entonces** obtiene las órdenes con su estado, monto total y fecha de recepción.
* **Dado** un producto, **cuando** se consulta su histórico de compras, **entonces** se visualiza la evolución del costo unitario pactado en el tiempo.
* **Dado** un gerente de sucursal, **cuando** consulta el histórico, **entonces** solo accede a las compras de su propia sucursal.

---

### HU-COM-03 — Gestión de proveedores

> **Como** administrador general, **quiero** registrar proveedores con sus datos de contacto y condiciones comerciales, **para** asociarlos a las órdenes de compra y evaluar su desempeño.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Should** | 2 | RF-COM-06 · CU-COM-01 |

**Criterios de aceptación**
* **Dado** un administrador general, **cuando** registra un proveedor, **entonces** este queda disponible para su selección en órdenes de compra.
* **Dado** un proveedor con órdenes asociadas, **cuando** se intenta eliminar, **entonces** el sistema realiza una baja lógica preservando el histórico.

---

### HU-COM-04 — Recepción parcial de mercancía

> **Como** operador de inventario, **quiero** registrar recepciones parciales de una orden de compra, **para** ingresar al stock lo que efectivamente llegó sin cerrar la orden pendiente.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Should** | 5 | RF-COM-02 · CU-COM-04 |

**Criterios de aceptación**
* **Dado** una orden aprobada de 100 unidades, **cuando** se reciben 60, **entonces** el stock aumenta en 60, la orden queda en `PARTIALLY_RECEIVED` y el saldo pendiente es 40.
* **Dado** una orden parcialmente recibida, **cuando** se recibe el saldo restante, **entonces** la orden pasa a `RECEIVED`.
* **Dado** una recepción superior a lo ordenado, **cuando** se intenta confirmar, **entonces** el sistema exige autorización de un gerente.

---

## 6. EP-04 — Operación Comercial de Ventas

### HU-VEN-01 — Registro de venta con validación de stock

> **Como** operador de inventario, **quiero** registrar una venta validando la disponibilidad antes de confirmarla, **para** no comprometer mercancía inexistente ni generar saldos negativos.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Must** | 8 | RF-VEN-01, RF-VEN-02, RF-INV-06 · CU-VEN-01 |

**Criterios de aceptación**
* **Dado** un producto con stock suficiente, **cuando** el operador confirma la venta, **entonces** el stock se descuenta, se genera el comprobante y se inserta un movimiento `SALE` en el Kardex.
* **Dado** un producto con stock insuficiente, **cuando** el operador intenta confirmar la venta, **entonces** el sistema la rechaza indicando cantidad solicitada y disponible, y no persiste ningún cambio.
* **Dado** dos ventas concurrentes sobre la última unidad disponible, **cuando** ambas se confirman simultáneamente, **entonces** solo una prospera y la otra es rechazada por stock insuficiente.
* **Dado** una venta confirmada, **cuando** se consulta, **entonces** expone sucursal, fecha, responsable, ítems, descuentos, impuestos y total.
* **Dado** una venta que deja el stock por debajo del umbral mínimo, **cuando** se confirma, **entonces** el sistema genera una alerta de reabastecimiento.

---

### HU-VEN-02 — Listas de precios y descuentos

> **Como** operador de inventario, **quiero** aplicar la lista de precios y los descuentos vigentes al registrar una venta, **para** respetar las políticas comerciales sin cálculos manuales.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Should** | 5 | RF-VEN-03 · CU-VEN-02 |

**Criterios de aceptación**
* **Dado** un producto con precio de lista definido, **cuando** se agrega a una venta, **entonces** el precio unitario se precarga automáticamente.
* **Dado** un descuento aplicado por ítem, **cuando** se calcula el total, **entonces** el subtotal refleja el descuento y el total agrega los impuestos correspondientes.
* **Dado** un descuento superior al máximo autorizado para el rol, **cuando** se intenta confirmar, **entonces** el sistema exige autorización de un gerente.
* **Dado** un descuento fuera del rango 0–100 %, **cuando** se intenta guardar, **entonces** el sistema lo rechaza.

---

### HU-VEN-03 — Anulación de venta con reversión de stock

> **Como** gerente de sucursal, **quiero** anular una venta registrada por error, **para** devolver la mercancía al inventario dejando traza de la corrección.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Should** | 5 | RF-VEN-05 · CU-VEN-03 |

**Criterios de aceptación**
* **Dado** una venta en estado `COMPLETED`, **cuando** el gerente la anula, **entonces** su estado pasa a `CANCELLED` y el stock se reintegra a la sucursal.
* **Dado** una venta anulada, **cuando** se consulta el Kardex, **entonces** existe un movimiento de reversión referenciado a la venta original; el movimiento original **no** se elimina.
* **Dado** un operador de inventario, **cuando** intenta anular una venta, **entonces** el sistema deniega la operación por rol insuficiente.
* **Dado** una venta ya anulada, **cuando** se intenta anular nuevamente, **entonces** el sistema rechaza la operación.

---

### HU-VEN-04 — Consulta de comprobantes de venta

> **Como** operador de inventario, **quiero** consultar los comprobantes de las ventas registradas, **para** responder consultas de clientes y verificar operaciones del turno.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Should** | 3 | RF-VEN-04 · CU-VEN-04 |

**Criterios de aceptación**
* **Dado** una venta confirmada, **cuando** se consulta por su número de comprobante, **entonces** el sistema devuelve su detalle completo.
* **Dado** un rango de fechas, **cuando** el operador lista las ventas de su sucursal, **entonces** obtiene el listado paginado con totales.
* **Dado** un operador de la Sucursal A, **cuando** consulta un comprobante de la Sucursal B, **entonces** el sistema deniega el acceso.

---

### HU-VEN-05 — Gestión de clientes y su histórico de compras

> **Como** operador de inventario, **quiero** registrar y editar los datos de un cliente y consultar su histórico de compras, **para** atribuir las ventas a una parte conocida y dar seguimiento a su relación comercial.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Should** | 3 | RF-VEN-06 · CU-VEN-05 · CU-VEN-06 |

**Criterios de aceptación**
* **Dado** un cliente nuevo en el mostrador, **cuando** el operador lo registra con su nombre y, opcionalmente, su identificación tributaria y datos de contacto, **entonces** el cliente queda disponible para asociarlo a las ventas.
* **Dado** un cliente ya registrado, **cuando** se registra una venta indicando su identificador, **entonces** la venta guarda el vínculo y congela el nombre y la identificación tal como estaban en ese momento; una edición posterior del cliente no altera el comprobante.
* **Dado** un identificador tributario que ya pertenece a otro cliente, **cuando** se intenta registrar o editar, **entonces** el sistema lo rechaza.
* **Dado** un cliente, **cuando** se consulta su histórico de compras, **entonces** el operador ve solo las ventas de su propia sucursal con ese cliente y sus totales; el Administrador las ve en toda la red.
* **Dado** un cliente dado de baja, **cuando** se intenta asociarlo a una nueva venta, **entonces** el sistema lo rechaza, pero su histórico sigue siendo consultable. Solo el Administrador puede dar de baja o reactivar un cliente.

---

## 7. EP-05 — Transferencias entre Sucursales

### HU-TRA-01 — Solicitud de transferencia con nivel de urgencia

> *Historia priorizada explícitamente en el enunciado de la prueba técnica.*
>
> **Como** operador de inventario, **quiero** solicitar la transferencia de un producto desde otra sucursal con indicación de urgencia, **para** que la sucursal origen pueda priorizar el despacho según disponibilidad.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Must** | 5 | RF-TRA-01, RF-INV-04 · CU-TRA-01 |

**Criterios de aceptación**
* **Dado** un producto con stock en otra sucursal, **cuando** el operador genera la solicitud indicando origen, cantidad y urgencia, **entonces** la transferencia se crea en estado `REQUESTED` con número único.
* **Dado** una solicitud creada, **cuando** la sucursal origen consulta sus solicitudes pendientes, **entonces** las visualiza ordenadas por nivel de urgencia.
* **Dado** una solicitud donde origen y destino coinciden, **cuando** se intenta crear, **entonces** el sistema la rechaza.
* **Dado** el momento de la solicitud, **cuando** se crea, **entonces** el stock de la sucursal origen **no** se descuenta: la solicitud no reserva mercancía.

---

### HU-TRA-02 — Aprobación o ajuste de la solicitud por la sucursal origen

> **Como** gerente de la sucursal origen, **quiero** revisar la disponibilidad y aprobar, ajustar o rechazar una solicitud de transferencia, **para** proteger la operación de mi propia sucursal antes de comprometer mercancía.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Must** | 5 | RF-TRA-02 · CU-TRA-02 |

**Criterios de aceptación**
* **Dado** una transferencia en estado `REQUESTED`, **cuando** el gerente de origen la aprueba, **entonces** su estado pasa a `IN_PREPARATION`.
* **Dado** una solicitud de 100 unidades con solo 60 disponibles, **cuando** el gerente aprueba parcialmente, **entonces** la cantidad aprobada queda en 60 y la diferencia se documenta.
* **Dado** una transferencia rechazada, **cuando** se confirma el rechazo, **entonces** su estado pasa a `CANCELLED` y la sucursal solicitante es notificada.
* **Dado** un gerente de una sucursal distinta al origen, **cuando** intenta aprobar la solicitud, **entonces** el sistema deniega la operación.

---

### HU-TRA-03 — Despacho con generación de stock en tránsito

> **Como** operador de la sucursal origen, **quiero** registrar el despacho indicando transportista y fecha estimada de llegada, **para** que el stock salga de mi inventario y quede visible como *en tránsito* para la sucursal destino.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Must** | 8 | RF-TRA-03, RF-LOG-01 · CU-TRA-03 |

**Criterios de aceptación**
* **Dado** una transferencia en estado `IN_PREPARATION`, **cuando** el operador registra el despacho, **entonces** su estado pasa a `IN_TRANSIT`, el stock disponible de origen se descuenta y el stock en tránsito de destino se incrementa.
* **Dado** un despacho confirmado, **cuando** se consulta el Kardex de la sucursal origen, **entonces** existe un movimiento `TRANSFER_OUT`.
* **Dado** una transferencia aprobada cuyo stock fue consumido por ventas posteriores, **cuando** se intenta despachar, **entonces** el sistema rechaza la operación y la transferencia permanece en `IN_PREPARATION`.
* **Dado** stock en tránsito en la sucursal destino, **cuando** se intenta venderlo, **entonces** el sistema lo rechaza: el stock en tránsito no es vendible.
* **Dado** una transferencia en estado `REQUESTED`, **cuando** se intenta despachar directamente, **entonces** el sistema lo impide por violación de la máquina de estados.

---

### HU-TRA-04 — Confirmación de recepción completa

> **Como** operador de la sucursal destino, **quiero** confirmar la recepción completa de una transferencia, **para** que el stock quede disponible para la venta de forma inmediata.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Must** | 5 | RF-TRA-04 · CU-TRA-04 |

**Criterios de aceptación**
* **Dado** una transferencia en estado `IN_TRANSIT`, **cuando** el operador confirma la recepción completa, **entonces** su estado pasa a `RECEIVED`, el stock en tránsito se libera y el stock disponible de destino se incrementa.
* **Dado** una recepción confirmada, **cuando** se consulta el Kardex de la sucursal destino, **entonces** existe un movimiento `TRANSFER_IN`.
* **Dado** una recepción confirmada, **cuando** se consulta la transferencia, **entonces** registra la fecha/hora real de arribo.
* **Dado** un operador de una sucursal distinta al destino, **cuando** intenta confirmar la recepción, **entonces** el sistema deniega la operación.

---

### HU-TRA-05 — Recepción parcial con registro de discrepancia

> **Como** operador de la sucursal destino, **quiero** registrar los faltantes cuando la mercancía recibida no coincide con la despachada, **para** ingresar solo lo real y activar el reclamo correspondiente.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Must** | 8 | RF-TRA-05, RF-VAL-01 · CU-TRA-05 |

**Criterios de aceptación**
* **Dado** un despacho de 100 unidades con 90 recibidas, **cuando** el operador confirma la recepción parcial, **entonces** el stock de destino aumenta en 90, la discrepancia registrada es 10 y el stock en tránsito queda en cero.
* **Dado** una recepción con discrepancia, **cuando** se confirma, **entonces** el estado de la transferencia pasa a `RECEIVED_WITH_DISCREPANCY`.
* **Dado** una discrepancia registrada, **cuando** se confirma, **entonces** el sistema genera una alerta `TRANSFER_DISCREPANCY` de severidad crítica visible para ambas sucursales.
* **Dado** una discrepancia, **cuando** el gerente define su tratamiento, **entonces** puede optar por reenvío, ajuste por merma en origen o reclamación al transportista.
* **Dado** una recepción sin diferencias, **cuando** se confirma, **entonces** el flujo deriva a recepción completa y no se genera alerta.

---

### HU-TRA-06 — Cancelación de solicitud previa al despacho

> **Como** gerente de sucursal, **quiero** cancelar una solicitud de transferencia que ya no es necesaria, **para** liberar la atención de la sucursal origen sin dejar solicitudes fantasma.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Should** | 3 | RF-TRA-06 · CU-TRA-06 |

**Criterios de aceptación**
* **Dado** una transferencia en estado `REQUESTED` o `IN_PREPARATION`, **cuando** el gerente la cancela, **entonces** su estado pasa a `CANCELLED`.
* **Dado** una transferencia en estado `IN_TRANSIT`, **cuando** se intenta cancelar, **entonces** el sistema lo impide: la mercancía ya está en movimiento y debe resolverse por recepción.
* **Dado** una transferencia cancelada, **cuando** se consulta el inventario, **entonces** ningún saldo de stock fue alterado.

---

## 8. EP-06 — Logística y Cumplimiento de Envíos

### HU-LOG-01 — Monitoreo de transferencias activas

> **Como** gerente de sucursal, **quiero** visualizar en un tablero el estado de todas las transferencias en curso, **para** anticipar el impacto de los traslados sobre mi disponibilidad de stock.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Must** | 5 | RF-LOG-01, RF-DSH-03 · CU-LOG-02 |

**Criterios de aceptación**
* **Dado** transferencias en distintos estados, **cuando** el gerente accede al monitor, **entonces** visualiza cada una con su estado, sucursales, productos, cantidades y fecha estimada de arribo.
* **Dado** el monitor, **cuando** se filtra por estado, **entonces** el listado se restringe al estado seleccionado.
* **Dado** una transferencia cuya fecha estimada de arribo ya venció, **cuando** se visualiza el monitor, **entonces** aparece destacada como demorada.

---

### HU-LOG-02 — Control de tiempos estimados contra tiempos reales

> **Como** gerente de sucursal, **quiero** comparar los tiempos estimados de entrega con los reales, **para** identificar rutas y transportistas que incumplen sistemáticamente.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Should** | 5 | RF-LOG-02, RF-LOG-04 · CU-LOG-03 |

**Criterios de aceptación**
* **Dado** una transferencia recibida, **cuando** se consulta su detalle, **entonces** el sistema muestra fecha estimada, fecha real y la desviación en horas.
* **Dado** un período de análisis, **cuando** se consulta el reporte de cumplimiento, **entonces** se obtiene el porcentaje de entregas puntuales por ruta y por sucursal.
* **Dado** una ruta con desvíos recurrentes, **cuando** se consulta el reporte, **entonces** aparece identificada entre las de menor cumplimiento.

---

### HU-LOG-03 — Parametrización de rutas logísticas

> **Como** administrador general, **quiero** definir rutas entre sucursales con su duración estimada, costo y prioridad, **para** que el sistema calcule fechas de arribo realistas y permita comparar alternativas.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Should** | 3 | RF-LOG-03 · CU-LOG-01 |

**Criterios de aceptación**
* **Dado** dos sucursales distintas, **cuando** el administrador define una ruta con duración, costo y prioridad, **entonces** la ruta queda disponible para las transferencias entre ese par.
* **Dado** una ruta cuyo origen y destino coinciden, **cuando** se intenta guardar, **entonces** el sistema la rechaza.
* **Dado** una transferencia despachada por una ruta parametrizada, **cuando** se registra el despacho, **entonces** la fecha estimada de arribo se precalcula con la duración de la ruta.

---

## 9. EP-07 — Analítica y Toma de Decisiones

### HU-DSH-01 — Comparativa de ventas contra meses anteriores

> *Historia priorizada explícitamente en el enunciado de la prueba técnica.*
>
> **Como** gerente de sucursal, **quiero** ver en un dashboard la comparativa de ventas entre el mes actual y los tres meses anteriores, **para** identificar tendencias y tomar decisiones de compra anticipadas.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Must** | 8 | RF-DSH-01, RF-DSH-02 · CU-DSH-01 |

**Criterios de aceptación**
* **Dado** ventas registradas en los últimos cuatro meses, **cuando** el gerente accede al dashboard, **entonces** visualiza una gráfica comparativa del volumen de ventas mes a mes.
* **Dado** el dashboard, **cuando** se presenta, **entonces** incluye la variación porcentual del mes en curso respecto del mes anterior.
* **Dado** un gerente de sucursal, **cuando** consulta el dashboard, **entonces** los datos corresponden exclusivamente a su sucursal.
* **Dado** una sucursal sin ventas en el período, **cuando** se consulta el dashboard, **entonces** el sistema muestra un estado vacío informativo, sin errores.
* **Dado** el dashboard, **cuando** se presenta, **entonces** incluye los productos de mayor y menor rotación del período.

---

### HU-DSH-02 — Indicadores de reabastecimiento crítico

> **Como** operador de inventario, **quiero** ver de forma destacada los productos agotados o próximos a agotarse, **para** actuar antes de perder ventas por falta de stock.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Must** | 5 | RF-DSH-04, RF-INV-07 · CU-DSH-02, CU-INV-07 |

**Criterios de aceptación**
* **Dado** un producto cuyo stock es menor o igual a su umbral mínimo, **cuando** se consulta el panel de reabastecimiento, **entonces** el producto aparece listado con su stock actual y su umbral.
* **Dado** un producto agotado, **cuando** se presenta el panel, **entonces** aparece con severidad crítica y prioridad de orden superior.
* **Dado** un producto crítico, **cuando** se selecciona, **entonces** el sistema ofrece como acciones directas crear una orden de compra o solicitar una transferencia.

---

### HU-DSH-03 — Tablero corporativo comparativo entre sucursales

> **Como** administrador general, **quiero** comparar el desempeño de todas las sucursales en un único tablero, **para** detectar sucursales con bajo rendimiento o con exceso de inventario inmovilizado.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Should** | 8 | RF-DSH-05 · CU-DSH-03 |

**Criterios de aceptación**
* **Dado** varias sucursales activas, **cuando** el administrador accede al tablero corporativo, **entonces** visualiza ventas, rotación y valor de inventario por sucursal.
* **Dado** el tablero, **cuando** se presenta, **entonces** las sucursales aparecen ordenables por cada indicador.
* **Dado** un gerente de sucursal, **cuando** intenta acceder al tablero corporativo, **entonces** el sistema deniega el acceso por rol insuficiente.

---

## 10. EP-08 — Alertas Inteligentes (Funcionalidad de Valor Agregado)

### HU-ALE-01 — Generación automática de alertas operativas

> **Como** gerente de sucursal, **quiero** que el sistema detecte y notifique automáticamente los quiebres de stock y las demoras logísticas, **para** reaccionar sin depender de que alguien revise manualmente los reportes.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Should** | 8 | RF-VAL-01, RF-INV-07 · CU-ALE-01 |

**Criterios de aceptación**
* **Dado** un movimiento que deja el stock por debajo del umbral mínimo, **cuando** se confirma, **entonces** el sistema genera una alerta `STOCK_MINIMUM` para esa sucursal y producto.
* **Dado** una transferencia cuya fecha estimada de arribo venció sin recepción, **cuando** el proceso programado se ejecuta, **entonces** el sistema genera una alerta `LOGISTIC_DELAY`.
* **Dado** una recepción con faltantes, **cuando** se confirma, **entonces** el sistema genera una alerta `TRANSFER_DISCREPANCY` de severidad crítica.
* **Dado** una condición de alerta ya notificada y aún no resuelta, **cuando** la condición persiste, **entonces** el sistema no duplica la alerta.

---

### HU-ALE-02 — Consulta y resolución de alertas

> **Como** gerente de sucursal, **quiero** consultar las alertas de mi sucursal y marcarlas como resueltas, **para** mantener el foco del equipo sobre lo que aún requiere acción.

| Prioridad | Estimación | Trazabilidad |
| :--- | :--- | :--- |
| **Should** | 3 | RF-VAL-01 · CU-ALE-02 |

**Criterios de aceptación**
* **Dado** alertas activas, **cuando** el gerente accede al centro de alertas, **entonces** las visualiza ordenadas por severidad y fecha.
* **Dado** una alerta activa, **cuando** el gerente la marca como resuelta, **entonces** el sistema registra la fecha de resolución y el usuario responsable.
* **Dado** un gerente de la Sucursal A, **cuando** consulta el centro de alertas, **entonces** solo visualiza las alertas de su propia sucursal.

---

## 11. Definición de Terminado (*Definition of Done*)

Una historia se considera terminada únicamente cuando cumple **todos** los siguientes criterios:

1. Todos los criterios de aceptación están implementados y verificados.
2. La lógica de negocio reside exclusivamente en el backend; el frontend no toma decisiones de dominio (restricción técnica del proyecto).
3. Existen pruebas automatizadas que cubren el camino feliz y, como mínimo, las excepciones de negocio declaradas en el caso de uso asociado.
4. Toda operación que muta stock se ejecuta dentro de una transacción atómica y genera su asiento correspondiente en el Kardex.
5. La autorización por rol y el aislamiento por sucursal están verificados para la funcionalidad entregada.
6. El endpoint expuesto está documentado en el contrato de la API y expone identificadores públicos (`external_id`), nunca identificadores internos.
7. La funcionalidad se ejecuta correctamente en el entorno levantado con `docker compose up`, sin configuración manual adicional.

---

## 12. Resumen del Backlog y Priorización del MVP

| Épica | Historias | Puntos | Must | Should |
| :--- | :---: | :---: | :---: | :---: |
| EP-01 — Seguridad y Control de Acceso | 3 | 13 | 3 | 0 |
| EP-02 — Inventario y Trazabilidad | 6 | 34 | 5 | 1 |
| EP-03 — Compras y Valorización | 4 | 15 | 1 | 3 |
| EP-04 — Ventas | 4 | 21 | 1 | 3 |
| EP-05 — Transferencias | 6 | 34 | 5 | 1 |
| EP-06 — Logística | 3 | 13 | 1 | 2 |
| EP-07 — Analítica | 3 | 21 | 2 | 1 |
| EP-08 — Alertas Inteligentes | 2 | 11 | 0 | 2 |
| **Total** | **31** | **162** | **18** | **13** |

### 12.1. Orden de Implementación Sugerido

La secuencia responde a **dependencias de dominio**, no a preferencias de comodidad: no es posible vender sin inventario, ni transferir sin stock previamente ingresado.

1. **EP-01** — Seguridad: precondición transversal de todo el sistema.
2. **EP-02** — Inventario y Kardex: núcleo del que dependen todos los demás módulos.
3. **EP-03** — Compras: única vía de ingreso valorizado de stock.
4. **EP-04** — Ventas: primera fuente de salida de stock y de datos analíticos.
5. **EP-05** — Transferencias: requiere inventario consolidado en más de una sucursal.
6. **EP-06** — Logística: se apoya sobre transferencias existentes.
7. **EP-07** — Analítica: requiere datos históricos de ventas y movimientos.
8. **EP-08** — Alertas: se alimenta de umbrales, movimientos y transferencias ya operativos.
