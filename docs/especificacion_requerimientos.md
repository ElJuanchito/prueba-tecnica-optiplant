# Especificación de Requerimientos del Sistema (SRS)
## Sistema de Gestión de Inventario Multi-Sucursal — OptiPlant Consultores
### Cumplimiento: Sección 6.1 — Levantamiento de Requerimientos

| Versión | Fecha | Cambios |
| :--- | :--- | :--- |
| 1.0 | 2026-08-25 | Versión inicial: requerimientos funcionales, no funcionales, restricciones, supuestos y dependencias. |
| 1.3 | 2026-08-27 | Se elimina de la categoría 5.7 el tercer requerimiento de mantenibilidad, «Consistencia de Estilo». Exigía un estándar de formato y análisis estático verificable en la construcción sin que ninguna decisión de arquitectura lo materializara, y se resolvió retirarlo en lugar de sostenerlo como deuda. La categoría conserva sus dos requerimientos restantes. Su identificador no se reasigna. |
| 1.2 | 2026-08-26 | Se resuelve OI-01: se incorpora al modelo de datos la entidad de listas de precios (`price_lists`, `price_list_items`), lo que habilita RF-VEN-03. Se agregan las reglas RN-16 y RN-17. |
| 1.1 | 2026-08-26 | Se incorpora el módulo de Seguridad e Identidad y el de Integración Externa; se centralizan las reglas de negocio; se agregan las categorías de RNF de disponibilidad, observabilidad, mantenibilidad y documentación de API; se define la volumetría de referencia; se suman glosario, alcance excluido, stakeholders y priorización. |

---

## 1. Introducción

### 1.1. Propósito del Documento

El presente documento define la especificación formal de requerimientos para el **Sistema de Gestión de Inventario Multi-Sucursal**, diseñado para permitir que múltiples sucursales de una organización gestionen su inventario con autonomía operativa local, manteniendo sincronización, visibilidad centralizada y coherencia de datos a nivel corporativo.

El diseño del sistema responde al principio rector de ingeniería: *toda decisión técnica y de modelado debe estar formalmente justificada*.

### 1.2. Alcance Incluido

El sistema cubre el ciclo operativo completo del inventario de una red de sucursales: catálogo maestro de productos, existencias por sucursal, abastecimiento mediante órdenes de compra, salidas comerciales por venta, traslados de mercancía entre nodos de la red, seguimiento logístico de esos traslados, analítica operativa y un motor de alertas.

### 1.3. Alcance Excluido (*Out of Scope*)

Declarar explícitamente lo que el sistema **no** hace evita expectativas implícitas y delimita el criterio de evaluación:

| Fuera de alcance | Motivo |
| :--- | :--- |
| Facturación fiscal y timbrado electrónico | Requiere integración con autoridad tributaria y normativa por país; el sistema emite comprobantes internos, no documentos fiscales. |
| Contabilidad general y asientos contables | Dominio distinto; el sistema valoriza inventario pero no lleva libro mayor. |
| Nómina, recursos humanos y comisiones de venta | Fuera del dominio de inventario. |
| Comercio electrónico y catálogo público | El sistema es de uso interno para operadores de sucursal. |
| Multimoneda y conversión cambiaria | Se asume una moneda base única (ver supuesto SUP-02). |
| Multiempresa (*multi-tenant*) | El sistema modela **una** organización con múltiples sucursales, no múltiples organizaciones aisladas. |
| Gestión de lotes, series y fechas de caducidad | El movimiento por daño o caducidad se registra, pero no existe trazabilidad por lote individual. |
| Aplicación móvil nativa | La interfaz es web responsiva (ver RNF-USA-01). |

### 1.4. Partes Interesadas (*Stakeholders*)

| Interesado | Interés principal en el sistema |
| :--- | :--- |
| Dirección de la organización | Visibilidad consolidada del inventario y del desempeño comparado entre sucursales. |
| Gerente de sucursal | Control operativo, aprobación de transferencias y compras, cumplimiento de metas locales. |
| Operador de inventario | Rapidez y confiabilidad en la operación diaria de ingresos, salidas y traslados. |
| Área de auditoría y control interno | Trazabilidad íntegra e inalterable de todo movimiento de existencias. |
| Área de sistemas | Operabilidad, despliegue reproducible y mantenibilidad de la solución. |
| Proveedores y transportistas | Actores externos al sistema; su desempeño se mide dentro de él. |

---

## 2. Glosario de Términos del Dominio

