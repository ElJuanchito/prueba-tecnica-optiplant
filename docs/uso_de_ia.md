# Uso de Inteligencia Artificial en el Desarrollo
## Sistema de Gestión de Inventario Multi-Sucursal — OptiPlant Consultores
### Cumplimiento: Sección 9 — Uso de Inteligencia Artificial

| Versión | Fecha | Cambios |
| :--- | :--- | :--- |
| 2.0 | 2026-08-31 | Se extiende el documento de la fase documental (20% del trabajo) al proyecto completo: se agregan las secciones 1.2 y 1.3 sobre la fase de construcción, cuatro prompts reales tomados de los ocho ciclos de desarrollo guiado por especificación archivados en `openspec/changes/archive/`, la evaluación crítica del backend y el frontend, y las filas de código y pruebas en la estimación de asistencia. Se corrigen las cifras de trazabilidad citadas en la versión anterior, que habían quedado desactualizadas, para reflejar el resultado real de `python3 scripts/validar_trazabilidad.py` (sección 6), junto con el número de comprobaciones de `validar_esquema.sh` y el número de tablas del esquema, todas verificadas ejecutando. Se retira la atribución de "convenciones de Spring Modulith" como aporte de la IA: el framework fue una propuesta descartada, no una decisión vigente (ver sección 4.2). |
| 1.0 | 2026-08-26 | Versión inicial. Cubre únicamente la fase documental: requerimientos, casos de uso, esquema SQL y diagramas. |

---

## 1. Herramientas y Etapas

### 1.1. Fase documental

| Herramienta | Etapa | Qué produjo |
| :--- | :--- | :--- |
| **Claude Opus 5**, vía **Claude Code** (interfaz agéntica de línea de comandos con acceso al sistema de archivos, a la terminal y a Docker) | Documentación | La totalidad de la documentación, el esquema SQL, los diagramas y los scripts de validación |
| **PostgreSQL 17** en contenedor efímero | Verificación | Ejecución real del esquema y de los invariantes de negocio |
| **mermaid-cli** y **PlantUML** | Verificación | Renderizado de los 11 diagramas declarativos para comprobar que compilan |

La diferencia relevante frente a un asistente conversacional es que esta herramienta **ejecuta**. No solo redacta SQL: lo corre contra una base real y reporta el error. Esa capacidad es la que sostiene el método de la sección siguiente, y es la misma que sostuvo la fase de construcción que sigue.

### 1.2. Fase de construcción

La fase documental terminó en el esqueleto ejecutable descrito en el encabezado de `CLAUDE.md`. Todo lo que sigue —diez módulos de backend, un frontend completo— se construyó con el mismo agente, pero con un método distinto: **desarrollo guiado por especificación (SDD)**. Cada unidad de trabajo pasó por un contrato de aceptación, un diseño técnico y una lista de tareas escritos **antes** de la primera línea de código, y cerró con un informe de verificación independiente y un informe de cierre. Los ocho ciclos completos —uno por módulo o sub-dominio— están archivados en `openspec/changes/archive/`:

```
2026-08-28-add-iam-module
2026-08-28-add-catalog-module
2026-08-29-add-inventory-module
2026-08-29-add-transfers-module
2026-08-30-add-purchases-module
2026-08-30-add-sales-module
2026-08-30-add-sales-customers
2026-08-31-add-analytics-module
```

