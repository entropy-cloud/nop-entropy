package io.nop.metadata.service.profiling;

import io.nop.api.core.ApiErrors;
import io.nop.api.core.exceptions.NopException;
import io.nop.metadata.service.tableref.TableReference;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * check2 P1-2 / P2-3（2026-08-23 审计）回归：
 * <ul>
 *   <li><b>P1-2</b>：{@code loadSortedDoubles} 无行数上限，整列非空值全量拉入内存排序——
 *       千万级外部表可经 GraphQL {@code profileTable} 触发进程级 OOM。修复后超过
 *       {@code MAX_IN_APP_SORT_ROWS}（对齐 maxCrossDbRows=10000 先例）时 median/percentiles/
 *       distribution 降级为 null + unavailable=["too-many-rows"]（不伪造、不拉取）。</li>
 *   <li><b>P2-3</b>：profileColumn 对每列重复执行与列无关的 {@code SELECT COUNT(*) FROM <表>}
 *       （N 列表 = N+1 次全表聚合）。修复后表级计算一次并传入列剖析。</li>
 * </ul>
 *
 * <p>纯 JDBC 单元（真实 H2 内存库 + 反射调用 private profile，沿
 * {@link TestMetaTableProfilerProbeNumeric} 先例）；P2-3 经 Statement 代理统计
 * {@code SELECT COUNT(*)} 恰好执行次数。
 */
public class TestMetaTableProfilerResourceLimits {

    // ===== P1-2：超限降级 =====

    /** 超过 MAX_IN_APP_SORT_ROWS：median/percentiles/distribution 降级 null + unavailable=too-many-rows。 */
    @Test
    public void testMedianDegradedWhenRowsExceedInAppLimit() throws Exception {
        ProfilingSnapshot snapshot = profileRangeTable("prof_over_limit", 10001);
        ProfilingColumnStats v = numericColumn(snapshot, "V");
        assertNotNull(v.getNumericStats(), "numeric stats must still be collected (min/max/mean/stddev via SQL)");
        assertNull(v.getNumericStats().get("medianValue"),
                "median must degrade to null over row limit (was: " + v.getNumericStats().get("medianValue") + ")");
        assertNull(v.getNumericStats().get("percentiles"));
        assertNull(v.getNumericStats().get("distribution"));
        assertTrue(v.getUnavailable().contains("too-many-rows"),
                "unavailable must flag too-many-rows, got: " + v.getUnavailable());
    }

    /** 恰在上限（10000 行）：不降级，median 正常计算（边界守护，防误伤）。 */
    @Test
    public void testMedianComputedAtExactRowLimit() throws Exception {
        ProfilingSnapshot snapshot = profileRangeTable("prof_at_limit", 10000);
        ProfilingColumnStats v = numericColumn(snapshot, "V");
        assertNotNull(v.getNumericStats());
        Object median = v.getNumericStats().get("medianValue");
        assertNotNull(median, "median at exact limit must be computed (boundary guard)");
        assertEquals(5000.5, ((Number) median).doubleValue(), 0.0001,
                "median of 1..10000 must be 5000.5");
        assertFalse(v.getUnavailable().contains("too-many-rows"));
    }

    // ===== P2-3：COUNT(*) 每表一次 =====

    /** 2 列表：{@code SELECT COUNT(*) FROM <表>}（无 WHERE）全剖析过程恰好执行 1 次（表级）。 */
    @Test
    public void testCountStarExecutedExactlyOncePerTable() throws Exception {
        String db = "prof_count_once";
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + db + ";DB_CLOSE_DELAY=-1", "sa", "");
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE T_CNT (a INT, b VARCHAR(10))");
            st.execute("INSERT INTO T_CNT VALUES (1, 'x')");
            st.execute("INSERT INTO T_CNT VALUES (2, 'y')");

            List<String> executedSql = new ArrayList<>();
            Connection recording = recordingConnection(conn, executedSql);

            MetaTableProfiler profiler = new MetaTableProfiler();
            TableReference ref = new TableReference(TableReference.Kind.EXTERNAL, "mt-cnt", "T_CNT",
                    null, null, null, null, null);
            ProfilingSnapshot snapshot = profile(profiler, recording, ref);

            assertEquals(2L, snapshot.getRowCount());
            String countStar = "SELECT COUNT(*) FROM PUBLIC.T_CNT";
            long countStarCalls = executedSql.stream().filter(countStar::equals).count();
            assertEquals(1, countStarCalls,
                    "SELECT COUNT(*) (no WHERE) must run exactly once per table (table-level), got "
                            + countStarCalls + " executions: " + executedSql);
        }
    }

    // ===== helpers =====

    /** H2 {@code SYSTEM_RANGE(1,n)} 快速建 n 行单数值列表（列名 V），剖析并返回 V 列统计。 */
    private static ProfilingSnapshot profileRangeTable(String dbName, int rows) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1", "sa", "");
             Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE T_RANGE_V AS SELECT X AS V FROM SYSTEM_RANGE(1, " + rows + ")");
            MetaTableProfiler profiler = new MetaTableProfiler();
            TableReference ref = new TableReference(TableReference.Kind.EXTERNAL, "mt-range", "T_RANGE_V",
                    null, null, null, null, null);
            return profile(profiler, conn, ref);
        }
    }

    private static ProfilingColumnStats numericColumn(ProfilingSnapshot snapshot, String column) {
        return snapshot.getColumnStats().stream()
                .filter(cs -> column.equalsIgnoreCase(cs.getColumnName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("column " + column + " must be profiled"));
    }

    /** 反射调用 private profile（沿 TestMetaTableProfilerProbeNumeric 先例）。 */
    private static ProfilingSnapshot profile(MetaTableProfiler profiler, Connection conn, TableReference ref)
            throws Exception {
        Method m = MetaTableProfiler.class.getDeclaredMethod("profile",
                Connection.class, DatabaseMetaData.class, TableReference.class,
                String.class, String.class, String.class);
        m.setAccessible(true);
        try {
            return (ProfilingSnapshot) m.invoke(profiler, conn, conn.getMetaData(), ref, "PUBLIC", null, "H2");
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new NopException(ApiErrors.ERR_WRAP_EXCEPTION, cause);
        }
    }

    /** 代理 Connection：createStatement 返回记录 executeQuery(sql) 的 Statement 代理（其余委托真实连接）。 */
    private static Connection recordingConnection(Connection real, List<String> executedSql) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if ("createStatement".equals(method.getName())) {
                        Statement realSt = real.createStatement();
                        return Proxy.newProxyInstance(Statement.class.getClassLoader(),
                                new Class<?>[]{Statement.class},
                                (p, m, a) -> {
                                    if ("executeQuery".equals(m.getName()) && a != null && a.length == 1) {
                                        executedSql.add(String.valueOf(a[0]));
                                    }
                                    try {
                                        return m.invoke(realSt, a);
                                    } catch (InvocationTargetException e) {
                                        throw e.getCause();
                                    }
                                });
                    }
                    try {
                        return method.invoke(real, args);
                    } catch (InvocationTargetException e) {
                        throw e.getCause();
                    }
                });
    }
}