| Término | Definición |
| :--- | :--- |
| **SKU** (*Stock Keeping Unit*) | Código único e irrepetible que identifica un artículo del catálogo maestro. Un mismo SKU representa el mismo artículo físico en todas las sucursales. |
| **Kardex** | Registro histórico e inmutable de todos los movimientos de existencias. Cada fila documenta un ingreso o una salida con su stock previo y resultante. |
| **CPP** (Costo Promedio Ponderado) | Método de valorización de inventario que recalcula el costo unitario promedio tras cada ingreso valorizado, ponderando cantidades y costos. |
| **Stock disponible** | Existencia física en la sucursal, libre de compromisos, apta para la venta. |
| **Stock reservado** | Existencia física comprometida por una operación en curso; no es vendible. |
| **Stock en tránsito** | Mercancía despachada por una sucursal origen y aún no recibida por la sucursal destino. No es vendible en ninguna de las dos. |
| **Sucursal origen** | En una transferencia, la sucursal que posee la mercancía y la despacha. |
| **Sucursal destino** | En una transferencia, la sucursal que solicita la mercancía y la recibe. |
| **Discrepancia** | Diferencia entre la cantidad despachada por la sucursal origen y la efectivamente recibida por la sucursal destino. |
| **Merma** | Pérdida de existencias por daño, deterioro, caducidad o descarte, registrada como salida sin contrapartida comercial. |
| **Movimiento** | Unidad atómica de cambio de existencias registrada en el Kardex, siempre asociada a un producto, una sucursal, un motivo y un responsable. |
| **Umbral de stock mínimo** | Cantidad parametrizable por producto y sucursal por debajo de la cual el sistema emite una alerta de reabastecimiento. |
| **Rotación** | Velocidad a la que un producto se consume en un período; base del análisis de alta y baja demanda. |
| **Recepción parcial** | Confirmación de arribo en la que la cantidad recibida es menor a la despachada, generando una discrepancia. |

---

## 3. Requerimientos Funcionales (RF)

Los requerimientos funcionales se encuentran organizados por módulos de dominio y numerados con identificadores unívocos para garantizar su trazabilidad en etapas posteriores de diseño, modelado (Casos de Uso, E-R) e implementación.

### 3.1. Módulo 0: Seguridad, Identidad y Administración

> Este módulo agrupa capacidades que el sistema **ejecuta** y que, por tanto, constituyen requerimientos funcionales. Las políticas de control de acceso que las gobiernan se especifican por separado en los RNF de seguridad (sección 5.4).

| ID | Nombre | Descripción |
| :--- | :--- | :--- |
| **RF-SEG-01** | Autenticación de Usuarios | El sistema debe permitir a un usuario autenticarse mediante credenciales y obtener una sesión que porte su identidad, su rol y su sucursal de contexto; debe además permitir el cierre de sesión y expirar sesiones inactivas. |
| **RF-SEG-02** | Gestión de Usuarios y Roles | El sistema debe permitir registrar, editar, deshabilitar y consultar usuarios, asignándoles un rol (`ADMIN`, `BRANCH_MANAGER`, `OPERATOR`) y una sucursal de pertenencia. |
| **RF-SEG-03** | Gestión de Sucursales | El sistema debe permitir registrar, editar, deshabilitar y consultar las sucursales de la red, cada una con código único, nombre y ubicación. |
| **RF-SEG-04** | Consulta de Bitácora de Auditoría | El sistema debe permitir consultar y filtrar la bitácora de eventos sensibles por usuario, sucursal, entidad afectada, acción y rango de fechas. |

### 3.2. Módulo 1: Gestión de Inventario y Catálogo (Core)

| ID | Nombre | Descripción |
| :--- | :--- | :--- |
| **RF-INV-01** | Catálogo de Productos | El sistema debe permitir registrar, actualizar, consultar y deshabilitar productos (CRUD), incluyendo código SKU, nombre, descripción, categoría y unidad(es) de medida. |
| **RF-INV-02** | Unidades de Medida Múltiples | El sistema debe permitir asociar y convertir múltiples unidades de medida por producto (ej. unidad, caja, pallet, kilogramo, litro). |
| **RF-INV-03** | Consulta de Stock Local | Cada sucursal debe visualizar en tiempo real el stock disponible, stock comprometido/reservado y stock en tránsito de sus propios productos. |
| **RF-INV-04** | Visibilidad de Red Multi-Sucursal | El sistema debe permitir a cualquier sucursal consultar la disponibilidad de stock en las demás sucursales de la red en tiempo real o *near-real-time*. |
| **RF-INV-05** | Registro de Movimientos de Entrada | El sistema debe registrar ingresos de inventario por compras, devoluciones de clientes y ajustes positivos de auditoría. |
| **RF-INV-06** | Registro de Movimientos de Salida | El sistema debe registrar salidas de inventario por ventas, mermas, descarte por daño/caducidad y ajustes negativos. |
| **RF-INV-07** | Control de Stock Mínimo y Alertas | El sistema debe permitir parametrizar umbrales de stock mínimo por producto/sucursal y emitir alertas visuales o notificaciones cuando el stock alcance o caiga por debajo de dicho umbral. |
| **RF-INV-08** | Trazabilidad y Auditoría de Movimientos (Kardex) | Cada modificación física o lógica del inventario debe persistir obligatoriamente: identificador de producto, sucursal, tipo de movimiento, cantidad, motivo, fecha/hora y usuario responsable. |

### 3.3. Módulo 2: Compras y Reabastecimiento