| Herramienta | Etapa | Qué produjo |
| :--- | :--- | :--- |
| **Claude Opus 5**, vía **Claude Code**, orquestando subagentes especializados (`backend-contract-architect`, `backend-module-designer`, `backend-implementer` para el backend; `data-architect`, `ui-integrator`, `qa-verifier` para el frontend, todos declarados en `.claude/agents/`) | Construcción (backend y frontend) | 573 clases de producción y 136 clases de prueba (85 `*Test` + 51 `*IT`) en `backend/src/main` y `backend/src/test`; 11 módulos de frontend en `frontend/src/features`; los ocho ciclos completos de `openspec/changes/archive/` (contrato, diseño, tareas, verificación y cierre) |
| **Java 25 / Spring Boot 4.1 / Maven**, verificado en runtime, no leído de un tutorial (`CLAUDE.md`: "Spring Boot 4 no es Spring Boot 3") | Construcción (backend) | Diez módulos con arquitectura hexagonal, fronteras declaradas a mano con ArchUnit (`ModuleBoundariesTest`) |
| **PostgreSQL 17 vía Testcontainers** | Verificación (backend) | Las 51 pruebas `*IT`, cada una contra una base efímera real, no contra un mock — la misma disciplina de la fase documental aplicada al código, y la que encontró los defectos de la sección 4.2 |
| **React 19 / Vite / TypeScript, TanStack Query + Router + Table, React Hook Form + Zod, Tailwind** | Construcción (frontend) | 11 módulos bajo `frontend/src/features`, con la capa de datos, la UI y las pruebas separadas por subagente (`data-architect` → `ui-integrator` → `qa-verifier`) |
| **Vitest + React Testing Library** | Verificación (frontend) | 45 archivos de prueba de componentes y hooks |

### 1.3. Cobertura de las Áreas Sugeridas (Sección 9.1)

| Área | Impacto declarado | Aplicación concreta en este proyecto |
| :--- | :--- | :--- |
| Diseño de arquitectura | Alta | Fase documental: comparación monolito modular contra microservicios, decisión de arquitectura hexagonal, diseño del modelo de precios versionado por vigencia, matriz de compensaciones. Fase de construcción: diseño interno de los diez módulos de backend (dominio, casos de uso, puertos y adaptadores) en `backend-module-designer`, incluidas decisiones como mantener la recalculación de costo promedio ponderado detrás de un puerto ya existente en vez de crear uno nuevo (sección 3.6) |
| Generación de código | Alta | Fase documental: 639 líneas de SQL (esquema de 21 tablas y datos semilla), generadores de los 16 diagramas Excalidraw, 2 scripts de validación. Fase de construcción: 573 clases de producción del backend (Java 25 / Spring Boot 4.1) y 281 archivos TypeScript/TSX del frontend (React 19, TanStack Query/Router/Table) |
| Generación de tests | Alta | Fase documental: las 34 comprobaciones de invariantes de `validar_esquema.sh`, más las validaciones de trazabilidad documental. Fase de construcción: 85 clases `*Test` y 51 `*IT` del backend, más 45 archivos de prueba del frontend |
| Documentación técnica | Alta | 8 documentos, más de 3 000 líneas: requerimientos, casos de uso, historias, modelado, arquitectura, deuda técnica — más el contrato, diseño, tareas e informe de verificación de cada uno de los ocho ciclos archivados |
| Revisión de código | Alta | Fase documental: auditorías de las secciones 6.1 y 8 que detectaron requerimientos huérfanos, referencias rotas y una contradicción arquitectónica. Fase de construcción: los ocho informes de verificación (`verify-report.md`), que encontraron entre otros defectos una consulta JPQL que compilaba pero fallaba contra PostgreSQL real al invocarse sin rango de fechas (sección 3.8), y una ambigüedad de especificación por la que un `BRANCH_MANAGER` nunca ve las auditorías de administración de usuarios de su propia sucursal (sección 3.9) |
| Consulta de buenas prácticas | Media | Patrón de clave sustituta más token público, índices únicos parciales, restricciones de exclusión temporal, bloqueo optimista contra pesimista. Una propuesta de esta categoría —adoptar Spring Modulith para declarar las fronteras entre módulos— se descartó tras chocar con la decisión explícita del proyecto de derivarlas a mano con ArchUnit; ver sección 4.2 |

---

## 2. Método de Trabajo: la IA Propone, la Ejecución Decide

El principio que gobernó todo el proceso se enuncia en una línea:

> **Ninguna afirmación técnica entra a la documentación sin haberse ejecutado.**

No es una postura filosófica. Es la consecuencia de un hecho medible: **cada vez que se ejecutó algo que la IA había redactado con confianza, apareció al menos un defecto**. Sin excepción.

### 2.1. El Ciclo Aplicado

