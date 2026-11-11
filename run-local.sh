#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
project_dir="$PWD"
local_dir="$project_dir/.local"
mkdir -p "$local_dir"
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"

if [[ -z "${JAVA_HOME:-}" && -d "$local_dir/tools/jdk/Contents/Home" ]]; then
    export JAVA_HOME="$local_dir/tools/jdk/Contents/Home"
fi
if [[ -z "${JAVA_HOME:-}" && -x /usr/libexec/java_home ]]; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || true)"
fi
java_bin="${JAVA_HOME:+$JAVA_HOME/bin/}java"
java_version="$("$java_bin" -version 2>&1 | head -n 1)"
if [[ ! "$java_version" =~ \"(17|21)\. ]]; then
    echo 'Укажите JAVA_HOME для Java 17 или 21.'
    exit 1
fi
if [[ -d "$local_dir/tools/maven/bin" ]]; then
    export PATH="$local_dir/tools/maven/bin:$PATH"
fi
for tool in mysqld mysql mysqladmin; do
    command -v "$tool" >/dev/null || { echo "Не найден $tool. Установите MySQL."; exit 1; }
done

socket="$local_dir/mysql.sock"
mysql_port="${LOCAL_DB_PORT:-33308}"
if [[ ! -d "$local_dir/mysql/mysql" ]]; then
    mkdir -p "$local_dir/mysql"
    mysqld --no-defaults --initialize-insecure --datadir="$local_dir/mysql" --log-error="$local_dir/mysql.log"
fi
started_mysql=false
if ! mysqladmin --no-defaults --socket="$socket" -u root ping >/dev/null 2>&1; then
    mysqld --no-defaults --datadir="$local_dir/mysql" --bind-address=127.0.0.1 --port="$mysql_port" \
        --socket="$socket" --pid-file="$local_dir/mysql.pid" --log-error="$local_dir/mysql.log" --mysqlx=OFF --daemonize
    started_mysql=true
fi
cleanup() {
    if [[ "$started_mysql" == true ]]; then
        mysqladmin --no-defaults --socket="$socket" -u root shutdown >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT
for attempt in {1..40}; do
    if mysqladmin --no-defaults --socket="$socket" -u root ping >/dev/null 2>&1; then break; fi
    sleep 0.5
done
mysql --no-defaults --socket="$socket" -u root -e 'CREATE DATABASE IF NOT EXISTS ecommerce_app CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'
export DB_URL="jdbc:mysql://127.0.0.1:$mysql_port/ecommerce_app?connectionTimeZone=UTC"
export DB_USERNAME=root
export DB_PASSWORD=''
export PORT="${PORT:-8080}"
if command -v mvn >/dev/null; then
    mvn -q -Dmaven.repo.local="$local_dir/m2" -DskipTests package
elif [[ ! -f target/shop-0.0.1-SNAPSHOT.jar ]]; then
    echo 'Не найден Maven. Установите Maven для сборки проекта.'
    exit 1
fi
echo "Магазин: http://localhost:$PORT"
echo 'Для остановки нажмите Ctrl+C. Данные сохраняются в .local/mysql.'
"$java_bin" -jar target/shop-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo
