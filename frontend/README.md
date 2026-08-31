# Frontend — SPA de presentación

Cliente web del sistema de inventario multi-sucursal. Es la **capa de presentación**
descrita en la sección 3.2 de [`docs/decisiones_arquitectura_tecnica.md`](../docs/decisiones_arquitectura_tecnica.md):
renderiza la interfaz, captura entradas, valida formato en el cliente para dar
respuesta inmediata, gestiona el token de sesión y presenta los errores que
devuelve la API.

No decide reglas de negocio, no calcula totales autoritativos, no valida
disponibilidad de stock, no accede a la base de datos y no conoce identificadores
internos. Toda ruta usa `external_id`; el `id` numérico nunca sale del backend.

## Stack

| Pieza              | Elección                        | Motivo                                              |
| :----------------- | :------------------------------ | :-------------------------------------------------- |
| Framework          | React 19                        | Ecosistema maduro para formularios y tablas densas  |
| Build              | Vite                            | Arranque en frío casi instantáneo                   |
| Lenguaje           | TypeScript en **modo estricto** | El contrato de la API llega tipado hasta el cliente |
| Enrutamiento       | TanStack Router                 | Rutas por archivo tipadas, con generación de árbol  |
| Estado de servidor | TanStack Query                  | Caché, revalidación y estados de carga de la API    |
| Linter             | oxlint                          | Viene con la plantilla de Vite                      |
| Formato            | Prettier                        | Sin comillas dobles, sin punto y coma               |
| Pruebas            | Vitest + Testing Library        | El proyecto no da nada por terminado sin ejecutarlo |
| Gestor de paquetes | **pnpm** (no npm)               | Fijado en `packageManager` y `engines`              |

El modo estricto de TypeScript es un requisito de arquitectura, no una preferencia:
un cambio incompatible en el backend debe romper la compilación del frontend, no
la producción. `tsconfig.app.json` añade además `noUncheckedIndexedAccess`,
`exactOptionalPropertyTypes` y `noImplicitOverride`.

## Requisitos

- Node 24 (ver `.nvmrc`); `engines` en `package.json` exige `>=22`
- pnpm >= 11 (`corepack enable` o instalación global)

## Comandos

```bash
pnpm install          # instalar dependencias
pnpm dev              # servidor de desarrollo con HMR
pnpm build            # typecheck (tsc -b) + build de producción
pnpm preview          # servir el build de producción
pnpm lint             # oxlint
pnpm format           # Prettier --write
pnpm format:check     # Prettier --check (para CI)
pnpm typecheck        # solo verificación de tipos
pnpm test             # Vitest en modo run (una pasada)
pnpm test:watch       # Vitest en modo watch
```

Antes de dar por terminado un cambio en `frontend/`: `pnpm lint`, `pnpm typecheck`
y `pnpm test` en verde.

## Estado

Interfaz de los diez módulos de dominio del backend implementada: IAM (inicio de
sesión, gestión de usuarios, auditoría), catálogo, inventario, transferencias,
logística, ventas, precios, compras, clientes y notificaciones, más la analítica
de solo lectura. El enrutamiento arranca en `src/main.tsx` con TanStack Router;
`src/routes/` define las páginas y `src/features/<dominio>/` agrupa componentes,
hooks, servicios y esquemas de cada área. Los estados de sesión y el aislamiento
por rol viven en `src/features/iam` y `src/lib/permissions.ts`.

El alias `@/` apunta a `src/`.