1. **Dirigir** — el humano define el objetivo y el criterio de calidad.
2. **Generar** — la IA produce el artefacto completo.
3. **Ejecutar** — el artefacto se corre: SQL contra PostgreSQL real, diagramas contra su renderizador, referencias contra un script.
4. **Corregir** — los defectos que la ejecución revela se arreglan y se vuelve a ejecutar.
5. **Auditar** — el humano revisa el resultado y cuestiona lo que no cierra.

Los pasos 3 y 5 son los que aportan valor. Los pasos 1 y 2 solo producen volumen.

### 2.2. Qué Encontró la Ejecución que la Lectura No Vio

| Defecto | Cómo se encontró | Consecuencia si no se detectaba |
| :--- | :--- | :--- |
| 29 identificadores UUID con prefijos no hexadecimales (`p`, `r`, `s`, `t`, `u`) | Ejecutar `02-seed-data.sql` contra PostgreSQL 17 | `docker compose up` fallaba: el proyecto no arrancaba |
| 7 usuarios con rol `ROLE_ADMIN` frente a un `CHECK` que solo admite `ADMIN` | El mismo script, tras corregir lo anterior | Ningún usuario se cargaba: sistema sin acceso |
| Rangos históricos de precio solapados aceptados por el esquema | Insertar dos rangos que se pisan y consultar la fecha en conflicto | Precio ambiguo al reconstruir una venta pasada |
| `COALESCE(valid_to, DATE 'infinity')` innecesario en la solución propuesta | Aplicar la restricción de exclusión en una base real | SQL de la ficha de deuda inservible al copiarlo |
| Dos flechas atravesando cajas en los diagramas de actividad | Script que muestrea 200 puntos por segmento | Diagramas ilegibles entregados como correctos |
| JPQL `(:from IS NULL OR a.createdAt >= :from)` sobre `Instant`: compilaba y pasaba en memoria, pero Hibernate no le da al driver un tipo SQL explícito para un parámetro cuya única aparición es un `IS NULL` | Ejecutar `AuditLogQueryIT` contra PostgreSQL real sin rango de fechas → `could not determine data type of parameter` del protocolo extendido | Consulta de auditoría inutilizable en el caso de uso más común: pedir el historial completo, sin filtrar |
| Marcador de prueba de 51 caracteres (`"audit-query-it-" + UUID.randomUUID()`) contra `entity_name VARCHAR(50)` | Ejecutar `AuditLogQueryIT` → `value too long for type character varying(50)` | Prueba de integración que nunca llega a insertar una fila |

Las dos primeras del esquema **rompían el arranque del proyecto**; las dos últimas, del backend, rompían la prueba que debía probar la funcionalidad. Ninguna de las siete era visible leyendo el archivo.

---

## 3. Prompts Reales y Resultados

Se transcriben literalmente, sin edición. Los fragmentos son preferibles a las capturas: se pueden buscar, copiar y verificar.

### 3.1. Dirección inicial

> *«Analiza @docs/prueba_tecnica_inventario.md Vamos a realizar por completo la sección "6. Ingeniería de Software Requerida"»*

**Resultado:** 37 casos de uso, 31 historias de usuario con criterios de aceptación en Gherkin, y diagramas. **Ajuste necesario:** la IA trazó los casos de uso contra el esquema SQL existente por decisión propia, lo cual fue acertado, pero produjo dos diagramas sobrecargados que el humano rechazó a continuación.

### 3.2. La corrección más valiosa de todo el proceso

> *«Se ve muy concentrado, si puedes separarlos en varios diagramas seria mejor»*

**Antes:** 2 diagramas — uno con 8 módulos y 22 flechas cruzándose.
**Después:** 9 diagramas, uno por módulo, con los casos de uso reordenados para que los de cada actor quedaran contiguos y **ninguna flecha se cruzara**.

Este intercambio merece detenimiento. La IA **había validado el diagrama original**: cero solapes, cero flechas atravesando cajas. Estaba correcto según la métrica que la IA sabía medir, y era ilegible según la única métrica que importaba. La corrección no fue de exactitud, fue de criterio — y la aportó el humano.

### 3.3. Auditoría solicitada

> *«revisa la parte "6.1. Levantamiento de Requerimientos" y si esta lo suficientemente completa o le falta algo»*

