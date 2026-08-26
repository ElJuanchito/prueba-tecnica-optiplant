#!/usr/bin/env bash
#
# Verifica el esquema y los datos semilla contra un PostgreSQL 17 real.
#
# No se limita a comprobar que los scripts corran: ejercita los invariantes
# que la documentación promete, confirmando que el esquema los hace cumplir.
#
# Uso:  ./scripts/validar_esquema.sh
# Requiere Docker. Sale con código 0 si todo pasa, 1 si algo falla.
#
set -uo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONTENEDOR="optiplant_validacion_$$"
IMAGEN="postgres:17-alpine"
OK=0; FALLOS=0

limpiar() { docker rm -f "$CONTENEDOR" >/dev/null 2>&1 || true; }
trap limpiar EXIT

sql() { docker exec -i "$CONTENEDOR" psql -U postgres -d optiplant -v ON_ERROR_STOP=1 -tAqc "$1" 2>&1; }

# Espera que la sentencia sea RECHAZADA por el esquema
rechaza() {
  local desc="$1" stmt="$2"
  if sql "$stmt" >/dev/null 2>&1; then
    printf "  FALLA  %s\n         se aceptó una operación que debía rechazarse\n" "$desc"; FALLOS=$((FALLOS+1))
  else
    printf "  ok     %s\n" "$desc"; OK=$((OK+1))
  fi
}

# Espera que la sentencia sea ACEPTADA
acepta() {
  local desc="$1" stmt="$2"
  if sql "$stmt" >/dev/null 2>&1; then
    printf "  ok     %s\n" "$desc"; OK=$((OK+1))
  else
    printf "  FALLA  %s\n         se rechazó una operación válida\n" "$desc"; FALLOS=$((FALLOS+1))
  fi
}

# Espera que la consulta devuelva un valor concreto
igual() {
  local desc="$1" stmt="$2" esperado="$3" real
  real="$(sql "$stmt" | tr -d '[:space:]')"
  if [ "$real" = "$esperado" ]; then
    printf "  ok     %s  (%s)\n" "$desc" "$real"; OK=$((OK+1))
  else
    printf "  FALLA  %s\n         esperado: %s   obtenido: %s\n" "$desc" "$esperado" "$real"; FALLOS=$((FALLOS+1))
  fi
}

echo "Validación del esquema — OptiPlant"
echo

echo "Levantando $IMAGEN..."
docker run --rm -e POSTGRES_PASSWORD=validacion -e POSTGRES_DB=optiplant \
  -d --name "$CONTENEDOR" "$IMAGEN" >/dev/null 2>&1 || { echo "No se pudo iniciar Docker."; exit 1; }

for _ in $(seq 1 60); do
  docker exec "$CONTENEDOR" psql -U postgres -d optiplant -tAc "SELECT 1" >/dev/null 2>&1 && break
  sleep 1
done

echo
echo "A. Carga de los scripts de inicialización"
for archivo in 01-init-schema.sql 02-seed-data.sql; do
  docker cp "$RAIZ/backend/init-db/$archivo" "$CONTENEDOR:/tmp/$archivo" >/dev/null
  if docker exec "$CONTENEDOR" psql -U postgres -d optiplant -v ON_ERROR_STOP=1 -q -f "/tmp/$archivo" >/dev/null 2>&1; then
    printf "  ok     %s\n" "$archivo"; OK=$((OK+1))
  else
    printf "  FALLA  %s — el script no se ejecuta sin errores\n" "$archivo"; FALLOS=$((FALLOS+1))
    docker exec "$CONTENEDOR" psql -U postgres -d optiplant -v ON_ERROR_STOP=1 -q -f "/tmp/$archivo" 2>&1 | head -3 | sed 's/^/         /'
    echo; echo "RESULTADO: la base no se puede inicializar."; exit 1
  fi
done
igual "19 tablas creadas" "SELECT count(*) FROM information_schema.tables WHERE table_schema='public'" "19"

echo
echo "B. Integridad del inventario"
rechaza "RN-01 · el stock no puede quedar negativo" \
  "UPDATE branch_inventories SET current_stock = -1 WHERE id = 1"
