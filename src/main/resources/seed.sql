INSERT INTO categories (name) VALUES ('Мыши'), ('Клавиатуры'), ('Наушники');

INSERT INTO products (category_id, name, description, price, stock)
SELECT id, 'Logitech G PRO X SUPERLIGHT 2', 'Беспроводная игровая мышь с сенсором HERO 2, переключателями LIGHTFORCE и подключением LIGHTSPEED. Вес — 60 г.', 79990.00, 8
FROM categories WHERE name = 'Мыши';

INSERT INTO products (category_id, name, description, price, stock)
SELECT id, 'VXE Dragonfly R1 Pro', 'Лёгкая беспроводная мышь с сенсором PAW3395. Подключение по радиоканалу 2,4 ГГц и USB, вес — 48 г.', 24990.00, 12
FROM categories WHERE name = 'Мыши';

INSERT INTO products (category_id, name, description, price, stock)
SELECT id, 'VGN Dragonfly F1 Pro', 'Игровая мышь с сенсором PAW3395 и контроллером Nordic. Радиоканал 2,4 ГГц и проводное подключение.', 27990.00, 4
FROM categories WHERE name = 'Мыши';

INSERT INTO products (category_id, name, description, price, stock)
SELECT id, 'Logitech G515 LIGHTSPEED TKL', 'Низкопрофильная механическая клавиатура без цифрового блока. LIGHTSPEED, Bluetooth и USB, подсветка LIGHTSYNC RGB.', 74990.00, 5
FROM categories WHERE name = 'Клавиатуры';

INSERT INTO products (category_id, name, description, price, stock)
SELECT id, 'VGN V87 V2', 'Механическая TKL-клавиатура с горячей заменой переключателей, RGB и gasket-конструкцией. USB, Bluetooth и 2,4 ГГц.', 29990.00, 7
FROM categories WHERE name = 'Клавиатуры';

INSERT INTO products (category_id, name, description, price, stock)
SELECT id, 'VGN S99', 'Механическая клавиатура формата 98% с цифровым блоком. Горячая замена переключателей, RGB и три способа подключения.', 39990.00, 6
FROM categories WHERE name = 'Клавиатуры';

INSERT INTO products (category_id, name, description, price, stock)
SELECT id, 'Logitech G PRO X 2 LIGHTSPEED', 'Беспроводная гарнитура с 50-мм графеновыми динамиками и съёмным микрофоном. LIGHTSPEED, Bluetooth и разъём 3,5 мм.', 119990.00, 5
FROM categories WHERE name = 'Наушники';

INSERT INTO products (category_id, name, description, price, stock)
SELECT id, 'HyperX Cloud III', 'Проводная игровая гарнитура с 53-мм динамиками и съёмным микрофоном. Подключение через 3,5 мм, USB-C и USB-A.', 49990.00, 9
FROM categories WHERE name = 'Наушники';

INSERT INTO products (category_id, name, description, price, stock)
SELECT id, 'Logitech G435 LIGHTSPEED', 'Лёгкая беспроводная гарнитура весом 165 г. Подключение LIGHTSPEED и Bluetooth, встроенные направленные микрофоны.', 32990.00, 3
FROM categories WHERE name = 'Наушники';