**Resultado:** la auditoría cruzó el documento contra el enunciado, el esquema SQL y los casos de uso, y encontró nueve funciones del sistema sin requerimiento que las respaldara —autenticación, gestión de usuarios, gestión de sucursales, proveedores, anulación de venta—, un requerimiento imposible de implementar con el modelo de datos existente, y cuatro categorías enteras de requerimientos no funcionales ausentes.

| Antes | Después |
| :--- | :--- |
| 32 RF · 15 RNF · 0 reglas de negocio | 42 RF · 35 requisitos no funcionales · 17 reglas de negocio |

**Observación incómoda:** las reglas `RN-01` a `RN-09` estaban **referenciadas en los casos de uso sin haber sido definidas en ninguna parte**. Ese defecto lo había introducido la propia IA dos pasos antes, y solo apareció porque el humano pidió una auditoría.

### 3.4. Exigir el plan antes de la acción

> *«Que planeas hacer con Flyway?»*

**Resultado:** obligar a explicitar el plan reveló dos trampas que no estaban documentadas: que mantener `init-db/` junto con Flyway hace fallar la migración, y que el arranque deja de ser indiferente al orden y exige un *healthcheck*. Ambas quedaron escritas en la ficha `DT-01`.

**Lección de método:** pedirle a la IA que explique un plan antes de ejecutarlo es más barato que revisar el resultado. Los supuestos ocultos salen a la superficie cuando hay que enunciarlos.

### 3.5. Recorte de alcance

> *«Si, pero creo que validar geometría de los diagramas sobra»*

**Resultado:** de tres scripts propuestos se entregaron dos. El descartado validaba el proceso interno de generación de la IA, no el entregable — al evaluador no le aportaba nada.

**Patrón observado:** la IA tiende a agregar. Propuso tres scripts porque los tres existían, sin preguntarse cuáles le sirven a quien recibe el proyecto. El recorte de alcance fue sistemáticamente humano.

### 3.6. Fase de construcción — un contrato que resuelve una pregunta abierta

Los ocho ciclos de `openspec/changes/archive/` no se dirigen con mensajes de chat sueltos sino con un contrato de aceptación por unidad de trabajo. Ese contrato tiene una sección obligatoria de preguntas abiertas, y **cerrarlas ahí, con su costo de reversión explícito, es la misma dirección humana de la sección 3.1, aplicada al código.** Del contrato de `add-purchases-module` (`contract.md:339`):

> *«PA-01 — The RN-10 recalculation lives inside `inventory`, behind `StockMutationPort.applyMovement`, with no new port and no signature change (P-05). `StockMutationPolicy` already holds both operands the formula needs, and the row is already locked there; a separate `shared` valuation-write port would mean a second write and a second lock on `inventory`'s own row, and would let a caller move stock without moving cost — the exact drift RN-02's design forbids. Reversal: extract it into a `shared` valuation port plus one call site in `purchases`.»*

**Resultado:** la recalculación del costo promedio ponderado (RN-10) no abrió un puerto nuevo entre `purchases` e `inventory` — se apoyó en el puerto síncrono que ya existía, evitando una segunda escritura y un segundo bloqueo sobre la misma fila de `branch_inventories`. La pregunta se cerró con su alternativa y el costo concreto de deshacerla, no con una afirmación de que "es la mejor opción".

### 3.7. Fase de construcción — un diseño que rechaza una opción

El diseño de `add-analytics-module` no solo elige: enumera explícitamente lo que **no** va a construir, con el motivo (`design.md:328-333`):

> *«Rejected. An `@Entity` over any foreign table (P-01 — two owners for one table). A materialised rollup or snapshot table (§1, §2.5 — a migration instead of a read). [...] A turnover ratio derived from current stock (PA-03 — a number that looks precise and is not). Caching (§1, RNF-DIS-01).»*

