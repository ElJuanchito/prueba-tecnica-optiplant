-- ============================================================================
-- OPTIPLANT CONSULTORES - SISTEMA DE GESTIÓN DE INVENTARIO MULTI-SUCURSAL
-- Script DDL: 01-init-schema.sql
-- Motor: PostgreSQL 17
-- Patrón: Surrogate Numerical PK (BIGINT) + Public API External ID (UUID)
-- ============================================================================

-- Habilitar extensión para UUIDs nativos
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ============================================================================
-- 1. MÓDULO: IAM & SUCURSALES (Organization & Security Context)
-- ============================================================================

CREATE TABLE branches (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    code VARCHAR(20) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    address VARCHAR(255) NOT NULL,
    city VARCHAR(100) NOT NULL,
    phone VARCHAR(50),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_branches_external_id ON branches(external_id);

CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    branch_id BIGINT REFERENCES branches(id) ON DELETE SET NULL, -- NULL para administradores corporativos
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(120) NOT NULL,
    role VARCHAR(30) NOT NULL CHECK (role IN ('ADMIN', 'BRANCH_MANAGER', 'OPERATOR')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_external_id ON users(external_id);
CREATE INDEX idx_users_branch_id ON users(branch_id);
CREATE INDEX idx_users_role ON users(role);

-- Sesiones de refresco. Guarda solo el digest del token, nunca el valor crudo.
-- family_id encadena las rotaciones de un mismo inicio de sesión: si un token ya
-- rotado se vuelve a presentar, se revoca la familia completa y no las demás
-- sesiones del usuario (varios dispositivos simultáneos son válidos).
CREATE TABLE refresh_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id UUID NOT NULL,
    -- SHA-256 en hexadecimal: determinista, de modo que la búsqueda por hash es
    -- un acceso por índice único. BCrypt, al llevar sal, no permitiría buscarlo.
    token_hash VARCHAR(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    issued_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_used_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_reason VARCHAR(20) CHECK (revoked_reason IN ('LOGOUT', 'ROTATED', 'REUSE_DETECTED', 'USER_DISABLED')),
    CONSTRAINT chk_refresh_tokens_revocacion CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL)),
    CONSTRAINT chk_refresh_tokens_vigencia CHECK (expires_at > issued_at)
);

CREATE INDEX idx_refresh_tokens_external_id ON refresh_tokens(external_id);
CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens(family_id);
CREATE INDEX idx_refresh_tokens_expires_at ON refresh_tokens(expires_at);

-- ============================================================================
-- 2. MÓDULO: CATÁLOGO MAESTRO (Master Catalog)
-- ============================================================================

