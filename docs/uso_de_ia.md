# Uso de Inteligencia Artificial en el Desarrollo
## Sistema de Gestión de Inventario Multi-Sucursal — OptiPlant Consultores
### Cumplimiento: Sección 9 — Uso de Inteligencia Artificial

---

## 1. Herramientas y Etapas

| Herramienta | Etapa | Qué produjo |
| :--- | :--- | :--- |
| **Claude Opus 5**, vía **Claude Code** (interfaz agéntica de línea de comandos con acceso al sistema de archivos, a la terminal y a Docker) | Todas | La totalidad de la documentación, el esquema SQL, los diagramas y los scripts de validación |
| **PostgreSQL 17** en contenedor efímero | Verificación | Ejecución real del esquema y de los invariantes de negocio |
| **mermaid-cli** y **PlantUML** | Verificación | Renderizado de los 11 diagramas declarativos para comprobar que compilan |

La diferencia relevante frente a un asistente conversacional es que esta herramienta **ejecuta**. No solo redacta SQL: lo corre contra una base real y reporta el error. Esa capacidad es la que sostiene el método de la sección siguiente.

### 1.1. Cobertura de las Áreas Sugeridas (Sección 9.1)

| Área | Impacto declarado | Aplicación concreta en este proyecto |
| :--- | :--- | :--- |
| Diseño de arquitectura | Alta | Comparación monolito modular contra microservicios, decisión de arquitectura hexagonal, diseño del modelo de precios versionado por vigencia, matriz de compensaciones |
| Generación de código | Alta | 639 líneas de SQL (esquema de 19 tablas y datos semilla), generadores de los 16 diagramas Excalidraw, 2 scripts de validación |
| Generación de tests | Media | Las 19 comprobaciones de invariantes de `validar_esquema.sh`, más las validaciones de trazabilidad documental |
| Documentación técnica | Alta | 8 documentos, 3 153 líneas: requerimientos, casos de uso, historias, modelado, arquitectura, deuda técnica |
| Revisión de código | Media | Auditorías de las secciones 6.1 y 8 que detectaron requerimientos huérfanos, referencias rotas y una contradicción arquitectónica |
| Consulta de buenas prácticas | Media | Patrón de clave sustituta más token público, índices únicos parciales, restricciones de exclusión temporal, convenciones de Spring Modulith |

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

Las dos primeras **rompían el arranque del proyecto** y ninguna era visible leyendo el archivo.

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
| 32 RF · 15 RNF · 0 reglas de negocio | 42 RF · 35 RNF · 17 reglas de negocio |

**Observación incómoda:** las reglas `RN-01` a `RN-09` estaban **referenciadas en los casos de uso sin haber sido definidas en ninguna parte**. Ese defecto lo había introducido la propia IA dos pasos antes, y solo apareció porque el humano pidió una auditoría.

### 3.4. Exigir el plan antes de la acción

> *«Que planeas hacer con Flyway?»*

**Resultado:** obligar a explicitar el plan reveló dos trampas que no estaban documentadas: que mantener `init-db/` junto con Flyway hace fallar la migración, y que el arranque deja de ser indiferente al orden y exige un *healthcheck*. Ambas quedaron escritas en la ficha `DT-01`.

**Lección de método:** pedirle a la IA que explique un plan antes de ejecutarlo es más barato que revisar el resultado. Los supuestos ocultos salen a la superficie cuando hay que enunciarlos.

### 3.5. Recorte de alcance

> *«Si, pero creo que validar geometría de los diagramas sobra»*

**Resultado:** de tres scripts propuestos se entregaron dos. El descartado validaba el proceso interno de generación de la IA, no el entregable — al evaluador no le aportaba nada.

**Patrón observado:** la IA tiende a agregar. Propuso tres scripts porque los tres existían, sin preguntarse cuáles le sirven a quien recibe el proyecto. El recorte de alcance fue sistemáticamente humano.

---

## 4. Evaluación Crítica

### 4.1. Qué Aportó la IA

**Volumen con consistencia sostenida.** 3 153 líneas de documentación con 168 identificadores trazables entre seis documentos. Mantener esa red de referencias a mano es posible; mantenerla *correcta* tras cada cambio, no. Cuando el modelo de precios se incorporó, hubo que actualizar el esquema, las semillas, tres representaciones del diagrama E-R, el SRS, el ADR y dos matrices de trazabilidad — treinta ediciones coordinadas sin dejar una referencia rota.

**Auditoría cruzada exhaustiva.** Comparar el enunciado contra el esquema SQL contra los casos de uso, línea por línea, es trabajo mecánico donde el cansancio humano produce omisiones. Ahí la IA encontró los nueve requerimientos faltantes de la sección 6.1.

**Verificación empírica barata.** Levantar PostgreSQL 17, cargar el esquema, ejercitar diecinueve invariantes y destruir el contenedor toma segundos. Ese costo tan bajo cambia la economía: se vuelve razonable verificar **todo** en lugar de solo lo sospechoso.

**Alternativas con sus compensaciones.** Bloqueo pesimista contra optimista, reservar stock al aprobar contra revalidar al despachar, columna de precio contra listas versionadas. La IA expuso las opciones con sus consecuencias; la elección siguió siendo humana.

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