**Resultado:** el módulo de analítica quedó como lectura pura con `JdbcClient` y SQL nativo, sin una segunda tabla ni una nueva capa de `@Entity` sobre datos que ya son propiedad de otro módulo, y sin caché para un requisito no funcional que no lo pedía. **Ajuste que hizo el humano al revisar:** ninguno — el diseño llegó ya con la opción descartada y su motivo, que es exactamente lo que la sección 4.3 del documento anterior echaba en falta en los diagramas: la IA decidiendo qué **no** incluir, no solo qué incluir.

### 3.8. Fase de construcción — una verificación que encuentra un defecto

Durante la implementación de `add-iam-module`, una consulta JPQL parecía correcta y compilaba sin error. Falló al ejecutarse contra PostgreSQL real, y solo en un caso: sin rango de fechas. De `apply-progress.md:773-780`:

> *«`AuditLogSpringDataRepository.search` is a native query, not JPQL, found by executing, not by reading (CLAUDE.md): a plain JPQL `(:from IS NULL OR a.createdAt >= :from)` made PostgreSQL's extended query protocol fail with `could not determine data type of parameter` for the `java.time.Instant` filters — Hibernate does not give the driver an explicit SQL type hint for a parameter whose only occurrence in the generated SQL is an `IS NULL` check.»*

**Resultado:** se cambió a una consulta nativa con `CAST(:from AS timestamptz)` explícito, tanto en la comprobación `IS NULL` como en la comparación. El defecto no era visible leyendo el JPQL — es sintácticamente válido y Hibernate lo traduce sin advertencias — y solo apareció al invocar el endpoint de auditoría sin filtrar por fecha, que es precisamente el caso de uso más común: pedir el historial completo.

### 3.9. Fase de construcción — una verificación que encuentra una ambigüedad de especificación

El informe de verificación de la Slice 5a de `add-iam-module` no se limitó a comprobar que el código hiciera lo que el diseño decía; comparó el comportamiento contra la especificación y encontró una lectura de la especificación que el código satisfacía a la letra y violaba en la práctica (`verify-report.md:724-737`):

> *«Audit entries for every user-administration mutation always carry `branch_id = NULL`, because the only actor who can reach `/api/admin/users/**` is `ADMIN`, and `ADMIN.branchId()` is always `null` (corporate scope) — regardless of which branch the mutated user belongs to. [...] a `BRANCH_MANAGER` of any branch will **never** see a user-administration audit entry for their own branch [...] `audit-log/spec.md`'s "for that action, user, and branch" wording is genuinely ambiguous between "the acting admin's branch" and "the affected resource's branch".»*

**Resultado:** clasificado como `WARNING`, no `CRITICAL` — el código implementa una lectura razonable de una especificación ambigua, no un defecto de programación — y se dejó como pregunta de producto explícita antes de repetir el mismo patrón en la Slice 5b (administración de sucursales). La auditoría cruzada del documento anterior (sección 3.3) encontraba requerimientos ausentes; esta encuentra una interpretación de un requerimiento existente que el propio texto permite en dos sentidos distintos.

---

## 4. Evaluación Crítica

### 4.1. Qué Aportó la IA

**Volumen con consistencia sostenida.** 3 153 líneas de documentación con 168 identificadores trazables entre seis documentos. Mantener esa red de referencias a mano es posible; mantenerla *correcta* tras cada cambio, no. Cuando el modelo de precios se incorporó, hubo que actualizar el esquema, las semillas, tres representaciones del diagrama E-R, el SRS, el ADR y dos matrices de trazabilidad — treinta ediciones coordinadas sin dejar una referencia rota.

**Auditoría cruzada exhaustiva.** Comparar el enunciado contra el esquema SQL contra los casos de uso, línea por línea, es trabajo mecánico donde el cansancio humano produce omisiones. Ahí la IA encontró los nueve requerimientos faltantes de la sección 6.1.

**Verificación empírica barata.** Levantar PostgreSQL 17, cargar el esquema, ejercitar treinta y cuatro invariantes y destruir el contenedor toma segundos. Ese costo tan bajo cambia la economía: se vuelve razonable verificar **todo** en lugar de solo lo sospechoso.

**Alternativas con sus compensaciones.** Bloqueo pesimista contra optimista, reservar stock al aprobar contra revalidar al despachar, columna de precio contra listas versionadas. La IA expuso las opciones con sus consecuencias; la elección siguió siendo humana.