| ID | Nombre | Descripción |
| :--- | :--- | :--- |
| **RF-COM-01** | Gestión de Órdenes de Compra | El sistema debe permitir generar, editar, cancelar y consultar órdenes de compra a proveedores especificando productos, cantidades, precios unitarios pactados, descuentos y plazos de pago. |
| **RF-COM-02** | Recepción de Mercancía e Incremento de Stock | Al confirmar la recepción física total o parcial de una orden de compra, el sistema debe actualizar automáticamente el stock disponible en la sucursal receptora. |
| **RF-COM-03** | Historial de Compras | El sistema debe mantener un histórico detallado de compras filtrable por proveedor, producto, sucursal y rango de fechas. |
| **RF-COM-04** | Cálculo de Costo Promedio Ponderado (CPP) | El sistema debe recalcular automáticamente el costo unitario promedio ponderado del inventario tras cada ingreso de mercadería valorizado por compra. |
| **RF-COM-05** | Aprobación de Órdenes de Compra | El sistema debe soportar el ciclo de estados de la orden (`PENDING` → `APPROVED` → `RECEIVED` / `PARTIALLY_RECEIVED` / `CANCELLED`), permitiendo que un perfil autorizado apruebe o cancele una orden antes de habilitar su recepción. |
| **RF-COM-06** | Gestión de Proveedores | El sistema debe permitir registrar, editar, deshabilitar y consultar proveedores con sus datos de contacto y condiciones comerciales, y asociarlos a las órdenes de compra. |

### 3.4. Módulo 3: Ventas y Salidas Comerciales

| ID | Nombre | Descripción |
| :--- | :--- | :--- |
| **RF-VEN-01** | Registro de Transacciones de Venta | El sistema debe registrar ventas comerciales capturando productos, cantidades, precios unitarios, descuentos aplicados, impuestos, fecha, responsable y sucursal emisora. |
| **RF-VEN-02** | Validación de Disponibilidad de Stock | El sistema debe validar que la sucursal posea stock disponible suficiente antes de confirmar cualquier venta, bloqueando la operación si el stock es insuficiente (prevención de stock negativo). |
| **RF-VEN-03** | Listas de Precios y Descuentos | El sistema debe administrar múltiples listas de precios (minorista, mayorista, institucional), cada una con su precio vigente por producto —corporativo o con excepción por sucursal— y su tope máximo de descuento. Al registrar una venta debe precargar el precio vigente resuelto y rechazar todo descuento que supere el tope de la lista aplicada. |
| **RF-VEN-04** | Comprobantes y Registro de Venta | El sistema debe generar un comprobante/resumen digital de la transacción con identificador único y permitir su consulta posterior. |
| **RF-VEN-05** | Anulación de Venta | El sistema debe permitir anular una venta confirmada, reintegrando el stock a la sucursal mediante un movimiento de reversión en el Kardex, sin eliminar el movimiento original. |
| **RF-VEN-06** | Gestión de Clientes | El sistema debe permitir registrar, editar, deshabilitar y consultar clientes con sus datos de contacto e identificación tributaria, asociarlos opcionalmente a las ventas comerciales y consultar su histórico de compras. |

> **Soporte de modelo de datos (RF-VEN-03):** resuelto en la versión 1.2. El modelo incorpora `price_lists` (lista con su tope de descuento) y `price_list_items` (precio por producto, con excepción opcional por sucursal y vigencia acotada por `valid_from` / `valid_to`). La venta registra la lista aplicada y cada ítem congela el precio de lista del momento, haciendo auditable el descuento. Ver [`diagrama_er.md`](./diagrama_er.md), sección 1.1.

### 3.5. Módulo 4: Transferencias entre Sucursales

| ID | Nombre | Descripción |
| :--- | :--- | :--- |
| **RF-TRA-01** | Solicitud de Transferencia | Una sucursal destino (o Administrador) debe poder crear una solicitud formal de traslado indicando sucursal origen, producto, cantidad requerida y nivel de prioridad/urgencia. |
| **RF-TRA-02** | Aprobación y Preparación de Envío | La sucursal origen debe poder evaluar la solicitud, validar su stock disponible, aprobar (total o parcialmente) o rechazar la solicitud y marcar el pedido en estado *En Preparación*. |
| **RF-TRA-03** | Despacho y Stock en Tránsito | Al registrar el despacho, el sistema debe descontar el stock de la sucursal origen y catalogarlo como *Stock en Tránsito*, registrando transportista y fecha/hora estimada de entrega. |
| **RF-TRA-04** | Confirmación de Recepción Completa | Al recibir la carga completa en destino, el sistema debe registrar el ingreso físico en el inventario de la sucursal destino y cerrar la transferencia como *Completada*. |
| **RF-TRA-05** | Gestión de Recepción Parcial y Discrepancias | Si la cantidad recibida difiere de la despachada, el sistema debe registrar los faltantes/daños, ingresar solo lo efectivamente recibido, generar una alerta de discrepancia y habilitar acciones de reclamación, reenvío o ajuste de merma. |
| **RF-TRA-06** | Cancelación de Transferencia | El sistema debe permitir cancelar una transferencia que aún no ha sido despachada (estados `REQUESTED` o `IN_PREPARATION`), sin alterar saldo de stock alguno, e impedir la cancelación una vez la mercancía está en tránsito. |

