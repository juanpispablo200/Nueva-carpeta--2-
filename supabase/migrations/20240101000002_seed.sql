-- ============================================================
-- JAGOO DELIVERY — Datos iniciales (Seed)
-- Archivo: 002_seed.sql
-- Ejecutar DESPUÉS de 001_schema.sql
-- Contiene: 7 restaurantes y 25 platos del mock de la app
-- ============================================================

SET client_encoding = 'UTF8';

-- ============================================================
-- RESTAURANTES (7)
-- ============================================================
INSERT INTO public.restaurants
    (id, name, category, description, logo_url, cover_url,
     brand_color, rating, review_count, delivery_time,
     delivery_fee, min_order, is_open, is_featured, is_offer, offer_text)
VALUES

('r1',
 'Brasa & Pan',
 'Hamburguesas',
 'Hamburguesas artesanales 100% carne de res a la parrilla de carbón.',
 'https://images.unsplash.com/photo-1550547660-d9450f859349?w=300',
 'https://images.unsplash.com/photo-1586190848861-99aa4a171e90?w=800',
 '#D32F2F', 4.8, 340, '25-35 min', 1.50, 6.00, TRUE, TRUE, TRUE,
 '20% OFF en combos'),

('r2',
 'Casa Nonna',
 'Pizza & Pastas',
 'Pastas frescas hechas a mano y pizzas en horno de piedra estilo italiano.',
 'https://images.unsplash.com/photo-1579684947550-22e945225d9a?w=300',
 'https://images.unsplash.com/photo-1555396273-367ea4eb4db5?w=800',
 '#388E3C', 4.7, 215, '30-40 min', 2.00, 8.00, TRUE, TRUE, FALSE,
 NULL),

('r3',
 'Café Tertulia',
 'Café & Postres',
 'Especialistas en cafés de origen ecuatoriano, repostería y desayunos.',
 'https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?w=300',
 'https://images.unsplash.com/photo-1501339847302-ac426a4a7cbb?w=800',
 '#6D4C41', 4.9, 412, '15-25 min', 1.25, 4.00, TRUE, FALSE, TRUE,
 'Envío Gratis desde $12'),

('r4',
 'Sabor Lojano',
 'Comida Típica',
 'Auténticos sabores del sur del Ecuador: cecina, repe y tamales lojanos.',
 'https://images.unsplash.com/photo-1565299624946-b28f40a0ae38?w=300',
 'https://images.unsplash.com/photo-1544025162-d76694265947?w=800',
 '#F57C00', 4.6, 180, '25-35 min', 1.75, 5.00, TRUE, TRUE, FALSE,
 NULL),

('r5',
 'Sushi Kenzo',
 'Sushi & Ramen',
 'Rolls gourmet, nigiris frescos y ramen reconfortante de preparación al instante.',
 'https://images.unsplash.com/photo-1611143669185-af224c5e3252?w=300',
 'https://images.unsplash.com/photo-1579871494447-9811cf80d66c?w=800',
 '#C62828', 4.8, 290, '35-45 min', 2.25, 10.00, TRUE, FALSE, TRUE,
 'Roll gratis > $20'),

('r6',
 'Pollos Don Chicho',
 'Pollo & Parrilla',
 'El mejor pollo broaster crocante y alitas barbecue con receta familiar secreta.',
 'https://images.unsplash.com/photo-1598515214211-89d3c73ae83b?w=300',
 'https://images.unsplash.com/photo-1626645738196-c2a7c87a8f58?w=800',
 '#E65100', 4.5, 520, '20-30 min', 1.50, 5.00, TRUE, FALSE, FALSE,
 NULL),

('r7',
 'Green Bowl',
 'Saludable',
 'Bowls nutritivos, ensaladas de estación y smoothies 100% naturales.',
 'https://images.unsplash.com/photo-1540420773420-3366772f4999?w=300',
 'https://images.unsplash.com/photo-1512621776951-a57141f2eefd?w=800',
 '#2E7D32', 4.7, 145, '20-30 min', 1.50, 6.00, TRUE, FALSE, FALSE,
 NULL)

ON CONFLICT (id) DO NOTHING;