**El contrato antes del código, aplicado a diez módulos.** La fase de construcción repitió a escala de módulo lo que la sección 2 describe a escala de documento: contrato, diseño y tareas por escrito antes de una línea de producción, con las preguntas abiertas cerradas y su costo de reversión explícito (sección 3.6), y las opciones descartadas nombradas junto a las elegidas (sección 3.7). El costo de mantener esa disciplina en diez módulos en vez de uno es exactamente el tipo de trabajo mecánico donde la IA sostiene el volumen y el humano audita el criterio.

**Verificación empírica barata, extendida al código.** Levantar PostgreSQL 17 vía Testcontainers, correr una prueba de integración real y destruir el contenedor toma segundos por prueba — la misma economía de la sección 2.1, aplicada a 51 pruebas `*IT` en vez de a un esquema. Esa base es la que encontró los dos defectos de la sección 3.8/2.2 que ninguna lectura del JPQL habría visto.

### 4.2. Qué Hubo que Corregir

Esta es la sección más útil del documento, y es la que un informe complaciente omitiría.

| Defecto introducido por la IA | Naturaleza | Cómo se detectó |
| :--- | :--- | :--- |
| `ROLE_ADMIN` en el documento de arquitectura | **Invención plausible.** El prefijo `ROLE_` es convención de Spring Security, pero contradecía el `CHECK` del propio esquema | Ejecutar las semillas |
| Latencia de «< 10 ms» afirmada sin medición | **Precisión falsa.** Un número concreto suena verificado; no lo estaba, y contradecía el objetivo declarado de 200 ms | Auditoría solicitada por el humano |
| Reglas `RN-01` a `RN-09` citadas y nunca definidas | **Referencia fantasma.** Se inventó una numeración coherente sin crear su fuente | Auditoría solicitada por el humano |
| Eventos de dominio descritos como «asíncronos» | **Contradicción interna.** Rompía la garantía de atomicidad que el mismo documento prometía en otras cinco páginas | Auditoría solicitada por el humano |
| SQL de la solución de `DT-03` con un `COALESCE` innecesario | **Código no ejecutado.** Escrito de memoria y dado por bueno | Probarlo al ser consultado |
| Dos flechas atravesando cajas en los diagramas de actividad | **Error geométrico.** Apuntar al centro de un nodo en vez de a su borde | Script de validación propio |
| Afirmar que PlantUML no podía verificarse | **Conclusión apresurada.** No se comprobó si había Java instalado; lo había | Revisión posterior |
| **Spring Modulith propuesto como base de las fronteras entre módulos** | **Herramienta descartada.** Adoptado en la versión inicial del ADR; retirado explícitamente en la versión 1.3 (`docs/decisiones_arquitectura_tecnica.md`) porque derivar las fronteras de la detección automática de un framework era exactamente lo que el proyecto necesitaba evitar — hoy se declaran a mano con `ModuleBoundariesTest` (ArchUnit), y `CLAUDE.md` dice explícitamente "no reintroducirlo" | Decisión humana posterior, no ejecución — este es el único defecto de la tabla que ninguna prueba habría encontrado, porque Spring Modulith *funciona*; el problema era de dirección arquitectónica, no de corrección |
| Consulta JPQL con filtro opcional por fecha (`AuditLogSpringDataRepository.search`) | **Código que compila y pasa en memoria.** Hibernate no infiere el tipo del parámetro cuando su única aparición es un `IS NULL` | Ejecutar `AuditLogQueryIT` contra PostgreSQL real sin rango de fechas (sección 3.8) |
| Marcador de prueba de 51 caracteres contra una columna `VARCHAR(50)` | **Límite de esquema no verificado.** El literal parecía suficientemente corto | Ejecutar `AuditLogQueryIT` |

El patrón es constante: **la IA falla produciendo cosas que parecen correctas**. `ROLE_ADMIN` es plausible. «< 10 ms» suena medido. `RN-05` parece existir. Un JPQL con `IS NULL` compila. Spring Modulith es una herramienta real y bien documentada. Ninguno de esos errores se detecta leyendo con atención; todos se detectan ejecutando, auditando contra otra fuente, o — en el único caso que ni la ejecución ni la auditoría documental podían atrapar — sosteniendo una decisión arquitectónica ya tomada frente a una sugerencia posterior que la contradecía.

