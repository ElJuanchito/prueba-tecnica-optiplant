# Documentación de Ingeniería — OptiPlant

Índice de la documentación del sistema de inventario multi-sucursal. Cada documento
cubre una de las secciones de ingeniería que pide el enunciado (`../prueba_tecnica_inventario.md`).

| # | Documento | Contenido | Enunciado |
| :--- | :--- | :--- | :--- |
| 1 | [`especificacion_requerimientos.md`](./especificacion_requerimientos.md) | Requerimientos funcionales y no funcionales, reglas de negocio, restricciones, supuestos y alcance excluido. Fuente de verdad de los identificadores `RF` / `RNF` / `RN`. | §6.1 |
| 2 | [`casos_de_uso.md`](./casos_de_uso.md) | Actores, matriz RBAC, catálogo de casos de uso, especificaciones detalladas y la matriz de trazabilidad `RF` ↔ `CU`. | §6.2 |
| 3 | [`historias_de_usuario.md`](./historias_de_usuario.md) | Backlog en épicas e historias de usuario con criterios de aceptación en Gherkin y priorización MoSCoW. | §6.3 |
| 4 | [`modelado_sistema.md`](./modelado_sistema.md) | Los cuatro diagramas obligatorios: casos de uso, actividad, arquitectura y el modelo entidad-relación (Mermaid, PlantUML y Excalidraw). | §7 |
| 5 | [`decisiones_arquitectura_tecnica.md`](./decisiones_arquitectura_tecnica.md) | Separación de responsabilidades, decisiones técnicas justificadas (lenguaje, base de datos, autenticación, sincronización, patrones) y el registro de deuda técnica (§7 del documento). | §8 |
| 6 | [`uso_de_ia.md`](./uso_de_ia.md) | Herramientas de IA usadas, prompts reales, evaluación crítica y estimación del porcentaje asistido. | §9 |
| 7 | Este índice | — | — |

## Diagramas

Los archivos editables viven en [`diagrams/`](./diagrams): 16 lienzos Excalidraw y 2
archivos PlantUML. Los bloques Mermaid embebidos en los documentos se renderizan
directamente en GitHub.

## Cómo leerla

El orden de la tabla es también el orden de lectura recomendado: los requerimientos
fijan el *qué*, los casos de uso y las historias lo desglosan, el modelado lo dibuja
y las decisiones de arquitectura explican el *por qué* de cada elección.

## Verificación

Desde la raíz del repositorio:

```bash
python3 scripts/validar_trazabilidad.py
```

Comprueba que todo identificador citado exista en el SRS, que todo requerimiento
funcional tenga un caso de uso, que todo caso de uso tenga respaldo, que toda deuda
técnica tenga su ficha y que ningún enlace relativo entre documentos esté roto.