-- ============================================================
-- SECCIONES DEL MENÚ
-- ============================================================
INSERT INTO public.menu_sections (id, restaurant_id, name, sort_order) VALUES
('ms_r1_1', 'r1', 'Hamburguesas',      1),
('ms_r1_2', 'r1', 'Acompañantes',      2),
('ms_r1_3', 'r1', 'Bebidas & Postres', 3),
('ms_r2_1', 'r2', 'Pastas',            1),
('ms_r2_2', 'r2', 'Pizzas',            2),
('ms_r2_3', 'r2', 'Postres',           3),
('ms_r3_1', 'r3', 'Cafetería',         1),
('ms_r3_2', 'r3', 'Bebidas Frías',     2),
('ms_r3_3', 'r3', 'Panadería',         3),
('ms_r3_4', 'r3', 'Postres',           4),
('ms_r4_1', 'r4', 'Sopas & Entradas',  1),
('ms_r4_2', 'r4', 'Entradas',          2),
('ms_r4_3', 'r4', 'Platos Fuertes',    3),
('ms_r5_1', 'r5', 'Rolls Especiales',  1),
('ms_r5_2', 'r5', 'Nigiris & Sashimi', 2),
('ms_r5_3', 'r5', 'Ramen',             3),
('ms_r6_1', 'r6', 'Pollo Broaster',    1),
('ms_r6_2', 'r6', 'Alitas',            2),
('ms_r7_1', 'r7', 'Bowls Nutritivos',  1),
('ms_r7_2', 'r7', 'Smoothies',         2)
ON CONFLICT (id) DO NOTHING;


-- ============================================================
-- PLATOS (25)
-- option_groups se guarda como JSON igual que en la app
-- ============================================================

-- ── Brasa & Pan (r1) ─────────────────────────────────────────
INSERT INTO public.dishes
    (id, restaurant_id, restaurant_name, section_name, name, description,
     price, image_url, is_popular, is_offer, offer_badge, option_groups)
VALUES
('d1', 'r1', 'Brasa & Pan', 'Hamburguesas',
 'Clásica Brasa',
 'Carne a la parrilla (150g), queso cheddar fundido, lechuga fresca, tomate y salsa artesanal de la casa.',
 6.50,
 'https://images.unsplash.com/photo-1568901346375-23c9450c58cd?w=500',
 TRUE, FALSE, NULL,
 '[
   {"id":"og1","title":"Término de la carne","isRequired":true,"maxSelection":1,
    "options":[
      {"id":"opt1","name":"Término Medio","price":0},
      {"id":"opt2","name":"Bien Cocida","price":0},
      {"id":"opt3","name":"Tres Cuartos","price":0}
    ]},
   {"id":"og2","title":"Extras recomendados","isRequired":false,"maxSelection":3,
    "options":[
      {"id":"opt4","name":"Extra Queso Cheddar","price":1.00},
      {"id":"opt5","name":"Tocino Crujiente","price":1.50},
      {"id":"opt6","name":"Huevo Frito","price":0.80}
    ]}
 ]'::jsonb),

('d2', 'r1', 'Brasa & Pan', 'Hamburguesas',
 'Doble Queso & Tocino',
 'Doble carne vacuna (300g), doble capa de cheddar, tocino ahumado crujiente y cebolla caramelizada.',
 8.90,
 'https://images.unsplash.com/photo-1586190848861-99aa4a171e90?w=500',
 TRUE, TRUE, 'Popular', '[]'::jsonb),

('d3', 'r1', 'Brasa & Pan', 'Hamburguesas',
 'BBQ Crispy Burger',
 'Carne de res, aros de cebolla empanizados, queso americano fundido y generosa salsa BBQ ahumada.',
 7.90,
 'https://images.unsplash.com/photo-1550547660-d9450f859349?w=500',
 FALSE, FALSE, NULL, '[]'::jsonb),

('d4', 'r1', 'Brasa & Pan', 'Acompañantes',
 'Papas Rústicas sazonadas',
 'Papas gruesas cortadas a mano con pimentón ahumado, sal de mar y dip de mayonesa de ajo.',
 3.20,
 'https://images.unsplash.com/photo-1573080496219-bb080dd4f877?w=500',
 TRUE, FALSE, NULL, '[]'::jsonb),

('d5', 'r1', 'Brasa & Pan', 'Bebidas & Postres',
 'Malteada de Vainilla & Oreo',
 'Helado cremoso de vainilla batido con trozos de galleta OREO y crema batida encima.',
 4.00,
 'https://images.unsplash.com/photo-1572490122747-3968b75cc699?w=500',
 FALSE, FALSE, NULL, '[]'::jsonb),