### 4.3. Dónde la IA No Fue Útil

**Criterio estético y carga cognitiva.** El caso de los diagramas es el más claro: la IA verificó cero solapes y cero cruces, declaró el diagrama correcto, y el resultado era ilegible. La IA puede medir lo que sabe medir; no sabe cuándo esa métrica no es la que importa.

**Decidir el alcance.** Qué queda afuera del sistema —facturación fiscal, multimoneda, segmentación por cliente— es una decisión de negocio. La IA propone incluir; casi nunca propone excluir. Las ocho exclusiones documentadas en el SRS existen porque el humano dirigió hacia allí.

**Saber cuándo parar.** Tres scripts propuestos, dos necesarios. La IA optimiza por completitud, no por utilidad para el destinatario.

**Juzgar su propio trabajo sin evidencia externa.** Cada auditoría solicitada por el humano encontró defectos que la IA no había visto al revisar lo mismo por su cuenta. La IA revisa contra su propia comprensión, que es exactamente la que produjo el error. Solo una fuente externa —un intérprete de SQL, un renderizador, una persona— rompe ese circuito.

**Mantener consistencia con una decisión arquitectónica ya cerrada.** Spring Modulith es la evidencia: propuesto en la consulta de buenas prácticas de la fase documental (sección 1.3), terminó reñido con la propia arquitectura del proyecto y hubo que retirarlo por decisión humana explícita. La IA no tiene memoria privilegiada de qué se decidió y por qué en una sesión anterior; el freno lo puso quien sí la tenía.

---

## 5. Estimación de Asistencia

**El 100% del texto, del SQL, de los diagramas y del código de la aplicación fue redactado con asistencia de IA.** Ese número, por sí solo, no informa nada: es el punto de partida, no un resultado.

La métrica útil es **cuánto sobrevivió sin corrección**:

| Artefacto | Generado con IA | Requirió corrección tras revisión |
| :--- | :---: | :--- |
| Especificación de requerimientos | 100% | **Sí** — reescrita: de 32 a 42 RF, de 15 a 35 requisitos no funcionales, 17 reglas de negocio nuevas |
| Casos de uso e historias | 100% | **Sí** — 30 correcciones de trazabilidad tras ampliar los requerimientos |
| Decisiones de arquitectura | 100% | **Sí** — reescrita: faltaba la sección 8.1, contenía una contradicción técnica, y más tarde se retiró Spring Modulith de sus propias justificaciones (sección 4.2) |
| Esquema y datos semilla SQL | 100% | **Sí** — 29 UUID inválidos, 7 roles inválidos, 2 tablas ausentes |
| Diagramas | 100% | **Sí** — de 2 lienzos densos a 9 enfocados; 3 defectos geométricos |
| Modelado del sistema y deuda técnica | 100% | No en su estructura; sí en el SQL de una ficha |
| Scripts de validación | 100% | **Sí** — uno de los tres propuestos se descartó por innecesario |
| Código de producción del backend — 573 clases, ~29 100 líneas en `backend/src/main` | 100% | **Sí** — la consulta JPQL de la sección 3.8, encontrada ejecutando `./mvnw verify`, no leyendo |
| Pruebas del backend — 136 clases (85 `*Test` + 51 `*IT`), ~22 100 líneas en `backend/src/test` | 100% | **Sí** — el marcador de prueba de 51 caracteres contra una columna `VARCHAR(50)` (sección 2.2) era un defecto del propio archivo de prueba, no del código bajo prueba |
| Código y pruebas del frontend — 11 módulos en `frontend/src/features`, 281 archivos TS/TSX, ~46 200 líneas, 45 archivos de prueba | 100% | **Sí** — iteración del subagente `qa-verifier` hasta dejar en verde typecheck, lint, la suite de pruebas y el build de Vite |

**Nueve de diez artefactos requirieron corrección sustantiva.** Ninguno fue utilizable en su primera versión.

