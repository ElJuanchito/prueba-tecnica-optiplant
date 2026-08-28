# Makefile — orquestacion local del sistema de inventario multi-sucursal.
#
# La DB y el backend corren en contenedores (compose.yml). El frontend todavia
# no tiene servicio de Compose: se levanta local con `pnpm dev` (HMR). Los
# objetivos de ciclo de vida (stop / down / logs / ps) operan sobre los
# contenedores de Compose.
#
# Uso: `make` o `make help` para ver los objetivos.

COMPOSE      ?= docker compose
PNPM         ?= pnpm
FRONTEND_DIR := frontend

.DEFAULT_GOAL := help

# ---------------------------------------------------------------------------
# Ayuda
# ---------------------------------------------------------------------------

.PHONY: help
help: ## Muestra esta ayuda
	@grep -E '^[a-zA-Z0-9_-]+:.*?## ' $(MAKEFILE_LIST) \
		| awk 'BEGIN{FS=":.*?## "}{printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

# ---------------------------------------------------------------------------
# Servicios por separado
# ---------------------------------------------------------------------------

.PHONY: db
db: ## Levanta solo PostgreSQL (contenedor, segundo plano)
	$(COMPOSE) up -d db

.PHONY: backend
backend: ## Levanta backend + su DB (contenedor, segundo plano; reconstruye la imagen)
	$(COMPOSE) up -d --build backend

.PHONY: frontend
frontend: frontend-install ## Levanta el frontend en modo dev (local, HMR, primer plano)
	$(PNPM) --dir $(FRONTEND_DIR) dev

.PHONY: frontend-install
frontend-install: ## Instala dependencias del frontend si faltan
	@test -d $(FRONTEND_DIR)/node_modules || $(PNPM) --dir $(FRONTEND_DIR) install

# ---------------------------------------------------------------------------
# Todo junto
# ---------------------------------------------------------------------------

.PHONY: up
up: frontend-install ## DB + backend (contenedores, segundo plano) y frontend (local, primer plano)
	$(COMPOSE) up -d --build db backend
	$(PNPM) --dir $(FRONTEND_DIR) dev

.PHONY: up-containers
up-containers: ## Solo lo contenerizado: DB + backend (segundo plano)
	$(COMPOSE) up -d --build db backend

# ---------------------------------------------------------------------------
# Ciclo de vida de los contenedores
# ---------------------------------------------------------------------------

.PHONY: stop
stop: ## Detiene los contenedores sin borrarlos
	$(COMPOSE) stop

.PHONY: down
down: ## Baja los contenedores (conserva el volumen de datos)
	$(COMPOSE) down

.PHONY: down-v
down-v: ## Baja los contenedores y BORRA el volumen pgdata
	$(COMPOSE) down -v

.PHONY: logs
logs: ## Sigue los registros de DB + backend
	$(COMPOSE) logs -f

.PHONY: ps
ps: ## Estado de los contenedores
	$(COMPOSE) ps

# ---------------------------------------------------------------------------
# Verificacion
# ---------------------------------------------------------------------------

.PHONY: verify
verify: ## Las tres validaciones del proyecto (trazabilidad, esquema, backend)
	python3 scripts/validar_trazabilidad.py
	./scripts/validar_esquema.sh
	cd backend && ./mvnw verify

.PHONY: verify-frontend
verify-frontend: frontend-install ## Lint + typecheck + test del frontend
	$(PNPM) --dir $(FRONTEND_DIR) lint
	$(PNPM) --dir $(FRONTEND_DIR) typecheck
	$(PNPM) --dir $(FRONTEND_DIR) test
