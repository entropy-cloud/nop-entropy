#!/usr/bin/env bash
# Copyright (c) 2017-2024 Nop Platform. All rights reserved.
# Author: canonical_entropy@163.com
# Blog:   https://www.zhihu.com/people/canonical_entropy
# Gitee:  https://gitee.com/entropy-cloud/nop-entropy
# Github: https://github.com/entropy-cloud/nop-entropy
#
# Plan 2308 Phase 0: EQL `ORDER BY ... NULLS FIRST/LAST` 的方言矩阵实测探针。
#
# 回答三个问题，每个都有可复制的证据：
#   Q1 该库是否支持原生 `NULLS FIRST/LAST` 语法？
#   Q2 该库缺省 ASC / 缺省 DESC 分别把 NULL 放哪？（决定能否做「省略子句」优化）
#   Q3 `CASE WHEN` 模拟的四种方向组合是否都正确？（本计划 emission 算法的依据）
#
# 用法（在仓库根目录执行）：
#   bash ai-dev/tools/probe-nulls-order-matrix.sh
#
# 需要本机 docker 可用；脚本自行起停容器。镜像**钉版本**（已知坑：
# `TestPostgreDialect` 曾钉的 postgres 9.6.12 镜像已从 Docker Hub 下架导致拉取超时）。
#
# 退出码：0 = 全部实测通过；1 = 有库实测失败（结论不可信，需人工介入）

set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="$ROOT/_tmp/nulls-matrix"
PG_IMAGE="postgres:16"
MY_IMAGE="mysql:8.0"
PG_PORT=55440
MY_PORT=33400

mkdir -p "$WORK"

# ---------------------------------------------------------------- JDBC 探针
cat > "$WORK/NullsMatrix.java" <<'JAVA'
import java.sql.*;

public class NullsMatrix {
    static final String SETUP_DDL =
        "drop table if exists T; create table T(id int, v int);"
      + " insert into T values (1,10),(2,NULL),(3,20)";

    static String order(Connection c, String orderBy) {
        try (Statement s = c.createStatement()) {
            for (String stmt : SETUP_DDL.split(";")) if (!stmt.isBlank()) s.execute(stmt);
            StringBuilder sb = new StringBuilder();
            try (ResultSet r = s.executeQuery("select id from T " + orderBy)) {
                while (r.next()) sb.append(r.getInt(1)).append(";");
            }
            return sb.toString();
        } catch (SQLException e) {
            return "FAIL:" + e.getSQLState();
        }
    }

    public static void main(String[] args) throws Exception {
        Connection c = DriverManager.getConnection(args[0], args[1], args[2]);
        System.out.println("## " + c.getMetaData().getDatabaseProductName()
                + " " + c.getMetaData().getDatabaseProductVersion());

        // Q2 缺省行为
        System.out.println("  default-ASC   = " + order(c, "order by v asc"));
        System.out.println("  default-DESC  = " + order(c, "order by v desc"));

        // Q1 原生语法支持
        boolean nativeOk = true;
        for (String ob : new String[]{
                "order by v asc nulls first", "order by v asc nulls last",
                "order by v desc nulls first", "order by v desc nulls last"}) {
            String r = order(c, ob);
            System.out.println("  native  " + ob + "  -> " + r);
            if (r.startsWith("FAIL")) nativeOk = false;
        }
        System.out.println("  NATIVE_SUPPORT = " + nativeOk);

        // Q3 CASE 模拟四组合（仅在不支持原生的库上有意义，这里一并记录）
        String[][] cases = {
            {"order by case when v is null then 0 else 1 end, v asc",        "2;1;3;"},
            {"order by case when v is null then 1 else 0 end, v asc",        "1;3;2;"},
            {"order by case when v is null then 1 else 0 end desc, v desc",  "2;3;1;"},
            {"order by case when v is null then 0 else 1 end desc, v desc",  "3;1;2;"},
        };
        boolean simOk = true;
        for (String[] cs : cases) {
            String r = order(c, cs[0]);
            boolean pass = cs[1].equals(r);
            if (!pass) simOk = false;
            System.out.println("  sim     " + (pass ? "PASS" : "MISMATCH") + "  -> " + r
                    + "  (want " + cs[1] + ")  [" + cs[0] + "]");
        }
        System.out.println("  SIM_ALL_CORRECT = " + simOk);
    }
}
JAVA

