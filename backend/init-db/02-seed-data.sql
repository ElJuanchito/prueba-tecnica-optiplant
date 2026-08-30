-- ============================================================================
-- OPTIPLANT CONSULTORES - SISTEMA DE GESTIÓN DE INVENTARIO MULTI-SUCURSAL
-- Script Seed: 02-seed-data.sql
-- Motor: PostgreSQL 17
-- Carga de Datos Semilla con BIGINT PKs y UUID external_id
--
-- Convención de external_id en las semillas: el primer dígito hexadecimal
-- identifica la entidad. Debe ser SIEMPRE hexadecimal (0-9, a-f).
--   a = system_alerts   b = branches        c = categories   d = products
--   e = users           f = suppliers       1 = product_units
--   2 = logistics_routes  3 = transfers     4 = price_lists  5 = price_list_items
--   6 = customers
-- ============================================================================

-- Contraseña estándar para todos los usuarios semilla: 'Password123!' (BCrypt hash)
-- Hash: $2a$10$0F5tK3tdxcZ1UPXOWbQybOJdttNDQ2hWgr4GCEgnNyoFCeOo6vY.q

-- ----------------------------------------------------------------------------
-- 1. SUCURSALES (Branches)
-- ----------------------------------------------------------------------------
INSERT INTO branches (external_id, code, name, address, city, phone) VALUES
('b0000000-0000-0000-0000-000000000001', 'SUC-BOG', 'Sucursal Central Bogotá', 'Av. El Dorado #68-90, Zona Industrial', 'Bogotá D.C.', '+57 601 7458900'),
('b0000000-0000-0000-0000-000000000002', 'SUC-MED', 'Sucursal Norte Medellín', 'Autopista Norte km 12, Parque Logístico', 'Medellín', '+57 604 4482310'),
('b0000000-0000-0000-0000-000000000003', 'SUC-CAL', 'Sucursal Occidente Cali', 'Calle 15 #28-40, Acopi Yumbo', 'Cali', '+57 602 6691122');

-- ----------------------------------------------------------------------------
-- 2. USUARIOS Y ROLES (Users & RBAC)
-- ----------------------------------------------------------------------------
INSERT INTO users (external_id, branch_id, username, email, password_hash, full_name, role) VALUES
-- Administrador Corporativo Global (sin sucursal fija)
('e0000000-0000-0000-0000-000000000001', NULL, 'admin.corp', 'admin@optiplant.com', '$2a$10$0F5tK3tdxcZ1UPXOWbQybOJdttNDQ2hWgr4GCEgnNyoFCeOo6vY.q', 'Carlos Mendoza (Admin Global)', 'ADMIN'),

-- Gerentes de Sucursal
('e0000000-0000-0000-0000-000000000002', 1, 'gerente.bogota', 'gerente.bog@optiplant.com', '$2a$10$0F5tK3tdxcZ1UPXOWbQybOJdttNDQ2hWgr4GCEgnNyoFCeOo6vY.q', 'Adriana Morales (Gerente Bogotá)', 'BRANCH_MANAGER'),
('e0000000-0000-0000-0000-000000000003', 2, 'gerente.medellin', 'gerente.med@optiplant.com', '$2a$10$0F5tK3tdxcZ1UPXOWbQybOJdttNDQ2hWgr4GCEgnNyoFCeOo6vY.q', 'Felipe Gómez (Gerente Medellín)', 'BRANCH_MANAGER'),
('e0000000-0000-0000-0000-000000000004', 3, 'gerente.cali', 'gerente.cal@optiplant.com', '$2a$10$0F5tK3tdxcZ1UPXOWbQybOJdttNDQ2hWgr4GCEgnNyoFCeOo6vY.q', 'Lucía Valencia (Gerente Cali)', 'BRANCH_MANAGER'),

