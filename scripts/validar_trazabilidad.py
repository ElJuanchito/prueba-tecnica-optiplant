#!/usr/bin/env python3
"""
Valida la integridad de la trazabilidad entre los documentos de ingeniería.

Comprueba que:
  1. Todo identificador (RF, RNF, RN) citado en cualquier documento esté definido en el SRS.
  2. Todo requerimiento funcional tenga al menos un caso de uso que lo materialice.
  3. Todo caso de uso del catálogo tenga un requerimiento que lo respalde.
  4. Todo ítem de deuda técnica tenga su ficha detallada.
  5. Ningún enlace relativo entre documentos esté roto.

Uso:  python3 scripts/validar_trazabilidad.py
Sale con código 0 si todo está consistente, 1 si encuentra algún defecto.
No requiere dependencias externas.
"""
import os
import re
import sys

RAIZ = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DOCS = os.path.join(RAIZ, "docs")

SRS = "especificacion_requerimientos.md"
CASOS = "casos_de_uso.md"
DEUDA = "deuda_tecnica.md"

PATRON_ID = r"\b(?:RF|RNF|RN)-[A-Z]*-?\d+\b"
PATRON_DEF = r"\*\*((?:RF|RNF|RN)-[A-Z]*-?\d+)"


def leer(nombre):
    with open(os.path.join(DOCS, nombre), encoding="utf-8") as f:
        return f.read()


def documentos():
    return sorted(f for f in os.listdir(DOCS)
                  if f.endswith(".md") and f != "prueba_tecnica_inventario.md")


def main():
    fallos = []
    srs = leer(SRS)
    definidos = set(re.findall(PATRON_DEF, srs))

    print("Validación de trazabilidad — OptiPlant\n")

    # 1. Referencias huérfanas
    print("1. Identificadores citados que no existen en el SRS")
    for doc in documentos():
        usados = set(re.findall(PATRON_ID, leer(doc)))
        rotas = sorted(u for u in usados if u not in definidos)
        estado = "ok" if not rotas else "FALLA"
        print(f"   {estado:5s} {doc:38s} {len(usados):3d} referencias")
        if rotas:
            fallos.append(f"{doc}: referencias inexistentes -> {', '.join(rotas)}")
            print(f"         rotas: {', '.join(rotas)}")

    casos = leer(CASOS)
    matriz = casos[casos.index("## 6. Matriz de Trazabilidad"):]

    # 2. Requerimientos funcionales sin caso de uso
    print("\n2. Requerimientos funcionales sin caso de uso que los materialice")
    rf = sorted(d for d in definidos if d.startswith("RF-"))
    sin_cu = [r for r in rf if f"| {r} |" not in matriz]
    print(f"   {'ok' if not sin_cu else 'FALLA':5s} {len(rf)} RF definidos, {len(rf) - len(sin_cu)} con caso de uso")
    if sin_cu:
        fallos.append("RF sin caso de uso -> " + ", ".join(sin_cu))
        print(f"         sin cubrir: {', '.join(sin_cu)}")

    # 3. Casos de uso sin requerimiento de respaldo
    print("\n3. Casos de uso del catálogo sin requerimiento de respaldo")
    filas = re.findall(r"\| \*\*(CU-[A-Z]+-\d+)\*\* \|[^|]+\|[^|]+\| ([^|]+) \|", casos)
    sin_rf = [c for c, r in filas if "RF-" not in r and "RNF-" not in r]
    print(f"   {'ok' if not sin_rf else 'FALLA':5s} {len(filas)} casos de uso en el catálogo")
    if sin_rf:
        fallos.append("CU sin requerimiento -> " + ", ".join(sin_rf))
        print(f"         sin respaldo: {', '.join(sin_rf)}")

    # 4. Deuda técnica declarada frente a deuda documentada
    print("\n4. Ítems de deuda técnica con ficha detallada")
    deuda = leer(DEUDA)
    declarados = set(re.findall(r"\*\*(DT-\d+)\*\*", deuda))
    con_ficha = set(re.findall(r"### (DT-\d+)", deuda))
    faltan = sorted(declarados - con_ficha)
    print(f"   {'ok' if not faltan else 'FALLA':5s} {len(declarados)} declarados, {len(con_ficha)} con ficha")
    if faltan:
        fallos.append("DT sin ficha -> " + ", ".join(faltan))

    # 5. Enlaces relativos rotos
    print("\n5. Enlaces relativos entre documentos")
    total = rotos = 0
    for doc in documentos():
        for destino in re.findall(r"\]\(\./([^)#]+)\)", leer(doc)):
            total += 1
            if not os.path.exists(os.path.join(DOCS, destino)):
                rotos += 1
                fallos.append(f"{doc}: enlace roto -> {destino}")
    print(f"   {'ok' if not rotos else 'FALLA':5s} {total} enlaces revisados, {rotos} rotos")

    print("\n" + "-" * 62)
    if fallos:
        print(f"RESULTADO: {len(fallos)} defecto(s) de trazabilidad\n")
        for f in fallos:
            print("  - " + f)
        return 1
    print("RESULTADO: trazabilidad íntegra")
    print(f"  {len(rf)} RF · {len([d for d in definidos if d.startswith('RNF-')])} RNF · "
          f"{len([d for d in definidos if d.startswith('RN-')])} RN · {len(filas)} CU · "
          f"{len(declarados)} DT")
    return 0


if __name__ == "__main__":
    sys.exit(main())