El patrón es constante: **la IA falla produciendo cosas que parecen correctas**. `ROLE_ADMIN` es plausible. «< 10 ms» suena medido. `RN-05` parece existir. Ninguno de esos errores se detecta leyendo con atención; todos se detectan ejecutando o auditando contra otra fuente.

### 4.3. Dónde la IA No Fue Útil

**Criterio estético y carga cognitiva.** El caso de los diagramas es el más claro: la IA verificó cero solapes y cero cruces, declaró el diagrama correcto, y el resultado era ilegible. La IA puede medir lo que sabe medir; no sabe cuándo esa métrica no es la que importa.

**Decidir el alcance.** Qué queda afuera del sistema —facturación fiscal, multimoneda, segmentación por cliente— es una decisión de negocio. La IA propone incluir; casi nunca propone excluir. Las ocho exclusiones documentadas en el SRS existen porque el humano dirigió hacia allí.

**Saber cuándo parar.** Tres scripts propuestos, dos necesarios. La IA optimiza por completitud, no por utilidad para el destinatario.

**Juzgar su propio trabajo sin evidencia externa.** Cada auditoría solicitada por el humano encontró defectos que la IA no había visto al revisar lo mismo por su cuenta. La IA revisa contra su propia comprensión, que es exactamente la que produjo el error. Solo una fuente externa —un intérprete de SQL, un renderizador, una persona— rompe ese circuito.

---

## 5. Estimación de Asistencia

**El 100% del texto, del SQL y de los diagramas fue redactado con asistencia de IA.** Ese número, por sí solo, no informa nada: es el punto de partida, no un resultado.

La métrica útil es **cuánto sobrevivió sin corrección**:

| Artefacto | Generado con IA | Requirió corrección tras revisión |
| :--- | :---: | :--- |
| Especificación de requerimientos | 100% | **Sí** — reescrita: de 32 a 42 RF, de 15 a 35 RNF, 17 reglas de negocio nuevas |
| Casos de uso e historias | 100% | **Sí** — 30 correcciones de trazabilidad tras ampliar los requerimientos |
| Decisiones de arquitectura | 100% | **Sí** — reescrita: faltaba la sección 8.1 y contenía una contradicción técnica |
| Esquema y datos semilla SQL | 100% | **Sí** — 29 UUID inválidos, 7 roles inválidos, 2 tablas ausentes |
| Diagramas | 100% | **Sí** — de 2 lienzos densos a 9 enfocados; 3 defectos geométricos |
| Modelado del sistema y deuda técnica | 100% | No en su estructura; sí en el SQL de una ficha |
| Scripts de validación | 100% | **Sí** — uno de los tres propuestos se descartó por innecesario |

**Seis de siete artefactos requirieron corrección sustantiva.** Ninguno fue utilizable en su primera versión.

**Trece intervenciones humanas cambiaron el rumbo del trabajo:** cinco correcciones de contenido, cuatro auditorías solicitadas, dos recortes de alcance, una decisión de modelo de datos y una definición de formato de entrega.

Si hubiera que resumirlo en un número honesto: la IA aportó **aproximadamente el 100% de la producción y cerca del 0% del criterio**. El valor del trabajo está en la distancia entre ambas cifras.

---

## 6. Reproducibilidad

Toda afirmación de verificación de este proyecto puede comprobarse de forma independiente. Los scripts están versionados en el repositorio:

```bash
# Integridad de la trazabilidad documental — sin dependencias externas
python3 scripts/validar_trazabilidad.py

# Integridad del esquema y de los invariantes de negocio — requiere Docker
./scripts/validar_esquema.sh
```

| Script | Qué comprueba | Salida esperada |
| :--- | :--- | :--- |
| `validar_trazabilidad.py` | Que todo identificador citado exista, que todo RF tenga caso de uso, que todo caso de uso tenga requerimiento, que toda deuda tenga ficha y que ningún enlace esté roto | `42 RF · 35 RNF · 17 RN · 37 CU · 6 DT` — trazabilidad íntegra |
| `validar_esquema.sh` | Levanta PostgreSQL 17, carga esquema y semillas, y ejercita 19 invariantes: stock negativo, roles válidos, jerarquía de precios, descuento sobre lista, estados de transferencia | `19 comprobaciones correctas` |

Los diagramas declarativos se verificaron renderizándolos: los 9 bloques Mermaid con `mermaid-cli` y los 2 archivos PlantUML con `plantuml.jar`. No se incluyó un script para ello porque exige descargar ambas herramientas; el procedimiento queda documentado aquí para quien quiera repetirlo.

---

## 7. Conclusión

El enunciado afirma que *«el uso de IA no es una señal de debilidad técnica, sino de madurez profesional»*, y que lo evaluable es *«la capacidad del candidato para dirigir, validar y mejorar el output»*.

Este documento sostiene que las tres capacidades son verificables, y deja la evidencia:

* **Dirigir** — trece intervenciones que cambiaron el rumbo, transcritas literalmente en la sección 3.
* **Validar** — dos scripts ejecutables que cualquiera puede correr, más los defectos que esa validación encontró, listados con nombre y consecuencia en la sección 4.2.
* **Mejorar** — el registro de lo que hubo que corregir, incluidos los siete errores que la propia IA introdujo.

La conclusión práctica del proceso cabe en una frase: **el valor no estuvo en lo que la IA produjo, sino en lo que se sometió a ejecución.** Todo lo que se dio por bueno sin ejecutar contenía un defecto. Todo.
