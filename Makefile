# Makefile — orquestacion local del sistema de inventario multi-sucursal.
#
# Docker se usa SOLO para la base de datos (`make db`) y para levantar todo
# junto (`make up`). El backend y el frontend, por separado, corren nativos:
#   - backend:  ./mvnw spring-boot:run   (necesita `make db` corriendo)
#   - frontend: pnpm dev                 (HMR)
#
# Los objetivos de ciclo de vida (stop / down / logs / ps) operan sobre los
# contenedores de Compose.
#
# Uso: `make` o `make help` para ver los objetivos.

COMPOSE      ?= docker compose
PNPM         ?= pnpm
MVNW         ?= ./mvnw
FRONTEND_DIR := frontend
BACKEND_DIR  := backend

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
db: ## Levanta solo PostgreSQL (unico objetivo que usa Docker por si solo)
	$(COMPOSE) up -d db

.PHONY: backend
backend: ## Levanta el backend nativo con Maven (requiere `make db` corriendo)
	cd $(BACKEND_DIR) && $(MVNW) spring-boot:run

.PHONY: frontend
frontend: frontend-install ## Levanta el frontend nativo en modo dev (HMR)
	$(PNPM) --dir $(FRONTEND_DIR) dev

.PHONY: frontend-install
frontend-install: ## Instala dependencias del frontend si faltan
	@test -d $(FRONTEND_DIR)/node_modules || $(PNPM) --dir $(FRONTEND_DIR) install

# ---------------------------------------------------------------------------
# Todo junto (Docker)
# ---------------------------------------------------------------------------

.PHONY: up
up: frontend-install ## DB + backend en contenedores; frontend nativo en primer plano
	$(COMPOSE) up -d --build db backend
	$(PNPM) --dir $(FRONTEND_DIR) dev

.PHONY: up-containers
up-containers: ## DB + backend en contenedores (segundo plano); para dev con frontend nativo
	$(COMPOSE) up -d --build db backend

.PHONY: up-full
up-full: ## Stack completo en contenedores: DB + backend + frontend (Nginx) en segundo plano
	$(COMPOSE) up -d --build

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
logs: ## Sigue los registros de los contenedores
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
	cd $(BACKEND_DIR) && $(MVNW) verify

.PHONY: verify-frontend
verify-frontend: frontend-install ## Lint + typecheck + test del frontend
	$(PNPM) --dir $(FRONTEND_DIR) lint
	$(PNPM) --dir $(FRONTEND_DIR) typecheck
	$(PNPM) --dir $(FRONTEND_DIR) test
