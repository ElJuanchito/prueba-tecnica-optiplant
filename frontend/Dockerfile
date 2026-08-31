# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# Etapa de construcción — Node + pnpm, se descarta al terminar.
#
# La SPA se compila a archivos estáticos; ni Node ni node_modules llegan a la
# imagen final. NODE_VERSION sigue el piso de `engines` en package.json y la
# versión del job `frontend` de .github/workflows/ci.yml (el `.nvmrc` local
# apunta a 24: conviene reconciliar esa diferencia).
# ---------------------------------------------------------------------------
ARG NODE_VERSION=22
ARG NGINX_IMAGE=nginxinc/nginx-unprivileged:stable-alpine

FROM node:${NODE_VERSION}-slim AS build

# Ubicación del store de pnpm; el cache mount de BuildKit apunta acá.
ENV PNPM_HOME="/pnpm"
ENV PATH="$PNPM_HOME/bin:$PATH"
# corepack toma la versión exacta del campo `packageManager` de package.json.
RUN corepack enable

WORKDIR /app

# El orden importa: las dependencias se resuelven en su propia capa, de modo que
# un cambio en src/ no obligue a reinstalar el árbol entero. pnpm-workspace.yaml
# es necesario para resolver `allowBuilds` y cualquier referencia de catálogo.
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
RUN --mount=type=cache,id=pnpm,target=/pnpm/store \
	pnpm install --frozen-lockfile

COPY . .

# `pnpm build` = `tsc -b && vite build`: un cambio incompatible de tipos con el
# backend rompe acá, no en producción. Genera /app/dist.
RUN pnpm build

# ---------------------------------------------------------------------------
# Etapa de ejecución — sólo Nginx y los estáticos.
#
# nginx-unprivileged corre como UID 101 sin root y escucha en 8080, igual que
# el patrón de usuario sin privilegios del Dockerfile del backend.
# ---------------------------------------------------------------------------
FROM ${NGINX_IMAGE} AS runtime

# Plantilla: el entrypoint de la imagen corre envsubst sobre /etc/nginx/templates
# al arrancar. BACKEND_URL define el upstream de /api dentro de la red de Compose;
# DNS_RESOLVER es el DNS embebido de Docker, que resuelve ese nombre en runtime.
ENV BACKEND_URL=http://backend:8080 \
	DNS_RESOLVER=127.0.0.11
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template

COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 8080

# Sonda de salud sin dependencias extra: wget viene en la base alpine.
# 127.0.0.1 explícito y no `localhost`: Nginx escucha sólo en IPv4 y el wget de
# BusyBox no reintenta en la siguiente dirección si `localhost` resuelve a ::1.
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=3 \
	CMD ["wget", "--spider", "-q", "http://127.0.0.1:8080/healthz"]