**En la fase documental, trece intervenciones humanas cambiaron el rumbo del trabajo:** cinco correcciones de contenido, cuatro auditorías solicitadas, dos recortes de alcance, una decisión de modelo de datos y una definición de formato de entrega. En la fase de construcción el mecanismo de dirección cambió de forma: en vez de corregir después, cada uno de los ocho ciclos archivados fijó por escrito, antes de escribir código, las preguntas abiertas que había que cerrar (sección 3.6) y las opciones que quedaban fuera (sección 3.7) — la misma dirección humana, movida al principio del ciclo en lugar del final.

Si hubiera que resumirlo en un número honesto: la IA aportó **aproximadamente el 100% de la producción y cerca del 0% del criterio**, tanto en la documentación como en el código. El valor del trabajo está en la distancia entre ambas cifras.

---

## 6. Reproducibilidad

Toda afirmación de verificación de este proyecto puede comprobarse de forma independiente. Los scripts están versionados en el repositorio:

```bash
# Integridad de la trazabilidad documental — sin dependencias externas
python3 scripts/validar_trazabilidad.py

# Integridad del esquema y de los invariantes de negocio — requiere Docker
./scripts/validar_esquema.sh

# Fronteras de arquitectura del backend + pruebas unitarias e integración — requiere Docker
cd backend && ./mvnw verify
```

| Comando | Qué comprueba | Salida esperada |
| :--- | :--- | :--- |
| `validar_trazabilidad.py` | Que todo identificador citado exista, que todo RF tenga caso de uso, que todo caso de uso tenga requerimiento, que toda deuda tenga ficha y que ningún enlace esté roto | `43 RF · 34 RNF · 17 RN · 39 CU · 15 DT` — trazabilidad íntegra |
| `validar_esquema.sh` | Levanta PostgreSQL 17, carga esquema y semillas, y ejercita 34 invariantes: stock negativo, roles válidos, jerarquía de precios, descuento sobre lista, estados de transferencia | `34 comprobaciones correctas` |
| `./mvnw verify` | Reglas de fronteras de `ModuleBoundariesTest` (ArchUnit) + 85 pruebas unitarias (surefire) + 51 pruebas de integración contra PostgreSQL real vía Testcontainers (failsafe) | `BUILD SUCCESS`, todas las pruebas en verde |

Los diagramas declarativos se verificaron renderizándolos: los 9 bloques Mermaid con `mermaid-cli` y los 2 archivos PlantUML con `plantuml.jar`. No se incluyó un script para ello porque exige descargar ambas herramientas; el procedimiento queda documentado aquí para quien quiera repetirlo.

---

## 7. Conclusión

El enunciado afirma que *«el uso de IA no es una señal de debilidad técnica, sino de madurez profesional»*, y que lo evaluable es *«la capacidad del candidato para dirigir, validar y mejorar el output»*.

Este documento sostiene que las tres capacidades son verificables en el proyecto completo — documentación y código — y deja la evidencia:

* **Dirigir** — trece intervenciones que cambiaron el rumbo de la documentación (sección 3.1–3.5), y ocho ciclos de desarrollo guiado por especificación que cerraron sus propias preguntas abiertas antes de que existiera código para corregir (sección 3.6).
* **Validar** — dos scripts documentales más `./mvnw verify`, todos ejecutables por cualquiera, más los defectos que esa validación encontró, listados con nombre y consecuencia en la sección 4.2 — desde los 29 UUID inválidos del esquema hasta la consulta JPQL que solo fallaba sin rango de fechas (sección 3.8).
* **Mejorar** — el registro de lo que hubo que corregir en nueve de los diez artefactos de la sección 5, incluida una herramienta completa —Spring Modulith— propuesta y luego retirada por chocar con una decisión arquitectónica ya tomada.

La conclusión práctica del proceso cabe en una frase: **el valor no estuvo en lo que la IA produjo, sino en lo que se sometió a ejecución.** Todo lo que se dio por bueno sin ejecutar contenía un defecto. Todo — en el esquema SQL de 2026-08-26 y en la consulta JPQL de 2026-08-28 por igual.