### 3.6. Módulo 5: Logística y Tiempos de Envío

| ID | Nombre | Descripción |
| :--- | :--- | :--- |
| **RF-LOG-01** | Monitoreo de Estados de Transferencia | El sistema debe clasificar y exponer el estado en tiempo real de cada transferencia: `Solicitada`, `En Preparación`, `En Tránsito`, `Recibida`, `Recibida con Faltantes`, `Cancelada`. |
| **RF-LOG-02** | Control de Tiempos Estimados vs. Reales | El sistema debe registrar la fecha/hora estimada de llegada y contrastarla contra la fecha/hora real de confirmación de recepción para calcular desviaciones. |
| **RF-LOG-03** | Clasificación de Rutas | El sistema debe permitir parametrizar y clasificar rutas entre pares de sucursales según prioridad, costo de transporte o tiempo promedio de traslado. |
| **RF-LOG-04** | Reportes de Cumplimiento Logístico | El sistema debe generar métricas de puntualidad y cumplimiento logístico por sucursal de origen, destino y ruta de traslado. |

### 3.7. Módulo 6: Dashboard y Análisis Visual

| ID | Nombre | Descripción |
| :--- | :--- | :--- |
| **RF-DSH-01** | Métricas de Ventas Locales e Históricas | El dashboard de sucursal debe presentar el volumen de ventas del mes en curso contrastado con periodos anteriores. |
| **RF-DSH-02** | Rotación y Demanda de Inventario | Visualización analítica de productos con mayor y menor rotación (análisis ABC / Pareto) por sucursal y global. |
| **RF-DSH-03** | Monitor de Transferencias Activas | Visualización del impacto de traslados en curso sobre el stock disponible y proyectado. |
| **RF-DSH-04** | Indicadores de Reabastecimiento Crítico | Visualización destacada de productos agotados o próximos a alcanzar su stock mínimo. |
| **RF-DSH-05** | Vista Corporativa Consolidada | Los administradores generales deben contar con un tablero global para comparar el desempeño operativo y de ventas entre todas las sucursales de la organización. |

### 3.8. Módulo 7: Integración con Sistemas Externos (Opcional)

| ID | Nombre | Descripción |
| :--- | :--- | :--- |
| **RF-EXT-01** | Consulta de Disponibilidad vía API | El sistema debe exponer un endpoint autenticado que permita a un sistema externo (ERP o POS) consultar la disponibilidad consolidada de un producto en la red, utilizando exclusivamente identificadores públicos. |
| **RF-EXT-02** | Registro de Venta desde Sistema Externo | El sistema debe permitir que un punto de venta externo notifique una venta a través de la API, aplicando exactamente las mismas validaciones de stock, cálculo y registro en Kardex que una venta registrada desde la interfaz propia. |

### 3.9. Módulo 8: Funcionalidad de Valor Agregado (Propuesta Técnica)

| ID | Nombre | Descripción |
| :--- | :--- | :--- |
| **RF-VAL-01** | Sistema Inteligente de Alertas y Notificaciones | Motor centralizado de eventos y notificaciones (UI/toast y registro persistente) para stock crítico, demoras en traslados logísticos y discrepancias en recepciones. |
| **RF-VAL-02** | Auditoría y Registro de Eventos de Seguridad | Bitácora inmutable de eventos sensibles del sistema (cambios de precios, ajustes manuales de stock, anulaciones de órdenes). |

### 3.10. Priorización de Requerimientos Funcionales (MoSCoW)

| Prioridad | Requerimientos | Criterio |
| :--- | :--- | :--- |
| **Must** | RF-SEG-01, RF-SEG-02, RF-SEG-03, RF-INV-01 … RF-INV-08, RF-COM-01, RF-COM-02, RF-COM-04, RF-VEN-01, RF-VEN-02, RF-VEN-04, RF-TRA-01 … RF-TRA-05, RF-LOG-01, RF-DSH-01, RF-DSH-02, RF-DSH-04 | Sin ellos el sistema no cumple su objetivo: no hay inventario confiable, ni trazabilidad, ni ciclo de transferencia completo. |
| **Should** | RF-SEG-04, RF-COM-03, RF-COM-05, RF-COM-06, RF-VEN-03, RF-VEN-05, RF-VEN-06, RF-TRA-06, RF-LOG-02, RF-LOG-03, RF-LOG-04, RF-DSH-03, RF-DSH-05, RF-VAL-01, RF-VAL-02 | Aportan valor operativo y de control significativos, pero el núcleo funciona sin ellos. |
| **Could** | RF-EXT-01, RF-EXT-02 | El propio enunciado los declara opcionales; se diseñan los contratos aunque no se integre un sistema real. |