-- Operadores de Inventario
('e0000000-0000-0000-0000-000000000005', 1, 'operador.bogota', 'operador.bog@optiplant.com', '$2a$10$0F5tK3tdxcZ1UPXOWbQybOJdttNDQ2hWgr4GCEgnNyoFCeOo6vY.q', 'Juan Pérez (Operador Bogotá)', 'OPERATOR'),
('e0000000-0000-0000-0000-000000000006', 2, 'operador.medellin', 'operador.med@optiplant.com', '$2a$10$0F5tK3tdxcZ1UPXOWbQybOJdttNDQ2hWgr4GCEgnNyoFCeOo6vY.q', 'Mateo Henao (Operador Medellín)', 'OPERATOR'),
('e0000000-0000-0000-0000-000000000007', 3, 'operador.cali', 'operador.cal@optiplant.com', '$2a$10$0F5tK3tdxcZ1UPXOWbQybOJdttNDQ2hWgr4GCEgnNyoFCeOo6vY.q', 'Sofía Caicedo (Operador Cali)', 'OPERATOR');

-- ----------------------------------------------------------------------------
-- 3. CATÁLOGO MAESTRO (Categories, Products & Units)
-- ----------------------------------------------------------------------------
INSERT INTO categories (external_id, name, description) VALUES
('c0000000-0000-0000-0000-000000000001', 'Fertilizantes y Nutrición Vegetal', 'Nutrientes de base, fertilizantes NPK, foliares y bioestimulantes'),
('c0000000-0000-0000-0000-000000000002', 'Semillas y Material Genético', 'Semillas certificadas de alta pureza y rendimiento agrícola'),
('c0000000-0000-0000-0000-000000000003', 'Protección de Cultivos', 'Fungicidas, bioinsecticidas y control biológico ecológico'),
('c0000000-0000-0000-0000-000000000004', 'Sistemas de Riego e Insumos', 'Mangueras de goteo, aspersores, filtros y conectores de alta presión');

INSERT INTO products (external_id, category_id, sku, name, description, base_unit) VALUES
('d0000000-0000-0000-0000-000000000001', 1, 'FERT-NPK-151515', 'Fertilizante Triple 15 (NPK 15-15-15)', 'Saco granulado para nutrición balanceada de cultivos', 'KG'),
('d0000000-0000-0000-0000-000000000002', 1, 'BIO-FOL-AMINO', 'Bioestimulante Foliar AminoPlus', 'Concentrado orgánico con aminoácidos libres para estrés hídrico', 'LITRO'),
('d0000000-0000-0000-0000-000000000003', 2, 'SEM-MAIZ-H300', 'Semilla de Maíz Híbrido H-300', 'Bolsa de semilla tratada de alta germinación', 'BOLSA_80K_SEM'),
('d0000000-0000-0000-0000-000000000004', 3, 'FUNG-BIO-TRICH', 'Biofungicida Trichoderma Viride', 'Inoculante biológico preventivo de pudrición radicular', 'KG'),
('d0000000-0000-0000-0000-000000000005', 4, 'RIEGO-MANG-16MM', 'Manguera de Riego por Goteo 16mm (Rollo 500m)', 'Tubería de polietileno con goteros autocompensados cada 20cm', 'ROLLO');

INSERT INTO product_units (external_id, product_id, unit_name, conversion_factor, is_default_sale_unit) VALUES
('10000000-0000-0000-0000-000000000001', 1, 'SACO_50KG', 50.0000, TRUE),
('10000000-0000-0000-0000-000000000002', 1, 'BULTITO_10KG', 10.0000, FALSE),
('10000000-0000-0000-0000-000000000003', 2, 'GALON_4L', 4.0000, TRUE),
('10000000-0000-0000-0000-000000000004', 2, 'CANECAS_20L', 20.0000, FALSE),
('10000000-0000-0000-0000-000000000005', 3, 'BOLSA_INDIVIDUAL', 1.0000, TRUE),
('10000000-0000-0000-0000-000000000006', 4, 'PAQUETE_1KG', 1.0000, TRUE),
('10000000-0000-0000-0000-000000000007', 5, 'ROLLO_500M', 1.0000, TRUE);