-- ── Casa Nonna (r2) ───────────────────────────────────────────
('d6', 'r2', 'Casa Nonna', 'Pastas',
 'Fetuccini Alfredo con Pollo',
 'Pasta fresca feta en casa salteada en salsa cremosa Alfredo de mantequilla, crema y queso Parmigiano.',
 9.50,
 'https://images.unsplash.com/photo-1645112411341-6c4fd023714a?w=500',
 TRUE, FALSE, NULL, '[]'::jsonb),

('d7', 'r2', 'Casa Nonna', 'Pastas',
 'Lasaña Bolognesa Gratinada',
 'Capas de pasta artesanal intercaladas con abundante salsa Bolognesa de res, bechamel y mozzarella.',
 10.50,
 'https://images.unsplash.com/photo-1574894709920-11b28e7367e3?w=500',
 TRUE, FALSE, NULL, '[]'::jsonb),

('d8', 'r2', 'Casa Nonna', 'Pizzas',
 'Pizza Margarita Italiana',
 'Masa madre de 48h de fermentación, salsa de tomate San Marzano, mozzarella fior di latte y albahaca.',
 11.00,
 'https://images.unsplash.com/photo-1604382354936-07c5d9983bd3?w=500',
 TRUE, TRUE, 'Chef', '[]'::jsonb),

('d9', 'r2', 'Casa Nonna', 'Postres',
 'Tiramisú Tradicional',
 'Bizcocho savoiardi humedecido en café espresso con licor Amaretto, capas de mascarpone y cacao.',
 4.50,
 'https://images.unsplash.com/photo-1571877227200-a0d98ea607e9?w=500',
 FALSE, FALSE, NULL, '[]'::jsonb),

-- ── Café Tertulia (r3) ────────────────────────────────────────
('d10', 'r3', 'Café Tertulia', 'Cafetería',
 'Capuchino Especial Tertulia',
 'Espresso doble de grano lojano tostado medio, leche cremada sedosa y toque de cacao en polvo.',
 3.00,
 'https://images.unsplash.com/photo-1534778101976-62847782c213?w=500',
 TRUE, FALSE, NULL, '[]'::jsonb),

('d11', 'r3', 'Café Tertulia', 'Cafetería',
 'Latte de Vainilla Natural',
 'Espresso balanceado con leche al vapor perfumada con esencia pura de vainilla.',
 3.50,
 'https://images.unsplash.com/photo-1461023058943-07fcbe16d735?w=500',
 FALSE, FALSE, NULL, '[]'::jsonb),

('d12', 'r3', 'Café Tertulia', 'Bebidas Frías',
 'Cold Brew Nitro',
 'Café infusionado en frío durante 16 horas con cascada nitrogenada para una textura suave y dulce.',
 3.80,
 'https://images.unsplash.com/photo-1517701604599-bb29b565090c?w=500',
 TRUE, FALSE, NULL, '[]'::jsonb),

('d13', 'r3', 'Café Tertulia', 'Panadería',
 'Croissant de Mantequilla',
 'Croissant hojaldrado francés horneado cada mañana con mantequilla de vaca seleccionada.',
 2.20,
 'https://images.unsplash.com/photo-1555507036-ab1f4038808a?w=500',
 FALSE, FALSE, NULL, '[]'::jsonb),

('d14', 'r3', 'Café Tertulia', 'Postres',
 'Cheesecake de Frutos Rojos',
 'Tarta cremosa sobre galleta horneada, decorada con salsa casera de mora, frutilla y arándanos.',
 4.20,
 'https://images.unsplash.com/photo-1533134242443-d4fd215305ad?w=500',
 TRUE, TRUE, 'Top Postre', '[]'::jsonb),

-- ── Sabor Lojano (r4) ─────────────────────────────────────────
('d15', 'r4', 'Sabor Lojano', 'Sopas & Entradas',
 'Repe Lojano Tradicional',
 'Sopa emblemática de plátano verde rallado, quesillo fresco de la sierra y cilantro tierno.',
 4.00,
 'https://images.unsplash.com/photo-1547592166-23ac45744acd?w=500',
 TRUE, FALSE, NULL, '[]'::jsonb),