---

## 4. Reglas de Negocio (RN)

Las reglas de negocio son invariantes del dominio que **ningún caso de uso puede violar**. Se numeran de forma centralizada porque atraviesan múltiples requerimientos y casos de uso; los casos de uso las referencian por identificador.

| ID | Regla | Requerimientos que la aplican |
| :--- | :--- | :--- |
| **RN-01** | Ninguna venta, ajuste o despacho puede dejar el stock físico en valores negativos. | RF-VEN-02, RF-INV-05, RF-INV-06, RF-TRA-03 |
| **RN-02** | Toda mutación de existencias genera obligatoriamente un asiento en el Kardex; no existe cambio de stock sin traza. | RF-INV-08 |
| **RN-03** | El costo unitario registrado en un movimiento de salida es el Costo Promedio Ponderado vigente en ese instante, nunca el precio de venta. | RF-COM-04, RF-VEN-01 |
| **RN-04** | El stock en tránsito no es vendible en la sucursal destino hasta la confirmación de recepción. | RF-TRA-03, RF-TRA-04 |
| **RN-05** | Ninguna transferencia puede saltar estados: el flujo `REQUESTED → IN_PREPARATION → IN_TRANSIT → RECEIVED / RECEIVED_WITH_DISCREPANCY` es obligatorio. | RF-TRA-01 … RF-TRA-05, RF-LOG-01 |
| **RN-06** | En toda recepción, la cantidad recibida más la discrepancia registrada debe igualar exactamente la cantidad despachada. | RF-TRA-05 |
| **RN-07** | Toda discrepancia de recepción genera obligatoriamente una alerta persistente; nunca se resuelve de forma silenciosa. | RF-TRA-05, RF-VAL-01 |
| **RN-08** | La consulta de inventario de otra sucursal es estrictamente de lectura; ningún actor que no sea administrador general puede mutar inventario ajeno. | RF-INV-04, RNF-SEC-03 |
| **RN-09** | La agregación de inventario global no debe degradar el rendimiento transaccional local de ninguna sucursal. | RF-INV-04, RNF-PER-03 |
| **RN-10** | El Costo Promedio Ponderado se recalcula exclusivamente ante ingresos valorizados, aplicando `CPP = ((stock × CPP) + (cantidad × costo)) / (stock + cantidad)`. | RF-COM-04 |
| **RN-11** | Todo ajuste manual de inventario exige un motivo explícito y queda restringido a los roles `BRANCH_MANAGER` y `ADMIN`. | RF-INV-05, RF-INV-06 |
| **RN-12** | Los registros de Kardex, auditoría y ventas nunca se eliminan físicamente; las bajas son siempre lógicas. | RF-INV-08, RF-VAL-02, RF-VEN-05 |
| **RN-13** | Los saldos de existencias se almacenan y operan siempre en la unidad base del producto; las unidades alternativas se convierten al ingresar. | RF-INV-02 |
| **RN-14** | La sucursal sobre la que actúa una operación se deriva siempre de la sesión autenticada, nunca de un parámetro enviado por el cliente. | RF-SEG-01, RNF-SEC-03 |
| **RN-15** | Una orden de compra solo admite recepción de mercancía en estado `APPROVED` o `PARTIALLY_RECEIVED`. | RF-COM-02, RF-COM-05 |
| **RN-16** | El precio de venta se resuelve dentro de la lista aplicada dando prioridad al precio específico de la sucursal sobre el precio corporativo, y tomando siempre el vigente a la fecha de la operación. | RF-VEN-01, RF-VEN-03 |
| **RN-17** | El descuento aplicado a un ítem nunca puede superar el tope de la lista de precios utilizada, ni el precio aplicado puede exceder el precio de lista vigente. | RF-VEN-03 |

---

## 5. Requerimientos No Funcionales (RNF)

### 5.1. Volumetría y Carga de Referencia

Los objetivos de rendimiento de la sección 5.2 solo son verificables contra una carga definida. Se establece la siguiente volumetría de referencia como base de las pruebas de aceptación:

| Parámetro | Valor de referencia |
| :--- | :--- |
| Sucursales en la red | 10 (escalable a 50 sin cambio de esquema, ver RNF-ESC-02) |
| Usuarios registrados | 150 |
| Usuarios concurrentes en hora pico | 50 |
| Productos en el catálogo maestro | 10 000 |
| Registros de inventario (producto × sucursal) | 100 000 |
| Transacciones de venta por día | 5 000 |
| Movimientos de Kardex acumulados a 2 años | 5 000 000 |
| Transferencias activas simultáneas | 200 |