-- ----------------------------------------------------------------------------
-- 3.B LISTAS DE PRECIOS COMERCIALES (Commercial Price Lists)
-- ----------------------------------------------------------------------------
INSERT INTO price_lists (external_id, code, name, description, max_discount_percent, is_default) VALUES
('40000000-0000-0000-0000-000000000001', 'RETAIL', 'Lista Minorista', 'Precio de venta al publico en mostrador de sucursal', 10.00, TRUE),
('40000000-0000-0000-0000-000000000002', 'WHOLESALE', 'Lista Mayorista', 'Precio para distribuidores y compras por volumen', 20.00, FALSE),
('40000000-0000-0000-0000-000000000003', 'INSTITUTIONAL', 'Lista Institucional', 'Precio para convenios con cooperativas y entidades agricolas', 25.00, FALSE);

-- Toda la red opera por defecto con la lista minorista
UPDATE branches SET default_price_list_id = 1;

-- Precios corporativos (branch_id NULL): aplican a toda la red salvo excepcion por sucursal.
-- El precio se expresa siempre en la unidad base del producto.
INSERT INTO price_list_items (external_id, price_list_id, product_id, branch_id, unit_price) VALUES
-- Lista Minorista
('50000000-0000-0000-0000-000000000001', 1, 1, NULL,     4200.0000),
('50000000-0000-0000-0000-000000000002', 1, 2, NULL,    68000.0000),
('50000000-0000-0000-0000-000000000003', 1, 3, NULL,   395000.0000),
('50000000-0000-0000-0000-000000000004', 1, 4, NULL,    52000.0000),
('50000000-0000-0000-0000-000000000005', 1, 5, NULL,  1180000.0000),
-- Lista Mayorista (aproximadamente 12% por debajo del minorista)
('50000000-0000-0000-0000-000000000006', 2, 1, NULL,     3700.0000),
('50000000-0000-0000-0000-000000000007', 2, 2, NULL,    59800.0000),
('50000000-0000-0000-0000-000000000008', 2, 3, NULL,   347000.0000),
('50000000-0000-0000-0000-000000000009', 2, 4, NULL,    45800.0000),
('50000000-0000-0000-0000-00000000000a', 2, 5, NULL,  1038000.0000),
-- Lista Institucional (convenios de volumen)
('50000000-0000-0000-0000-00000000000b', 3, 1, NULL,     3450.0000),
('50000000-0000-0000-0000-00000000000c', 3, 3, NULL,   322000.0000),
('50000000-0000-0000-0000-00000000000d', 3, 5, NULL,   975000.0000),
-- Excepciones por sucursal: Cali sostiene un precio menor por presion competitiva
('50000000-0000-0000-0000-00000000000e', 1, 1, 3,        3980.0000),
('50000000-0000-0000-0000-00000000000f', 1, 4, 3,       49500.0000);

-- Historico de precios: version anterior del NPK minorista, ya vencida
INSERT INTO price_list_items (external_id, price_list_id, product_id, branch_id, unit_price, valid_from, valid_to) VALUES
('50000000-0000-0000-0000-000000000010', 1, 1, NULL, 3900.0000, DATE '2026-01-01', DATE '2026-06-30');

-- ----------------------------------------------------------------------------
-- 4. PROVEEDORES (Suppliers)
-- ----------------------------------------------------------------------------
INSERT INTO suppliers (external_id, tax_id, name, contact_name, email, phone, address) VALUES
('f0000000-0000-0000-0000-000000000001', '900.123.456-7', 'Agroquímicos & Fertilizantes de Colombia S.A.', 'Mauricio Arango', 'ventas@agrofertil.com.co', '+57 601 3209800', 'Zona Franca Fontibón, Bogotá'),
('f0000000-0000-0000-0000-000000000002', '890.987.654-3', 'Biotecnología & Semillas Andinas Ltda.', 'Claudia Restrepo', 'contacto@biosemillas.com', '+57 604 5557788', 'Km 5 Vía Rionegro, Antioquia'),
('f0000000-0000-0000-0000-000000000003', '800.333.222-1', 'Sistemas de Riego del Pacífico S.A.S.', 'Hernando Caicedo', 'pedidos@riegopacifico.com', '+57 602 8884400', 'Parque Industrial Yumbo, Valle');

