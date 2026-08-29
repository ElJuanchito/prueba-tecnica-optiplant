# Plan de construcción del backend

Estado del trabajo pendiente y las decisiones de planificación que lo gobiernan. Se
actualiza al cerrar cada cambio SDD.

**Última actualización:** 2026-08-29

---

## 1. Estado actual

| Módulo | Paquete | Estado | Casos de uso |
| :--- | :--- | :--- | :--- |
| `iam` | `iam/` | Archivado | `CU-SEG-01` … `CU-SEG-04` |
| `catalog` | `catalog/` | Archivado | `CU-INV-01`, `CU-INV-02` |
| `inventory` | `inventory/` | Archivado | `CU-INV-03` … `CU-INV-08` |
| `notifications` | `notifications/` | Archivado | `CU-ALE-01`, `CU-ALE-02` |

**14 de 37 casos de uso entregados.** Cuatro paquetes de módulo de diez.

Los ciclos SDD cerrados viven en `openspec/changes/archive/`, cada uno con su contrato,
diseño, tareas, informe de verificación e informe de archivado.

---

## 2. Lo que falta

Cuatro cambios SDD que crean los seis paquetes de módulo restantes y cubren los 23
casos de uso pendientes.

| Orden | Cambio SDD | Paquetes que crea | Casos de uso |
| :--- | :--- | :--- | :--- |
| 1 | `add-transfers-module` | `transfers`, `logistics` | `CU-TRA-01` … `CU-TRA-06`, `CU-LOG-01` … `CU-LOG-03` |
| 2 | `add-sales-module` | `sales`, `pricing` | `CU-VEN-01` … `CU-VEN-04`, `CU-EXT-02` |
| 3 | `add-purchases-module` | `purchases` | `CU-COM-01` … `CU-COM-05` |
| 4 | `add-analytics-module` | `analytics` | `CU-DSH-01` … `CU-DSH-03`, `CU-EXT-01` |

### Por qué `transfers` va primero

Es el más caro y el único del sistema que no es un CRUD: la máquina de estados de cinco
pasos con recepción parcial y stock en tránsito es lo que demuestra que se entendió el
problema multi-sucursal. Se construye mientras queda más tiempo, porque si algo tiene
que sufrir por falta de margen conviene que sea una consulta de histórico y no esta
máquina de estados.

Es además el primer consumidor real de `shared/stock/StockMutationPort`. Ese contrato
lo definió `inventory` y hasta ahora no lo ejerce nadie —`StockMovementService` escribe
saldo y Kardex en línea—, así que `transfers` es quien va a forzar su implementación y
quien va a revelar si el puerto estaba bien planteado.

### Por qué `analytics` va último

Es solo lectura sobre las tablas que los tres anteriores llenaron. Construirlo al final
lo vuelve barato: son consultas agregadas, sin dominio propio ni mutación.

### Por qué `CU-EXT-01` y `CU-EXT-02` no tienen módulo propio

Son adaptadores de entrada, no funcionalidades. `CU-EXT-01` expone la consulta de
disponibilidad de red que `CU-INV-04` ya resuelve; `CU-EXT-02` expone el caso de uso de
registrar venta. Un controlador con autenticación por API key y sus DTOs, sin trabajo de
dominio. Es el pago de haber construido con puertos: un segundo adaptador primario sobre
un caso de uso que ya existe.

---

## 3. Decisiones de planificación vigentes

### Un cambio SDD puede crear dos paquetes de módulo

Los diez módulos de la sección 2.4 de `docs/decisiones_arquitectura_tecnica.md` se
construyen como diez paquetes, cada uno con sus fronteras verificadas por ArchUnit.
Pero un **cambio SDD** es una unidad de planificación, no una unidad de arquitectura:
puede crear dos paquetes afines en un solo ciclo de contrato, diseño y tareas.

`add-inventory-module` ya se hizo así, creando `inventory` y `notifications`. Reduce la
ceremonia sin fusionar módulos ni tocar la documentación de arquitectura.

### Tres slices por cambio, un pull request cada uno

1. **S1** — dominio y aplicación, con sus pruebas unitarias `*Test`.
2. **S2** — infraestructura y web: entidades JPA, mappers, repositorios, adaptadores,
   controladores y manejadores de excepción.
3. **S3** — verificación transversal: las pruebas de integración `*IT` y el cierre
   documental.

El frontend de cada módulo entra como un cuarto pull request y lo construye el autor del
proyecto por separado.

### Las pruebas de integración se reservan para las invariantes

Los `*IT` con Testcontainers cuestan tiempo de ejecución, así que se escriben sólo para
lo que puede romper el sistema: atomicidad del Kardex, aislamiento por sucursal,
validación de stock bajo concurrencia, recepción parcial. Los CRUD y las lecturas se
cubren con prueba unitaria más un `smoke` por grupo de controlador.

Esta política ya se pagó sola: el `S3` de `inventory` descubrió que la consulta del
Kardex fallaba contra PostgreSQL real siempre que se la invocaba sin rango de fechas.
Ninguna prueba unitaria podía verlo.

### Ningún caso de uso se difiere

Los 37 casos de uso del SRS se entregan. La velocidad se busca en la estrategia de
construcción —reutilizar el patrón ya establecido, acotar la ceremonia, concentrar las
pruebas— nunca recortando el alcance comprometido.

---

## 4. Referencias

| Necesitás… | Fuente de verdad |
| :--- | :--- |
| Requerimientos y reglas de negocio | `docs/especificacion_requerimientos.md` |
| Casos de uso y matriz de trazabilidad | `docs/casos_de_uso.md` |
| Justificación de una decisión técnica | `docs/decisiones_arquitectura_tecnica.md` |
| Trabajo postergado y su plan de pago | `docs/deuda_tecnica.md` |
| Contrato y diseño de un módulo ya construido | `openspec/changes/archive/` |