# ---------------------------------------------------------------- 驱动解析
resolve_driver() {
    case "$1" in
        postgresql)
            find "$HOME/.m2/repository/org/postgresql" -name "postgresql-*.jar" \
                2>/dev/null | grep -v sources | head -1 ;;
        mysql)
            find "$HOME/.m2/repository/com/mysql" -name "mysql-connector-j-*.jar" \
                2>/dev/null | grep -v sources | head -1 ;;
        h2)
            find "$HOME/.m2/repository/com/h2database/h2" -path "*2.3.232*" -name "h2-*.jar" \
                2>/dev/null | grep -v sources | head -1 ;;
    esac
}

PG_DRIVER="$(resolve_driver postgresql)"
MY_DRIVER="$(resolve_driver mysql)"
H2_DRIVER="$(resolve_driver h2)"

fail=0
javac -d "$WORK" "$WORK/NullsMatrix.java" || { echo "javac 失败"; exit 1; }

# JDBC 层重试：容器内 ping 通过不代表映射端口已可连，这里以真正建连成功为准
run_probe() {
    local cp="$1" url="$2" u="$3" p="$4" label="$5"
    for i in $(seq 1 40); do
        if java -cp "$cp" NullsMatrix "$url" "$u" "$p" 2>/dev/null; then
            return 0
        fi
        sleep 2
    done
    echo "## $label  FAILED (40 次重试后仍无法建连)"
    return 1
}

# ---------------------------------------------------------------- H2（本地 jar，无需 docker）
echo "=============================================================="
if [ -n "$H2_DRIVER" ]; then
    java -cp "$WORK:$H2_DRIVER" NullsMatrix "jdbc:h2:mem:probe" sa "" || fail=1
else
    echo "## H2  SKIPPED (未找到 h2-2.3.232 jar)"
    fail=1
fi

# ---------------------------------------------------------------- PostgreSQL
echo "=============================================================="
if [ -z "$PG_DRIVER" ]; then
    echo "## PostgreSQL  SKIPPED (未找到驱动 jar)"; fail=1
else
    docker rm -f nulls-matrix-pg >/dev/null 2>&1
    if docker run -d --rm --name nulls-matrix-pg \
        -e POSTGRES_PASSWORD=test -e POSTGRES_USER=test -e POSTGRES_DB=test \
        -p "$PG_PORT":5432 "$PG_IMAGE" >/dev/null 2>&1; then
        echo "waiting for postgres..."
        for _ in $(seq 1 40); do
            docker exec nulls-matrix-pg pg_isready -U test >/dev/null 2>&1 && break
            sleep 1
        done
        run_probe "$WORK:$PG_DRIVER" "jdbc:postgresql://localhost:$PG_PORT/test" test test "PostgreSQL" || fail=1
        docker rm -f nulls-matrix-pg >/dev/null 2>&1
    else
        echo "## PostgreSQL  SKIPPED (无法启动容器)"; fail=1
    fi
fi

# ---------------------------------------------------------------- MySQL
echo "=============================================================="
if [ -z "$MY_DRIVER" ]; then
    echo "## MySQL  SKIPPED (未找到驱动 jar)"; fail=1
else
    docker rm -f nulls-matrix-my >/dev/null 2>&1
    if docker run -d --rm --name nulls-matrix-my \
        -e MYSQL_ROOT_PASSWORD=test -e MYSQL_DATABASE=testdb \
        -e MYSQL_USER=test -e MYSQL_PASSWORD=test \
        -p "$MY_PORT":3306 "$MY_IMAGE" >/dev/null 2>&1; then
        echo "waiting for mysql..."
        for _ in $(seq 1 60); do
            docker exec nulls-matrix-my mysqladmin ping -utest -ptest >/dev/null 2>&1 && break
            sleep 1
        done
        run_probe "$WORK:$MY_DRIVER" \
            "jdbc:mysql://localhost:$MY_PORT/testdb?useSSL=false&allowPublicKeyRetrieval=true" \
            test test "MySQL" || fail=1
        docker rm -f nulls-matrix-my >/dev/null 2>&1
    else
        echo "## MySQL  SKIPPED (无法启动容器)"; fail=1
    fi
fi

echo "=============================================================="
if [ "$fail" -eq 0 ]; then
    echo "探针完成：以上为实测证据。"
else
    echo "探针存在失败项（SKIPPED 或实测异常），矩阵结论不可直接采信。"
fi
exit "$fail"