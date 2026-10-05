#!/bin/bash
# WI7b Phase 0: H2 live-DB probe (v2 — CREATE+SELECT in ONE Shell call: each org.h2.tools.Shell
# invocation is a fresh JVM and a mem DB dies with it; v1 with separate DDL calls proved nothing).
set -u
M2=${M2:-$HOME/.m2/repository/com/h2database/h2}
OUT=${1:-_tmp/h2probe/raw-transcript.txt}
: > "$OUT"
probe() {
  local ver="$1" url="$2" label="$3" sql="$4"
  echo "#### VERSION=$ver MODE=$label" >> "$OUT"
  echo "#### SQL: $sql" >> "$OUT"
  java -cp "$M2/$ver/h2-$ver.jar" org.h2.tools.Shell -url "$url" -user sa -password "" -sql "$sql" >> "$OUT" 2>&1
  echo "" >> "$OUT"
}
DDL="CREATE TABLE t(g INT, v INT); INSERT INTO t VALUES (1,10),(2,20);"
for ver in 2.3.232 2.4.240; do
  for mode in "" "MODE=Oracle" "MODE=PostgreSQL"; do
    label=${mode:-DEFAULT}
    url="jdbc:h2:mem:probe${mode:+;$mode}"
    probe "$ver" "$url" "$label" "$DDL SELECT SUM(v) FROM t GROUP BY ROLLUP(g);"
    probe "$ver" "$url" "$label" "$DDL SELECT SUM(v) FROM t GROUP BY CUBE(g);"
    probe "$ver" "$url" "$label" "$DDL SELECT g, GROUPING(g) FROM t GROUP BY g;"
  done
done
for ver in 2.4.240 2.3.232; do
  probe "$ver" "jdbc:h2:mem:probe" DEFAULT "SELECT TIMESTAMP '2020-01-01 00:00:00' + INTERVAL '1' DAY;"
  probe "$ver" "jdbc:h2:mem:probe" DEFAULT "SELECT TIMESTAMP '2020-01-01 00:00:00' + INTERVAL 1 DAY;"
  probe "$ver" "jdbc:h2:mem:probe" DEFAULT "SELECT TIMESTAMP '2020-01-01 00:00:00' + INTERVAL '1 DAY';"
done
echo "DONE -> $OUT"
