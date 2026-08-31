# Plan de construcción del backend

Estado del trabajo pendiente y las decisiones de planificación que lo gobiernan. Se
actualiza al cerrar cada cambio SDD.

**Última actualización:** 2026-08-31

---

## 1. Estado actual

| Módulo | Paquete | Estado | Casos de uso |
| :--- | :--- | :--- | :--- |
| `iam` | `iam/` | Archivado | `CU-SEG-01` … `CU-SEG-04` |
| `catalog` | `catalog/` | Archivado | `CU-INV-01`, `CU-INV-02` |
| `inventory` | `inventory/` | Archivado | `CU-INV-03` … `CU-INV-08` |
| `notifications` | `notifications/` | Archivado | `CU-ALE-01`, `CU-ALE-02` |
| `transfers` | `transfers/` | Archivado | `CU-TRA-01` … `CU-TRA-06` |
| `logistics` | `logistics/` | Archivado | `CU-LOG-01` … `CU-LOG-03` |
| `sales` | `sales/` | Archivado | `CU-VEN-01`, `CU-VEN-03`, `CU-VEN-04`, `CU-VEN-05`, `CU-VEN-06`, `CU-EXT-02` |
| `pricing` | `pricing/` | Archivado | `CU-VEN-02` |
| `purchases` | `purchases/` | Archivado | `CU-COM-01` … `CU-COM-05` |
| `analytics` | `analytics/` | Archivado | `CU-DSH-01` … `CU-DSH-03`, `CU-EXT-01` |

**39 de 39 casos de uso archivados.** Diez paquetes de módulo de diez. El sub-dominio
de clientes (`CU-VEN-05`, `CU-VEN-06`) se construyó dentro de `sales` en el cambio
`add-sales-customers`, sin paquete de módulo nuevo.

Los ciclos SDD cerrados viven en `openspec/changes/archive/`, cada uno con su contrato,
diseño, tareas, informe de verificación e informe de archivado. El backend de OptiPlant
está completo: `add-analytics-module` cierra el décimo módulo.

---

## 2. Lo que falta

Nada. Los 10 módulos backend están entregados, archivados y verificados. Los 39 casos
de uso del sistema están implementados con sus pruebas de arquitectura (`ArchUnit`),
pruebas unitarias de dominio (520 `*Test`) y pruebas de integración Testcontainers
(223 `*IT`). El backend de OptiPlant está listo para producción.

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