CREATE TABLE categories (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_categories_external_id ON categories(external_id);
-- La unicidad del nombre debe ser insensible a mayúsculas: el UNIQUE de la columna
-- distingue mayúsculas y dejaría convivir 'Fertilizantes' con 'fertilizantes'
-- (RNF-INT-03; el mismo papel que products.sku UNIQUE cumple para el SKU, que sí
-- llega normalizado en mayúsculas desde el dominio).
CREATE UNIQUE INDEX uq_categories_name_ci ON categories (LOWER(name));

CREATE TABLE products (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    category_id BIGINT NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    sku VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    base_unit VARCHAR(20) NOT NULL, -- ej. UNIDAD, KG, LITRO
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_products_external_id ON products(external_id);
CREATE INDEX idx_products_category ON products(category_id);
CREATE INDEX idx_products_sku ON products(sku);

CREATE TABLE product_units (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    unit_name VARCHAR(50) NOT NULL, -- ej. CAJA_24, SACO_50KG, PALLET
    conversion_factor NUMERIC(12, 4) NOT NULL CHECK (conversion_factor > 0), -- Cantidad en base_unit
    is_default_sale_unit BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_product_unit UNIQUE (product_id, unit_name)
);

CREATE INDEX idx_product_units_external_id ON product_units(external_id);
CREATE INDEX idx_product_units_product ON product_units(product_id);
-- Un producto tiene a lo sumo una unidad de venta predeterminada (RN-13 / RNF-INT-03).
-- Mismo patrón que uq_price_lists_single_default (:145), que resuelve el problema
-- idéntico un módulo más allá.
CREATE UNIQUE INDEX uq_product_units_single_default
    ON product_units(product_id) WHERE is_default_sale_unit;

-- ============================================================================
-- 2.B MÓDULO: LISTAS DE PRECIOS COMERCIALES (Commercial Pricing)
-- ----------------------------------------------------------------------------
-- Regla de resolución del precio vigente de un producto en una sucursal:
--   1. Se toma la lista de precios de la venta (o la lista por defecto de la sucursal).
--   2. Dentro de esa lista gana el ítem con branch_id de la sucursal; si no existe,
--      se aplica el ítem corporativo (branch_id IS NULL).
--   3. Entre esos, se toma el ítem vigente a la fecha de la operación.
-- ============================================================================

CREATE TABLE price_lists (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    code VARCHAR(30) NOT NULL UNIQUE, -- ej. RETAIL, WHOLESALE, INSTITUTIONAL
    name VARCHAR(100) NOT NULL,
    description TEXT,
    max_discount_percent NUMERIC(5, 2) NOT NULL DEFAULT 0.00
        CHECK (max_discount_percent BETWEEN 0 AND 100), -- Tope de descuento permitido sobre esta lista
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_price_lists_external_id ON price_lists(external_id);
-- Sólo puede existir una lista de precios corporativa marcada como predeterminada
CREATE UNIQUE INDEX uq_price_lists_single_default ON price_lists(is_default) WHERE is_default;

CREATE TABLE price_list_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    price_list_id BIGINT NOT NULL REFERENCES price_lists(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    branch_id BIGINT REFERENCES branches(id) ON DELETE CASCADE, -- NULL = precio corporativo para toda la red
    unit_price NUMERIC(14, 4) NOT NULL CHECK (unit_price >= 0), -- Precio por unidad base del producto
    valid_from DATE NOT NULL DEFAULT CURRENT_DATE,
    valid_to DATE, -- NULL = precio vigente sin fecha de término
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_price_period CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE INDEX idx_price_list_items_external_id ON price_list_items(external_id);
CREATE INDEX idx_price_list_items_lookup ON price_list_items(product_id, price_list_id, branch_id);
-- Un único precio vigente por lista + producto + sucursal, y otro a nivel corporativo
CREATE UNIQUE INDEX uq_price_current_branch ON price_list_items(price_list_id, product_id, branch_id)
    WHERE valid_to IS NULL AND branch_id IS NOT NULL;
CREATE UNIQUE INDEX uq_price_current_corporate ON price_list_items(price_list_id, product_id)
    WHERE valid_to IS NULL AND branch_id IS NULL;

-- Cada sucursal opera con una lista de precios predeterminada
ALTER TABLE branches ADD COLUMN default_price_list_id BIGINT
    REFERENCES price_lists(id) ON DELETE SET NULL;
CREATE INDEX idx_branches_price_list ON branches(default_price_list_id);

-- ============================================================================
-- 3. MÓDULO: INVENTARIO, EXISTENCIAS & KARDEX (Core Inventory)
-- ============================================================================

CREATE TABLE branch_inventories (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    current_stock NUMERIC(14, 4) NOT NULL DEFAULT 0.0000 CHECK (current_stock >= 0),
    reserved_stock NUMERIC(14, 4) NOT NULL DEFAULT 0.0000 CHECK (reserved_stock >= 0),
    in_transit_stock NUMERIC(14, 4) NOT NULL DEFAULT 0.0000 CHECK (in_transit_stock >= 0),
    min_stock_threshold NUMERIC(14, 4) NOT NULL DEFAULT 10.0000 CHECK (min_stock_threshold >= 0),
    average_cost NUMERIC(14, 4) NOT NULL DEFAULT 0.0000 CHECK (average_cost >= 0), -- Costo Promedio Ponderado (CPP)
    last_updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_branch_product UNIQUE (branch_id, product_id)
);

CREATE INDEX idx_branch_inventory_external_id ON branch_inventories(external_id);
CREATE INDEX idx_branch_inventory_branch ON branch_inventories(branch_id);
CREATE INDEX idx_branch_inventory_product ON branch_inventories(product_id);
CREATE INDEX idx_branch_inventory_critical ON branch_inventories(branch_id, current_stock, min_stock_threshold);

-- Tabla Inmutable de Kardex (Append-Only)
CREATE TABLE kardex_movements (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    movement_type VARCHAR(30) NOT NULL CHECK (
        movement_type IN (
            'PURCHASE_RECEIPT',    -- Ingreso por compra a proveedor
            'SALE',                -- Salida por venta comercial
            'TRANSFER_OUT',        -- Salida por despacho de transferencia
            'TRANSFER_IN',         -- Ingreso por recepción de transferencia
            'ADJUSTMENT_POS',      -- Ajuste manual positivo (auditoría)
            'ADJUSTMENT_NEG',      -- Ajuste manual negativo (auditoría)
            'DAMAGE_WASTE',        -- Merma, daño o caducidad
            'INITIAL_LOAD'         -- Carga inicial de stock
        )
    ),
    quantity NUMERIC(14, 4) NOT NULL CHECK (quantity > 0),
    unit_cost NUMERIC(14, 4) NOT NULL CHECK (unit_cost >= 0),
    total_cost NUMERIC(14, 4) NOT NULL CHECK (total_cost >= 0),
    previous_stock NUMERIC(14, 4) NOT NULL,
    resulting_stock NUMERIC(14, 4) NOT NULL CHECK (resulting_stock >= 0),
    reference_id VARCHAR(100),   -- External ID o código de la orden de compra, venta o transferencia
    reference_type VARCHAR(50), -- ej. 'PURCHASE_ORDER', 'SALE_INVOICE', 'TRANSFER'
    notes TEXT,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kardex_external_id ON kardex_movements(external_id);
CREATE INDEX idx_kardex_branch_product ON kardex_movements(branch_id, product_id);
CREATE INDEX idx_kardex_created_at ON kardex_movements(created_at);
CREATE INDEX idx_kardex_reference ON kardex_movements(reference_type, reference_id);

-- ============================================================================
-- 4. MÓDULO: COMPRAS Y PROVEEDORES (Purchases & Suppliers)
-- ============================================================================

CREATE TABLE suppliers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    tax_id VARCHAR(30) NOT NULL UNIQUE, -- NIT / RUC
    name VARCHAR(150) NOT NULL,
    contact_name VARCHAR(100),
    email VARCHAR(100),
    phone VARCHAR(50),
    address VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_suppliers_external_id ON suppliers(external_id);

CREATE TABLE purchase_orders (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    order_number VARCHAR(50) NOT NULL UNIQUE,
    branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
    supplier_id BIGINT NOT NULL REFERENCES suppliers(id) ON DELETE RESTRICT,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING' CHECK (
        status IN ('PENDING', 'APPROVED', 'RECEIVED', 'PARTIALLY_RECEIVED', 'CANCELLED')
    ),
    payment_terms VARCHAR(100), -- ej. 'Contado', 'Crédito 30 días'
    total_amount NUMERIC(14, 4) NOT NULL DEFAULT 0.0000 CHECK (total_amount >= 0),
    notes TEXT,
    received_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_purchases_external_id ON purchase_orders(external_id);
CREATE INDEX idx_purchases_branch ON purchase_orders(branch_id);
CREATE INDEX idx_purchases_supplier ON purchase_orders(supplier_id);
CREATE INDEX idx_purchases_status ON purchase_orders(status);

CREATE TABLE purchase_order_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    purchase_order_id BIGINT NOT NULL REFERENCES purchase_orders(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    ordered_quantity NUMERIC(14, 4) NOT NULL CHECK (ordered_quantity > 0),
    received_quantity NUMERIC(14, 4) NOT NULL DEFAULT 0.0000 CHECK (received_quantity >= 0),
    unit_cost NUMERIC(14, 4) NOT NULL CHECK (unit_cost >= 0),
    discount_percent NUMERIC(5, 2) NOT NULL DEFAULT 0.00 CHECK (discount_percent BETWEEN 0 AND 100),
    subtotal NUMERIC(14, 4) NOT NULL CHECK (subtotal >= 0)
);

CREATE INDEX idx_purchase_items_order ON purchase_order_items(purchase_order_id);

-- ============================================================================
-- 5. MÓDULO: VENTAS Y SALIDAS COMERCIALES (Sales)
-- ============================================================================

CREATE TABLE customers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    name VARCHAR(150) NOT NULL,
    tax_id VARCHAR(30),                       -- NIT / RUC; optional (D-3)
    email VARCHAR(100),
    phone VARCHAR(50),
    address VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customers_external_id ON customers(external_id);
CREATE INDEX idx_customers_name ON customers(name);
CREATE UNIQUE INDEX uq_customers_tax_id ON customers(tax_id) WHERE tax_id IS NOT NULL;

CREATE TABLE sales (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    invoice_number VARCHAR(50) NOT NULL UNIQUE,
    branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    price_list_id BIGINT NOT NULL REFERENCES price_lists(id) ON DELETE RESTRICT,
    customer_id BIGINT REFERENCES customers(id) ON DELETE RESTRICT,
    customer_name VARCHAR(150) NOT NULL,
    customer_tax_id VARCHAR(30),
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED' CHECK (status IN ('COMPLETED', 'CANCELLED')),
    subtotal NUMERIC(14, 4) NOT NULL CHECK (subtotal >= 0),
    discount_amount NUMERIC(14, 4) NOT NULL DEFAULT 0.0000 CHECK (discount_amount >= 0),
    tax_amount NUMERIC(14, 4) NOT NULL DEFAULT 0.0000 CHECK (tax_amount >= 0),
    total_amount NUMERIC(14, 4) NOT NULL CHECK (total_amount >= 0),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_sales_external_id ON sales(external_id);
CREATE INDEX idx_sales_branch_date ON sales(branch_id, created_at);
CREATE INDEX idx_sales_invoice ON sales(invoice_number);
CREATE INDEX idx_sales_customer ON sales(customer_id, created_at);

CREATE TABLE sale_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    sale_id BIGINT NOT NULL REFERENCES sales(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    quantity NUMERIC(14, 4) NOT NULL CHECK (quantity > 0),
    list_unit_price NUMERIC(14, 4) NOT NULL CHECK (list_unit_price >= 0), -- Precio de lista vigente al momento de la venta
    unit_price NUMERIC(14, 4) NOT NULL CHECK (unit_price >= 0), -- Precio efectivamente aplicado tras descuento
    discount_percent NUMERIC(5, 2) NOT NULL DEFAULT 0.00 CHECK (discount_percent BETWEEN 0 AND 100),
    subtotal NUMERIC(14, 4) NOT NULL CHECK (subtotal >= 0),
    CONSTRAINT check_applied_price_not_above_list CHECK (unit_price <= list_unit_price)
);

CREATE INDEX idx_sale_items_sale ON sale_items(sale_id);

-- ============================================================================
-- 6. MÓDULO: TRANSFERENCIAS Y LOGÍSTICA (Transfers & Logistics)
-- ============================================================================

CREATE TABLE logistics_routes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    origin_branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    destination_branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    estimated_duration_hours NUMERIC(6, 2) NOT NULL CHECK (estimated_duration_hours > 0),
    transport_cost NUMERIC(12, 2) NOT NULL DEFAULT 0.00 CHECK (transport_cost >= 0),
    priority_level VARCHAR(20) NOT NULL DEFAULT 'STANDARD' CHECK (priority_level IN ('LOW', 'STANDARD', 'URGENT')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_route_pair UNIQUE (origin_branch_id, destination_branch_id),
    CONSTRAINT check_different_branches CHECK (origin_branch_id <> destination_branch_id)
);

CREATE INDEX idx_routes_external_id ON logistics_routes(external_id);

CREATE TABLE transfers (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    transfer_number VARCHAR(50) NOT NULL UNIQUE,
    origin_branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
    destination_branch_id BIGINT NOT NULL REFERENCES branches(id) ON DELETE RESTRICT,
    requested_by_user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    dispatched_by_user_id BIGINT REFERENCES users(id) ON DELETE RESTRICT,
    received_by_user_id BIGINT REFERENCES users(id) ON DELETE RESTRICT,
    status VARCHAR(35) NOT NULL DEFAULT 'REQUESTED' CHECK (
        status IN (
            'REQUESTED',                   -- 1. Solicitada por sucursal destino
            'IN_PREPARATION',              -- 2. Aprobada / en preparación en origen
            'IN_TRANSIT',                  -- 3. Despachada (stock descontado a 'en tránsito')
            'RECEIVED',                    -- 4. Recepción completa conforme
            'RECEIVED_WITH_DISCREPANCY',   -- 5. Recepción con faltantes o daños
            'CANCELLED'                    -- 6. Cancelada antes de despacho
        )
    ),
    carrier_name VARCHAR(100),
    tracking_number VARCHAR(100),
    dispatched_at TIMESTAMPTZ,
    estimated_arrival_at TIMESTAMPTZ,
    actual_arrival_at TIMESTAMPTZ,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_transfer_different_branches CHECK (origin_branch_id <> destination_branch_id)
);

CREATE INDEX idx_transfers_external_id ON transfers(external_id);
CREATE INDEX idx_transfers_origin ON transfers(origin_branch_id);
CREATE INDEX idx_transfers_destination ON transfers(destination_branch_id);
CREATE INDEX idx_transfers_status ON transfers(status);

CREATE TABLE transfer_items (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    transfer_id BIGINT NOT NULL REFERENCES transfers(id) ON DELETE CASCADE,
    product_id BIGINT NOT NULL REFERENCES products(id) ON DELETE RESTRICT,
    requested_quantity NUMERIC(14, 4) NOT NULL CHECK (requested_quantity > 0),
    dispatched_quantity NUMERIC(14, 4) NOT NULL DEFAULT 0.0000 CHECK (dispatched_quantity >= 0),
    received_quantity NUMERIC(14, 4) NOT NULL DEFAULT 0.0000 CHECK (received_quantity >= 0),
    discrepancy_quantity NUMERIC(14, 4) NOT NULL DEFAULT 0.0000 CHECK (discrepancy_quantity >= 0),
    discrepancy_reason TEXT
);

CREATE INDEX idx_transfer_items_transfer ON transfer_items(transfer_id);

-- ============================================================================
-- 7. MÓDULO: ALERTAS DEL SISTEMA Y AUDITORÍA (Alerts & Audit)
-- ============================================================================

CREATE TABLE system_alerts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    branch_id BIGINT REFERENCES branches(id) ON DELETE CASCADE,
    alert_type VARCHAR(40) NOT NULL CHECK (
        alert_type IN ('STOCK_MINIMUM', 'LOGISTIC_DELAY', 'TRANSFER_DISCREPANCY', 'PRICE_CHANGE')
    ),
    severity VARCHAR(20) NOT NULL DEFAULT 'WARNING' CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL')),
    title VARCHAR(150) NOT NULL,
    message TEXT NOT NULL,
    is_resolved BOOLEAN NOT NULL DEFAULT FALSE,
    resolved_at TIMESTAMPTZ,
    resolved_by_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_alerts_external_id ON system_alerts(external_id);
CREATE INDEX idx_alerts_branch ON system_alerts(branch_id);
CREATE INDEX idx_alerts_resolved ON system_alerts(is_resolved);

CREATE TABLE audit_logs (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    external_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    branch_id BIGINT REFERENCES branches(id) ON DELETE SET NULL,
    action VARCHAR(50) NOT NULL, -- ej. 'CREATE_SALE', 'DISPATCH_TRANSFER', 'ADJUST_STOCK'
    entity_name VARCHAR(50) NOT NULL,
    entity_id VARCHAR(100) NOT NULL,
    payload_before JSONB,
    payload_after JSONB,
    ip_address VARCHAR(50),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_external_id ON audit_logs(external_id);
CREATE INDEX idx_audit_created_at ON audit_logs(created_at);
CREATE INDEX idx_audit_entity ON audit_logs(entity_name, entity_id);
