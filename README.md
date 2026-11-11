# Интернет-магазин — проект № 5

Третья неделя по документу Java_Projects.docx: MySQL и CRUD.

Я добавил Spring Boot-проект, подключение к MySQL и основные операции с товарами и категориями. В базе шесть связанных таблиц. Запросы можно отправлять через Postman.

## Что работает

- Создание, просмотр, изменение и удаление категорий и товаров.
- Хранение названий, описаний, цен и остатков в MySQL.
- Проверка обязательных полей, цены, остатка и выбранной категории.
- Обработка ошибок с кодами 400, 404 и 409.
- Запрет удаления категории с товарами и товара, который используется в корзине или заказе.

Для пользователей, корзины, заказов и позиций заказа созданы таблицы, сущности и репозитории. Оформление заказа, регистрация, вход, проверка ролей, поиск, фильтры, сортировка каталога и статистика ещё не реализованы. Интерфейс относится к четвёртой неделе исходного документа. Защита от одновременной продажи последнего товара будет добавлена вместе с оформлением заказов.

API этого этапа работает без входа и доступен только на локальном адресе 127.0.0.1. Spring Security будет подключён на этапе авторизации.

## Что установить

- Java 17 или 21.
- Maven.
- MySQL с поддержкой CHECK-ограничений (8.0.16 или новее).
- Postman для ручной проверки запросов.

Проект использует Spring Boot 3.5.16, Spring Web, Spring Data JPA, Hibernate и MySQL Connector. Проверка входных данных выполняется средствами Spring Validation. Тесты написаны на JUnit 5 и Spring Boot Test.

## Подготовка базы

Все команды выполняются из папки проекта. MySQL должен быть запущен. Если используется другой пользователь MySQL, замените root на его имя.

```sh
mysql -u root -p < sql/create-database.sql
mysql -u root -p ecommerce < src/main/resources/schema.sql
mysql -u root -p ecommerce < src/main/resources/seed.sql
```

Последняя команда добавляет три категории и три товара. Запускайте seed.sql один раз в новой базе. Скрипт не очищает существующие данные. Таблицы пользователей, корзины и заказов на этом этапе остаются пустыми.

## Запуск

Укажите свои данные MySQL. Пароль не нужно записывать в Java-код или добавлять в Git.

macOS / Linux:

```sh
export DB_USERNAME=root
export DB_PASSWORD='ваш_пароль_MySQL'
export DB_URL='jdbc:mysql://localhost:3306/ecommerce?connectionTimeZone=UTC'
mvn spring-boot:run
```

Windows PowerShell:

```powershell
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "ваш_пароль_MySQL"
$env:DB_URL = "jdbc:mysql://localhost:3306/ecommerce?connectionTimeZone=UTC"
mvn spring-boot:run
```

Откройте `http://localhost:8080/api/products`. Ответ будет в JSON. Адрес `/` пока не содержит страницу магазина.

Hibernate проверяет схему при запуске через `ddl-auto=validate`. Таблицы создаются SQL-скриптом, существующие данные автоматически не удаляются.

## Проверка

Тесты используют отдельную базу ecommerce_test и настоящую MySQL. Подставлять адрес основной базы в TEST_DB_URL не нужно.

```sh
mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ecommerce_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
```

macOS / Linux:

```sh
export TEST_DB_USERNAME=root
export TEST_DB_PASSWORD='ваш_пароль_MySQL'
export TEST_DB_URL='jdbc:mysql://localhost:3306/ecommerce_test?connectionTimeZone=UTC'
mvn test
```

Windows PowerShell:

```powershell
$env:TEST_DB_USERNAME = "root"
$env:TEST_DB_PASSWORD = "ваш_пароль_MySQL"
$env:TEST_DB_URL = "jdbc:mysql://localhost:3306/ecommerce_test?connectionTimeZone=UTC"
mvn test
```

Схема тестовой базы создаётся при запуске тестов. Тестовые записи откатываются после каждого теста. Тесты проверяют CRUD, неверные данные, отсутствующие записи и ограничения при удалении. Для сборки JAR после подготовки тестовой базы выполните `mvn package`. Файл появится в `target/shop-0.0.1-SNAPSHOT.jar`; запустить его можно командой `java -jar target/shop-0.0.1-SNAPSHOT.jar` с теми же переменными DB_USERNAME, DB_PASSWORD и DB_URL.

## Файлы проекта

- [Отчёт за третью неделю](docs/week-3.md).
- [Отчёт в Word](docs/week-3-report.docx).
- [Описание базы](docs/database.md).
- [ER-диаграмма](design/database.png) и [исходник Mermaid](design/database.mmd).
- [Список API и примеры запросов](docs/api.md).
- [Коллекция Postman](docs/shop.postman_collection.json).
- `src/main/java/kz/student/shop`: model, repository, service, controller, dto и exception.
- `src/main/resources`: настройки, SQL-схема и демонстрационные данные.
- `src/test`: автоматизированные тесты.

Файлы week-1 и макеты интерфейса сохранены как материалы первого этапа. Текущая реализация соответствует третьей неделе исходного документа. Дополнительные поля доставки и скрытие товара из раннего черновика в этот этап не включены.

История Git сохранена локально. Публикация на GitHub пока не выполнена.