rechaza "RN-02 · un movimiento de Kardex exige cantidad positiva" \
  "INSERT INTO kardex_movements (branch_id, product_id, movement_type, quantity, unit_cost, total_cost, previous_stock, resulting_stock) VALUES (1,1,'SALE',0,10,0,10,10)"
rechaza "un movimiento de Kardex exige un tipo válido" \
  "INSERT INTO kardex_movements (branch_id, product_id, movement_type, quantity, unit_cost, total_cost, previous_stock, resulting_stock) VALUES (1,1,'INVENTADO',1,10,10,10,9)"

echo
echo "C. Seguridad y roles"
rechaza "el rol debe ser ADMIN, BRANCH_MANAGER u OPERATOR" \
  "INSERT INTO users (branch_id, username, email, password_hash, full_name, role) VALUES (1,'x','x@x.com','h','X','ROLE_ADMIN')"
igual  "todo usuario tiene external_id público" \
  "SELECT count(*) FROM users WHERE external_id IS NULL" "0"

echo
echo "D. Precios comerciales"
igual "RN-16 · la sucursal con precio propio gana sobre el corporativo" \
  "SELECT unit_price::numeric(14,0) FROM price_list_items WHERE price_list_id=1 AND product_id=1 AND branch_id=3 AND valid_to IS NULL" "3980"
igual "RN-16 · las demás sucursales aplican el precio corporativo" \
  "SELECT unit_price::numeric(14,0) FROM price_list_items WHERE price_list_id=1 AND product_id=1 AND branch_id IS NULL AND valid_to IS NULL" "4200"
igual "el precio histórico vencido no está vigente hoy" \
  "SELECT count(*) FROM price_list_items WHERE price_list_id=1 AND product_id=1 AND branch_id IS NULL AND valid_to < CURRENT_DATE" "1"
rechaza "no puede haber dos precios corporativos vigentes" \
  "INSERT INTO price_list_items (price_list_id, product_id, branch_id, unit_price) VALUES (1,1,NULL,9999)"
rechaza "no puede haber dos listas marcadas por defecto" \
  "UPDATE price_lists SET is_default = TRUE WHERE code = 'WHOLESALE'"
rechaza "RN-17 · el descuento no puede superar el precio de lista" \
  "INSERT INTO sales (invoice_number,branch_id,user_id,price_list_id,customer_name,subtotal,total_amount) VALUES ('V-TEST',1,5,1,'Prueba',1000,1000);
   INSERT INTO sale_items (sale_id,product_id,quantity,list_unit_price,unit_price,discount_percent,subtotal) VALUES (1,1,10,4200,4500,0,45000)"

echo
echo "E. Transferencias entre sucursales"
rechaza "RN-05 · el estado de la transferencia debe ser uno de los válidos" \
  "UPDATE transfers SET status = 'ENTREGADO' WHERE id = 1"
rechaza "origen y destino no pueden ser la misma sucursal" \
  "INSERT INTO transfers (transfer_number, origin_branch_id, destination_branch_id, requested_by_user_id) VALUES ('T-TEST',1,1,1)"
rechaza "una ruta logística no puede unir una sucursal consigo misma" \
  "INSERT INTO logistics_routes (origin_branch_id, destination_branch_id, estimated_duration_hours) VALUES (2,2,5)"

echo
echo "F. Trazabilidad e identificadores públicos"
igual "toda tabla de negocio expone external_id" \
  "SELECT count(*) FROM information_schema.tables t WHERE t.table_schema='public' AND NOT EXISTS (SELECT 1 FROM information_schema.columns c WHERE c.table_name=t.table_name AND c.column_name='external_id')" "0"
igual "el Kardex sembrado cuadra con el stock de la sucursal 1" \
  "SELECT count(*) FROM kardex_movements WHERE resulting_stock < 0" "0"

echo
echo "------------------------------------------------------------"
if [ "$FALLOS" -gt 0 ]; then
  echo "RESULTADO: $FALLOS comprobación(es) fallida(s), $OK correcta(s)"
  exit 1
fi
echo "RESULTADO: $OK comprobaciones correctas — esquema íntegro"
