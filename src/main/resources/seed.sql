INSERT INTO categories (name) VALUES ('Мыши'), ('Клавиатуры'), ('Наушники');

INSERT INTO products (category_id, name, description, price, stock)
SELECT id, 'Мышь M1', 'Проводная мышь с USB-подключением', 5000.00, 8
FROM categories WHERE name = 'Мыши';

INSERT INTO products (category_id, name, description, price, stock)
SELECT id, 'Клавиатура K1', 'Компактная проводная клавиатура', 12000.00, 5
FROM categories WHERE name = 'Клавиатуры';

INSERT INTO products (category_id, name, description, price, stock)
SELECT id, 'Наушники H1', 'Наушники с микрофоном', 9000.00, 0
FROM categories WHERE name = 'Наушники';
