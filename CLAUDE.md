# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Proyecto

Sistema de inventario multi-sucursal (prueba técnica para OptiPlant). **Estado actual: documentación de ingeniería y capa de datos.** No existe todavía backend, frontend ni `docker-compose.yml`.

Toda la documentación está en español. Mantener ese idioma al extenderla.

## Verificación

```bash
python3 scripts/validar_trazabilidad.py   # referencias y enlaces entre documentos; sin dependencias
./scripts/validar_esquema.sh              # 19 invariantes contra PostgreSQL 17 real; requiere Docker
```

Ambos deben pasar antes de dar por terminado cualquier cambio en `docs/` o en `backend/init-db/`.

**Regla de trabajo del proyecto: no se afirma nada sin ejecutarlo.** Cada defecto grave de este repositorio apareció al ejecutar, nunca al leer. Un SQL que "se ve bien" no está verificado; un diagrama Mermaid sin renderizar tampoco.

Al levantar PostgreSQL manualmente, esperar `PostgreSQL init process complete` en los registros antes de consultar: el servidor acepta conexiones **mientras** aún corre los scripts de `init-db`, y una consulta prematura devuelve un esquema a medio crear.

## Invariantes que ya rompieron el proyecto

| Regla | Por qué |
| :--- | :--- |
| Los roles son `ADMIN`, `BRANCH_MANAGER`, `OPERATOR` — **sin prefijo `ROLE_`** | El `CHECK` de `users` los rechaza. Este error se propagó del documento de arquitectura a las semillas y rompió el arranque. Con Spring Security usar `hasAuthority()`, no `hasRole()`, que antepone el prefijo. |
| Los `external_id` de las semillas son UUID: **solo dígitos hexadecimales** | 29 literales con prefijos `p`, `r`, `s`, `t`, `u` hacían fallar la carga entera. La convención de prefijos está documentada en la cabecera de `backend/init-db/02-seed-data.sql`. |
| Toda mutación de stock escribe su movimiento en el Kardex **en la misma transacción** | El saldo es una proyección del histórico. Sin eso, ambos quedan desalineados sin forma de reconciliarlos. |
| Los efectos atómicos van por **puerto de salida síncrono**, nunca por evento | Un evento asíncrono queda fuera de la transacción. Los eventos de dominio se usan solo en `AFTER_COMMIT` para lo que puede fallar sin revertir la operación: alertas y analítica. |
| La sucursal se deriva **de la sesión autenticada**, nunca de un parámetro del cliente | Es la frontera de aislamiento entre sucursales. |
| La API expone **solo `external_id`**, jamás los `id` numéricos internos | Evita enumeración directa de recursos. |

## Trazabilidad

Los identificadores encadenan los documentos: `RF` / `RNF` / `RN` → `CU` → `HU`.

Al agregar un requerimiento hay que agregar también su caso de uso y su fila en la matriz de trazabilidad de `docs/casos_de_uso.md`, o `scripts/validar_trazabilidad.py` falla. Lo mismo al renombrar o eliminar.

| Necesitás… | Fuente de verdad |
| :--- | :--- |
| Requerimientos, reglas de negocio `RN-xx`, alcance excluido | `docs/especificacion_requerimientos.md` |
| Justificación de una decisión técnica | `docs/decisiones_arquitectura_tecnica.md` |
| Trabajo postergado y su plan de pago | `docs/deuda_tecnica.md` |
| Modelo de datos | `docs/diagrama_er.md` + `backend/init-db/01-init-schema.sql` |

## Al montar el backend

`backend/init-db/` es hoy el mecanismo de arranque. **No agregar Flyway junto a él**: el volumen ya inicializado hace que Flyway encuentre tablas que no creó y falle. La migración es sustitución, no coexistencia — el procedimiento completo está en `DT-01` de `docs/deuda_tecnica.md`.

Los diez módulos del backend y sus responsabilidades están definidos en la sección 2.4 del documento de arquitectura; respetarlos al crear paquetes.