### 5.2. Rendimiento y Eficiencia (Performance)
* **RNF-PER-01 (Tiempo de Respuesta de API):** El 95% de las solicitudes de consulta (lecturas) a través de la API deben responder en menos de 200 ms bajo la carga de referencia definida en la sección 5.1.
* **RNF-PER-02 (Transacciones de Inventario):** Las operaciones de mutación crítica (registro de ventas, transferencias y recepción de compras) deben ejecutarse de forma atómica en menos de 500 ms bajo la misma carga de referencia.
* **RNF-PER-03 (Consultas Cross-Branch):** La agregación de inventario global multi-sucursal no debe degradar el rendimiento de las operaciones transaccionales locales de cada nodo.
* **RNF-PER-04 (Paginación Obligatoria):** Todo endpoint que devuelva colecciones potencialmente ilimitadas (Kardex, ventas, movimientos, auditoría) debe exponer paginación y un tamaño de página máximo, evitando respuestas de volumen no acotado.

### 5.3. Integridad de Datos y Concurrencia
* **RNF-INT-01 (Transaccionalidad ACID):** Toda operación que altere saldos de stock (ventas, transferencias, ajustes) debe ejecutarse bajo transacciones atómicas de base de datos para evitar inconsistencias o sobreventas (evitar condiciones de carrera / *race conditions*).
* **RNF-INT-02 (Inmutabilidad de Auditoría):** Los registros del Kardex y eventos de auditoría no deben admitir mutación ni eliminación física (`soft deletes` y tablas *append-only* para movimientos).
* **RNF-INT-03 (Última Línea de Defensa en el Esquema):** Las invariantes críticas de negocio (no negatividad de existencias, rangos de descuento, unicidad de SKU y de códigos de sucursal) deben estar además garantizadas por restricciones declarativas en la base de datos, independientemente de la validación aplicativa.

### 5.4. Seguridad, Autenticación y Autorización
* **RNF-SEC-01 (Control de Acceso Basado en Roles - RBAC):** El sistema debe implementar control de acceso estricto distinguiendo al menos: *Administrador General*, *Gerente de Sucursal* y *Operador de Inventario*.
* **RNF-SEC-02 (Autenticación Segura):** Toda comunicación con la API debe estar autenticada mediante tokens seguros (JWT / Bearer Token o sesiones seguras) y las contraseñas deben estar cifradas mediante algoritmos robustos (ej. Argon2 o BCrypt con salt).
* **RNF-SEC-03 (Aislamiento de Sucursal por Contexto):** Un operador o gerente de sucursal solo debe tener privilegios de mutación sobre su propia sucursal, manteniendo visibilidad de solo lectura sobre el resto de las sucursales.
* **RNF-SEC-04 (Cifrado en Tránsito):** Toda comunicación entre cliente y API debe viajar sobre TLS en entornos distintos al de desarrollo local; el sistema no debe aceptar credenciales por canales no cifrados.
* **RNF-SEC-05 (Validación de Entrada y Superficie de Ataque):** Todo dato proveniente del cliente debe validarse en el backend antes de alcanzar la capa de dominio. El sistema debe mitigar las categorías del OWASP Top 10 aplicables: inyección (uso exclusivo de sentencias parametrizadas), *broken access control* (verificación de propiedad de recurso en cada operación) y exposición de identificadores internos (uso exclusivo de `external_id` público).
* **RNF-SEC-06 (Política de Origen Cruzado y Limitación de Tasa):** La API debe declarar una política CORS restringida a los orígenes autorizados y aplicar limitación de tasa sobre los endpoints de autenticación para mitigar ataques de fuerza bruta.
* **RNF-SEC-07 (Gestión de Secretos):** Ninguna credencial, clave de firma o cadena de conexión debe residir en el código fuente ni en el repositorio; se inyectan exclusivamente por variables de entorno.
* **RNF-SEC-08 (Retención de Auditoría):** Los registros de auditoría y Kardex deben conservarse durante un mínimo de 5 años, acorde a las exigencias habituales de control interno.

### 5.5. Disponibilidad y Recuperación
* **RNF-DIS-01 (Autonomía Operativa Local):** La indisponibilidad de una sucursal, o de la conectividad hacia ella, no debe impedir que las demás sucursales continúen operando sus transacciones locales.
* **RNF-DIS-02 (Respaldo de Datos):** La base de datos debe admitir respaldo completo periódico y restauración verificable; el objetivo de punto de recuperación (RPO) es de 24 horas y el objetivo de tiempo de recuperación (RTO) es de 4 horas.
* **RNF-DIS-03 (Arranque Reproducible):** El entorno completo debe reconstruirse desde cero mediante los scripts de inicialización y semillas versionados, sin intervención manual.

### 5.6. Observabilidad
* **RNF-OBS-01 (Registro Estructurado):** El backend debe emitir logs estructurados que incluyan identificador de correlación, usuario, sucursal y operación, sin registrar jamás credenciales ni datos sensibles.
* **RNF-OBS-02 (Sondas de Salud):** Cada servicio debe exponer un endpoint de estado que permita verificar su disponibilidad y la de sus dependencias, apto para el `healthcheck` de Docker Compose.
* **RNF-OBS-03 (Métricas Operativas):** El sistema debe exponer métricas de latencia, tasa de error y volumen por endpoint, habilitando la verificación empírica de los objetivos de la sección 5.2.