-- ----------------------------------------------------------------------------
-- 5. INVENTARIO INICIAL POR SUCURSAL (Branch Inventories)
-- ----------------------------------------------------------------------------
INSERT INTO branch_inventories (branch_id, product_id, current_stock, reserved_stock, in_transit_stock, min_stock_threshold, average_cost) VALUES
-- SUCURSAL BOGOTÁ (id: 1)
(1, 1, 5000.0000, 0.0000, 0.0000, 500.0000, 3200.0000), -- 5.000 KG de NPK
(1, 2, 450.0000, 0.0000, 0.0000, 50.0000, 48000.0000), -- 450 L de Foliar
(1, 3, 120.0000, 0.0000, 0.0000, 20.0000, 380000.0000), -- 120 Bolsas Maíz
(1, 4, 300.0000, 0.0000, 0.0000, 40.0000, 65000.0000),  -- 300 KG Trichoderma
(1, 5, 85.0000, 0.0000, 0.0000, 15.0000, 210000.0000),  -- 85 Rollos

-- SUCURSAL MEDELLÍN (id: 2)
(2, 1, 2500.0000, 0.0000, 0.0000, 400.0000, 3250.0000),
(2, 2, 180.0000, 0.0000, 0.0000, 40.0000, 48500.0000),
(2, 3, 12.0000, 0.0000, 0.0000, 25.0000, 385000.0000), -- CRÍTICO: 12 < 25
(2, 4, 150.0000, 0.0000, 0.0000, 30.0000, 66000.0000),
(2, 5, 40.0000, 0.0000, 0.0000, 10.0000, 215000.0000),

-- SUCURSAL CALI (id: 3)
(3, 1, 1800.0000, 0.0000, 0.0000, 300.0000, 3280.0000),
(3, 2, 90.0000, 0.0000, 0.0000, 30.0000, 49000.0000),
(3, 3, 5.0000, 0.0000, 30.0000, 20.0000, 385000.0000), -- 5 disp + 30 en tránsito
(3, 4, 80.0000, 0.0000, 0.0000, 25.0000, 66500.0000),
(3, 5, 25.0000, 0.0000, 0.0000, 8.0000, 218000.0000);

-- ----------------------------------------------------------------------------
-- 6. REGISTRO DE KARDEX INICIAL (Inmutable Append-Only Log)
-- ----------------------------------------------------------------------------
INSERT INTO kardex_movements (branch_id, product_id, movement_type, quantity, unit_cost, total_cost, previous_stock, resulting_stock, reference_id, reference_type, notes, user_id)
SELECT 
    bi.branch_id,
    bi.product_id,
    'INITIAL_LOAD',
    bi.current_stock,
    bi.average_cost,
    (bi.current_stock * bi.average_cost),
    0.0000,
    bi.current_stock,
    'INIT-LOAD-2026',
    'SYSTEM_INITIALIZATION',
    'Carga de inventario base de arranque del sistema',
    1
FROM branch_inventories bi;

-- ----------------------------------------------------------------------------
-- 7. RUTAS LOGÍSTICAS (Logistics Routes)
-- ----------------------------------------------------------------------------
INSERT INTO logistics_routes (external_id, origin_branch_id, destination_branch_id, estimated_duration_hours, transport_cost, priority_level) VALUES
-- Bogotá (1) <-> Medellín (2)
('20000000-0000-0000-0000-000000000001', 1, 2, 8.50, 450000.00, 'STANDARD'),
('20000000-0000-0000-0000-000000000002', 2, 1, 8.50, 450000.00, 'STANDARD'),