('d16', 'r4', 'Sabor Lojano', 'Platos Fuertes',
 'Cecina Lojana con Yuca',
 'Carne de cerdo marinado seco al humo, acompañada de yucas al vapor, encurtido de cebolla y aguacate.',
 8.50,
 'https://images.unsplash.com/photo-1544025162-d76694265947?w=500',
 TRUE, FALSE, NULL, '[]'::jsonb),

('d17', 'r4', 'Sabor Lojano', 'Entradas',
 'Tamal Lojano de Pollo',
 'Masa de maíz criollo rellena de guiso de pollo, arvejas y huevo duro, envuelta en hoja de achira.',
 2.80,
 'https://images.unsplash.com/photo-1626777552726-4a6b54c97e46?w=500',
 FALSE, FALSE, NULL, '[]'::jsonb),

('d18', 'r4', 'Sabor Lojano', 'Platos Fuertes',
 'Hornado Lojano Festivo',
 'Jugoso cerdo asado al horno de leña, servido con llapingachos, mote caliente y agrio casero.',
 9.00,
 'https://images.unsplash.com/photo-1555939594-58d7cb561ad1?w=500',
 TRUE, TRUE, 'Típico', '[]'::jsonb),

-- ── Sushi Kenzo (r5) ──────────────────────────────────────────
('d19', 'r5', 'Sushi Kenzo', 'Rolls Especiales',
 'Kenzo Special Roll (10 bocados)',
 'Relleno de camarón empanizado y palta, cubierto con láminas de salmón fresco y salsa acevichada picante.',
 11.50,
 'https://images.unsplash.com/photo-1579871494447-9811cf80d66c?w=500',
 TRUE, TRUE, 'Favorito', '[]'::jsonb),

('d20', 'r5', 'Sushi Kenzo', 'Nigiris & Sashimi',
 'Combo Nigiris Variados (8 Pzs)',
 'Selección del chef con nigiris de salmón, atún rojo, langostino y pesca blanca del día.',
 12.00,
 'https://images.unsplash.com/photo-1611143669185-af224c5e3252?w=500',
 FALSE, FALSE, NULL, '[]'::jsonb),

('d21', 'r5', 'Sushi Kenzo', 'Ramen',
 'Tonkotsu Ramen de Cerdo',
 'Fideos frescos en caldo denso de cerdo cocido 12h, con panceta chashu, huevo marinado y alga nori.',
 10.50,
 'https://images.unsplash.com/photo-1569718212165-3a8278d5f624?w=500',
 TRUE, FALSE, NULL, '[]'::jsonb),

-- ── Pollos Don Chicho (r6) ────────────────────────────────────
('d22', 'r6', 'Pollos Don Chicho', 'Pollo Broaster',
 'Combo Broaster 4 Presas',
 '4 presas de pollo crujiente doradas a la perfección, acompañadas de papas fritas y ensalada coleslaw.',
 7.50,
 'https://images.unsplash.com/photo-1626645738196-c2a7c87a8f58?w=500',
 TRUE, FALSE, NULL, '[]'::jsonb),

('d23', 'r6', 'Pollos Don Chicho', 'Alitas',
 'Alitas BBQ o Búfalo (12 Pzs)',
 'Alitas jugosas a la parrilla bañadas en salsa a elección (salsa BBQ ahumada o Búfalo picante).',
 8.90,
 'https://images.unsplash.com/photo-1527477396000-e27163b481c2?w=500',
 TRUE, TRUE, 'Combo Alitas', '[]'::jsonb),

-- ── Green Bowl (r7) ───────────────────────────────────────────
('d24', 'r7', 'Green Bowl', 'Bowls Nutritivos',
 'Bowl de Quinoa & Salmón Teriyaki',
 'Quinoa orgánica, salmón fresco a la plancha, palta, edamame, rodajas de pepino y vinagreta de sésamo.',
 9.80,
 'https://images.unsplash.com/photo-1546069901-ba9599a7e63c?w=500',
 TRUE, FALSE, NULL, '[]'::jsonb),

('d25', 'r7', 'Green Bowl', 'Smoothies',
 'Smoothie Acai Berry Energy',
 'Batido denso de acai orgánico, plátano, arándanos, leche de coco y semillas de chía arriba.',
 4.50,
 'https://images.unsplash.com/photo-1553530666-ba11a7da3888?w=500',
 FALSE, FALSE, NULL, '[]'::jsonb)

ON CONFLICT (id) DO NOTHING;


-- ============================================================
-- FIN DEL SEED
-- ============================================================
