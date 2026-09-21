#!/usr/bin/env bash
set -euo pipefail

# Fail before creating databases if the private initialization inputs are missing.
for sql in /opt/sql/nacos.sql /opt/res/sql/nacos-auth.sql /opt/res/sql/nacosdata.sql /opt/res/sql/db.sql; do
  if [[ ! -s "$sql" || ! -r "$sql" ]]; then
    printf 'Missing or unreadable initialization SQL: %s\n' "$sql" >&2
    exit 1
  fi
done

sql_escape() {
  printf "%s" "$1" | sed "s/'/''/g"
}

app_user=$(sql_escape "${MYSQL_APP_USER}")
app_password=$(sql_escape "${MYSQL_APP_PASSWORD}")

export MYSQL_PWD="${MYSQL_ROOT_PASSWORD}"
mysql --protocol=socket -uroot <<SQL
SET SESSION sql_mode = CONCAT_WS(',', @@SESSION.sql_mode, 'NO_BACKSLASH_ESCAPES');
CREATE DATABASE IF NOT EXISTS frameworkjava_nacos_prd DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE DATABASE IF NOT EXISTS frameworkjava_prd DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
CREATE USER IF NOT EXISTS '${app_user}'@'%' IDENTIFIED BY '${app_password}';
ALTER USER '${app_user}'@'%' IDENTIFIED BY '${app_password}';
GRANT ALL PRIVILEGES ON frameworkjava_nacos_prd.* TO '${app_user}'@'%';
GRANT ALL PRIVILEGES ON frameworkjava_prd.* TO '${app_user}'@'%';
FLUSH PRIVILEGES;
SQL

mysql --protocol=socket --default-character-set=utf8mb4 -uroot < /opt/sql/nacos.sql
mysql --protocol=socket --default-character-set=utf8mb4 -uroot < /opt/res/sql/nacos-auth.sql
mysql --protocol=socket --default-character-set=utf8mb4 -uroot < /opt/res/sql/nacosdata.sql
mysql --protocol=socket --default-character-set=utf8mb4 -uroot frameworkjava_prd < /opt/res/sql/db.sql
unset MYSQL_PWD