-- Bogotá (1) <-> Cali (3)
('20000000-0000-0000-0000-000000000003', 1, 3, 10.00, 520000.00, 'URGENT'),
('20000000-0000-0000-0000-000000000004', 3, 1, 10.00, 520000.00, 'STANDARD'),

-- Medellín (2) <-> Cali (3)
('20000000-0000-0000-0000-000000000005', 2, 3, 9.00, 480000.00, 'STANDARD'),
('20000000-0000-0000-0000-000000000006', 3, 2, 9.00, 480000.00, 'STANDARD');

-- ----------------------------------------------------------------------------
-- 8. TRANSFERENCIA DEMOSTRATIVA EN TRÁNSITO (Transfer Life-Cycle)
-- ----------------------------------------------------------------------------
INSERT INTO transfers (
    external_id, transfer_number, origin_branch_id, destination_branch_id,
    requested_by_user_id, dispatched_by_user_id, status,
    carrier_name, tracking_number, dispatched_at, estimated_arrival_at, notes
) VALUES (
    '30000000-0000-0000-0000-000000000001',
    'TRF-2026-0001',
    1, -- Origen: Bogotá
    3, -- Destino: Cali
    7, -- Sofía (Cali)
    5, -- Juan (Bogotá)
    'IN_TRANSIT',
    'Servientrega Carga Agrícola',
    'TRK-9988776655',
    CURRENT_TIMESTAMP - INTERVAL '4 hours',
    CURRENT_TIMESTAMP + INTERVAL '6 hours',
    'Reabastecimiento urgente de semilla de maíz para siembra de temporada'
);

INSERT INTO transfer_items (
    transfer_id, product_id, requested_quantity, dispatched_quantity, received_quantity
) VALUES (
    1,
    3, -- Semilla Maíz Híbrido
    30.0000,
    30.0000,
    0.0000
);

-- ----------------------------------------------------------------------------
-- 9. ALERTAS INTELIGENTES INICIALES (System Alerts RF-VAL-01)
-- ----------------------------------------------------------------------------
INSERT INTO system_alerts (external_id, branch_id, alert_type, severity, title, message, is_resolved) VALUES
(
    'a0000000-0000-0000-0000-000000000001',
    2, -- Medellín
    'STOCK_MINIMUM',
    'CRITICAL',
    'Stock Crítico: Semilla Maíz H-300',
    'La sucursal Medellín tiene 12 bolsas disponibles (umbral mínimo configurado: 25 bolsas). Se recomienda solicitar transferencia desde Bogotá.',
    FALSE
),
(
    'a0000000-0000-0000-0000-000000000002',
    3, -- Cali
    'STOCK_MINIMUM',
    'WARNING',
    'Stock en Nivel de Alerta: Semilla Maíz H-300',
    'La sucursal Cali opera con 5 bolsas en almacén local mientras la transferencia TRF-2026-0001 se encuentra en tránsito.',
    FALSE
);

-- ----------------------------------------------------------------------------
-- 10. CLIENTES (Customers)
-- ----------------------------------------------------------------------------
INSERT INTO customers (external_id, name, tax_id, email, phone, address, is_active) VALUES
('60000000-0000-0000-0000-000000000001', 'Agropecuaria El Progreso S.A.S.', '900.555.444-1', 'contacto@elprogreso.com.co', '+57 601 5551234', 'Vereda El Salitre, Finca La Esperanza', TRUE),
('60000000-0000-0000-0000-000000000002', 'Juan Camilo Morales', NULL, 'juan.morales@gmail.com', '+57 310 9876543', 'Calle 45 #12-34, Bogotá', TRUE),
('60000000-0000-0000-0000-000000000003', 'Hacienda San José Inactiva Ltda.', '800.777.666-5', 'admin@haciendasanjose.com', '+57 604 7778899', 'Km 10 Vía Llanogrande, Antioquia', FALSE);

