#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
project_dir="$PWD"
local_dir="$project_dir/.local"
socket="$local_dir/mysql.sock"
mysql_port="${LOCAL_DB_PORT:-33308}"
started_mysql=false
tests_finished=false
export PATH="/opt/homebrew/bin:/usr/local/bin:$PATH"

finish() {
    result=$?
    trap - EXIT
    if [[ "$started_mysql" == true ]]; then
        mysqladmin --no-defaults --socket="$socket" -u root shutdown >/dev/null 2>&1 || true
    fi
    if [[ "$result" == 0 && "$tests_finished" == true ]]; then
        echo 'Все тесты прошли.'
    else
        if [[ "$result" == 0 ]]; then result=1; fi
        echo 'Тесты не завершились успешно. Причина указана выше.'
    fi
    if [[ -t 0 && -t 1 ]]; then
        read -r -p 'Нажмите Enter, чтобы закрыть окно.' || true
    fi
    exit "$result"
}
trap finish EXIT

if [[ -x "$local_dir/tools/jdk/Contents/Home/bin/java" ]]; then
    export JAVA_HOME="$local_dir/tools/jdk/Contents/Home"
elif [[ -z "${JAVA_HOME:-}" && -x /usr/libexec/java_home ]]; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || /usr/libexec/java_home -v 17 2>/dev/null || true)"
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
    export PATH="$JAVA_HOME/bin:$PATH"
fi
if [[ -d "$local_dir/tools/maven/bin" ]]; then
    export PATH="$local_dir/tools/maven/bin:$PATH"
fi
for tool in java mvn mysqld mysql mysqladmin; do
    command -v "$tool" >/dev/null || { echo "Не найден $tool. Установите его для запуска тестов."; exit 1; }
done
java_version="$(java -version 2>&1)"
if [[ ! "$java_version" =~ \"(17|21)\. ]]; then
    echo 'Для тестов нужна Java 17 или 21. Укажите JAVA_HOME.'
    exit 1
fi

mkdir -p "$local_dir"
if ! mysqladmin --no-defaults --socket="$socket" -u root ping >/dev/null 2>&1; then
    if [[ ! -d "$local_dir/mysql/mysql" ]]; then
        mkdir -p "$local_dir/mysql"
        mysqld --no-defaults --initialize-insecure --datadir="$local_dir/mysql" --log-error="$local_dir/mysql.log"
    fi
    mysqld --no-defaults --datadir="$local_dir/mysql" --bind-address=127.0.0.1 --port="$mysql_port" \
        --socket="$socket" --pid-file="$local_dir/mysql.pid" --log-error="$local_dir/mysql.log" --mysqlx=OFF --daemonize
    started_mysql=true
fi
ready=false
for attempt in {1..40}; do
    if mysqladmin --no-defaults --socket="$socket" -u root ping >/dev/null 2>&1; then
        ready=true
        break
    fi
    sleep 0.5
done
if [[ "$ready" != true ]]; then
    echo 'MySQL не запустился. Подробности находятся в .local/mysql.log.'
    exit 1
fi
mysql_port="$(mysql --no-defaults --socket="$socket" -u root -Nse 'SELECT @@port;')"
mysql --no-defaults --socket="$socket" -u root -e \
    'CREATE DATABASE IF NOT EXISTS ecommerce_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'
export TEST_DB_URL="jdbc:mysql://127.0.0.1:$mysql_port/ecommerce_test?connectionTimeZone=UTC"
export TEST_DB_USERNAME=root
export TEST_DB_PASSWORD=''
echo 'Запуск тестов в отдельной базе ecommerce_test.'
echo 'Товары и заказы магазина хранятся в другой базе.'
if [[ -n "${1:-}" ]]; then
    mvn -B -ntp -Dmaven.repo.local="$local_dir/m2" "-Dtest=$1" test
else
    mvn -B -ntp -Dmaven.repo.local="$local_dir/m2" test
fi
tests_finished=true