### 5.7. Mantenibilidad y Calidad del Código
* **RNF-MAN-01 (Cobertura de Pruebas del Dominio):** La lógica de negocio crítica (cálculo de CPP, validación de stock, máquina de estados de transferencias, autorización por rol) debe estar cubierta por pruebas automatizadas, con un objetivo mínimo del 80% de cobertura en la capa de dominio.
* **RNF-MAN-02 (Verificación de Fronteras Arquitectónicas):** La separación entre módulos de negocio y entre capas debe verificarse mediante pruebas de arquitectura automatizadas, no únicamente por convención.

### 5.8. Escalabilidad y Arquitectura
* **RNF-ESC-01 (Arquitectura Desacoplada):** El backend debe ser modular (Clean Architecture / Hexagonal) permitiendo desacoplar la lógica de dominio de los adaptadores de infraestructura y base de datos.
* **RNF-ESC-02 (Escalabilidad de Sucursales):** El modelo de datos y la arquitectura deben permitir incorporar nuevas sucursales u operadores sin requerir modificaciones de esquema ni reestructuraciones de código.
* **RNF-ESC-03 (Ausencia de Estado en el Servicio):** El backend no debe mantener estado de sesión en memoria, de modo que sea posible ejecutar varias instancias tras un balanceador sin afinidad de sesión.

### 5.9. Usabilidad y Experiencia de Usuario (UX/UI)
* **RNF-USA-01 (Diseño Responsivo e Intuitivo):** La interfaz web debe ser responsiva y adaptarse fluidamente a dispositivos de escritorio y tablets utilizados en estaciones de inventario.
* **RNF-USA-02 (Feedback Visual Inmediato):** Los formularios críticos (ventas, transferencias) deben contar con validaciones reactivas en cliente y mensajes de error claros provenientes de la API.
* **RNF-USA-03 (Accesibilidad):** La interfaz debe cumplir los criterios de nivel AA de las WCAG 2.1 aplicables: contraste suficiente, navegación completa por teclado, etiquetado accesible de formularios y foco visible.
* **RNF-USA-04 (Idioma y Formato Regional):** La interfaz se presenta en español, con formatos de fecha, número y moneda consistentes en toda la aplicación.

### 5.10. Portabilidad y Despliegue
* **RNF-CON-01 (Contenedorización Total):** Todos los componentes (Frontend, Backend, Base de Datos) deben estar contenerizados mediante imágenes estándar de Docker y orquestados a través de `compose.yml`, el nombre canónico de Compose V2 (el enunciado lo nombra `docker-compose.yml`, forma heredada de la V1; ambas las resuelve `docker compose up`).
* **RNF-CON-02 (Configuración Externalizada):** Toda configuración dependiente del entorno debe inyectarse por variables de entorno con valores por defecto operativos, de modo que `docker compose up` levante un sistema funcional sin edición previa de archivos.

### 5.11. Documentación e Interfaz de Programación
* **RNF-API-01 (Contrato Documentado):** La API debe publicar una especificación OpenAPI navegable que documente cada endpoint, sus parámetros, sus códigos de respuesta y sus estructuras de error.
* **RNF-API-02 (Semántica REST Consistente):** La API debe emplear los verbos y códigos de estado HTTP de forma coherente, exponer errores con una estructura uniforme y utilizar exclusivamente identificadores públicos (`external_id`) en rutas y payloads.

---

## 6. Restricciones Técnicas y de Negocio

### 6.1. Restricciones Técnicas
1. **Arquitectura en 3 Capas Independientes:** Separación estricta entre capa de presentación (Frontend SPA), capa de aplicación/negocio (Backend API) y capa de persistencia (Base de Datos).
2. **Comunicación Exclusiva por API:** Toda la lógica de negocio, validaciones y cálculos deben residir exclusivamente en el backend. El frontend es un cliente que consume endpoints RESTful estructurados con payloads JSON y códigos HTTP estándar.
3. **Despliegue con un Solo Comando:** El sistema completo debe iniciar y quedar operativo ejecutando únicamente `docker compose up` (o `docker-compose up`), sin requerir configuraciones manuales previas en el sistema anfitrión.
4. **Persistencia Relacional Transaccional:** La integridad financiera y de stock requiere un motor relacional con soporte transaccional ACID robusto (PostgreSQL).

### 6.2. Restricciones de Negocio
1. **No Permitir Stock Negativo:** Ninguna venta, ajuste o despacho de transferencia puede dejar el stock físico en valores negativos (RN-01).
2. **Autonomía Operativa Local vs. Visibilidad Global:** Cada sucursal opera sus transacciones diarias con independencia; los bloqueos o problemas de otra sucursal no deben frenar la venta local (RNF-DIS-01).
3. **Flujo Obligatorio de Transferencia:** Ninguna transferencia puede pasar directamente de origen a destino sin haber transitado los estados formales de solicitud, despacho (*in-transit*) y confirmación de recepción (RN-05).
4. **Cálculo de Costos Estándar:** La valoración de existencias debe regirse estrictamente por el método de Costo Promedio Ponderado (RN-10).
5. **Responsabilidad Nominal de Todo Movimiento:** Ningún movimiento de existencias puede quedar registrado sin un usuario responsable identificado (RN-02).

---

## 7. Supuestos y Dependencias del Sistema

### 7.1. Supuestos (Assumptions)
1. **SUP-01 — Conectividad de Red Estable:** Se asume que las sucursales cuentan con conectividad a Internet o red corporativa para comunicarse con los servicios del backend en tiempo real.
2. **SUP-02 — Moneda y Zona Horaria:** Las transacciones se asumen en una moneda base compartida por la organización y los timestamps se almacenan estandarizados en formato UTC a nivel de base de datos.
3. **SUP-03 — Catálogo Maestro Homogéneo:** Los productos, códigos SKU y categorías son administrados de forma centralizada o compartida, garantizando que un mismo SKU represente el mismo artículo físico en todas las sucursales.
4. **SUP-04 — Usuario Autenticado por Estación:** Se asume que cada terminal de trabajo u operador cuenta con credenciales únicas para garantizar la validez del log de auditoría.
5. **SUP-05 — Régimen Impositivo Simplificado:** Se asume una tasa impositiva única y parametrizable sobre la venta; el sistema no modela regímenes fiscales diferenciados por producto o jurisdicción.
6. **SUP-06 — Transportistas como Actores Externos:** Los transportistas no son usuarios del sistema; su identificación y desempeño se registran como datos de la transferencia.

### 7.2. Dependencias del Sistema (Dependencies)
1. **DEP-01 — Entorno de Ejecución:** Dependencia de Docker Engine (>= 20.10) y Docker Compose (>= v2) en el entorno de evaluación o servidor de despliegue.
2. **DEP-02 — Motor de Base de Datos:** Base de datos relacional (PostgreSQL 17) con scripts de migración y seeders automáticos ejecutados al iniciar los contenedores.
3. **DEP-03 — Navegadores Modernos:** El frontend depende de navegadores web compatibles con estándares modernos ECMAScript / HTML5 / CSS3 (Chrome, Firefox, Safari, Edge) en sus dos últimas versiones estables.
4. **DEP-04 — Integraciones Externas Futuras (Opcionales):** Preparación de contratos API para posibles integraciones con puntos de venta (POS) o ERP corporativo (RF-EXT-01, RF-EXT-02).
5. **DEP-05 — Reloj Sincronizado:** La validez del cálculo de tiempos logísticos y del orden del Kardex depende de que los servidores mantengan la hora sincronizada.

---

## 8. Trazabilidad

La trazabilidad del sistema se sostiene sobre tres documentos encadenados, de modo que ningún requerimiento quede sin materializar y ninguna funcionalidad exista sin requerimiento que la respalde:

| Desde | Hacia | Documento |
| :--- | :--- | :--- |
| Requerimiento funcional (RF) | Caso de uso (CU) | [`casos_de_uso.md`](./casos_de_uso.md) — sección 6, matriz de trazabilidad |
| Caso de uso (CU) | Historia de usuario (HU) y criterios de aceptación | [`historias_de_usuario.md`](./historias_de_usuario.md) |
| Requerimiento no funcional (RNF) | Decisión de arquitectura justificada | [`decisiones_arquitectura_tecnica.md`](./decisiones_arquitectura_tecnica.md) |
| Regla de negocio (RN) | Restricción declarativa en el esquema | [`diagrama_er.md`](./diagrama_er.md) |
| Decisión de postergar trabajo | Ítem con plan de pago y disparador | [`deuda_tecnica.md`](./deuda_tecnica.md) |

---

## 9. Asuntos Abiertos (*Open Issues*)

Las decisiones de postergar trabajo y las limitaciones conocidas del diseño se registran en [`deuda_tecnica.md`](./deuda_tecnica.md). Esta sección conserva únicamente los asuntos originados en el levantamiento de requerimientos.

| ID | Asunto | Impacto | Estado |
| :--- | :--- | :--- | :--- |
| **OI-01** | RF-VEN-03 requería una entidad de precios de venta que el modelo no contemplaba: `products` no poseía columna de precio y no existía tabla de listas de precios. | Bloqueaba la implementación del módulo de ventas. | **Resuelto en v1.2.** Se incorporaron `price_lists` y `price_list_items` al esquema, a las tres representaciones del diagrama E-R y a los datos semilla. Verificado contra PostgreSQL 17. |
| **OI-02** | El enunciado menciona perfiles de clientes asociados a listas de precios. | Impide segmentar precios por cliente; no impide operar. | **Parcialmente resuelto.** Se incorporó la entidad `customers` con gestión CRUD, asociación opcional en ventas e histórico de compras. La segmentación de listas de precios por cliente se mantiene fuera de alcance (sección 1.3). |
